// MyFit AI proxy — Cloudflare Worker.
// Holds the AI provider keys as Worker secrets so they never ship inside the app.
// Every request must carry a valid Firebase ID token (a signed-in MyFit user); each user is rate-limited.
//
// Routes:  /<provider>/<upstream path>   e.g.  /gemini/v1beta/models/gemini-2.5-flash:generateContent
const UPSTREAM = {
  gemini:     { host: "https://generativelanguage.googleapis.com", auth: (h, e) => h.set("x-goog-api-key", e.GEMINI_API_KEY), key: "GEMINI_API_KEY", allow: /^\/v1beta\/models/ },
  groq:       { host: "https://api.groq.com",       auth: (h, e) => h.set("Authorization", `Bearer ${e.GROQ_API_KEY}`), key: "GROQ_API_KEY", allow: /^\/openai\/v1\/(chat\/completions|models)/ },
  openrouter: { host: "https://openrouter.ai",      auth: (h, e) => h.set("Authorization", `Bearer ${e.OPENROUTER_API_KEY}`), key: "OPENROUTER_API_KEY", allow: /^\/api\/v1\/(chat\/completions|models)/ },
  mistral:    { host: "https://api.mistral.ai",     auth: (h, e) => h.set("Authorization", `Bearer ${e.MISTRAL_API_KEY}`), key: "MISTRAL_API_KEY", allow: /^\/v1\/(chat\/completions|models)/ },
  eleven:     { host: "https://api.elevenlabs.io",  auth: (h, e) => h.set("xi-api-key", e.ELEVENLABS_API_KEY), key: "ELEVENLABS_API_KEY", allow: /^\/v1\/(text-to-speech|models|user\/subscription)/ },
  azure:      { host: (e) => `https://${e.AZURE_SPEECH_REGION}.tts.speech.microsoft.com`, auth: (h, e) => h.set("Ocp-Apim-Subscription-Key", e.AZURE_SPEECH_KEY), key: "AZURE_SPEECH_KEY", allow: /^\/cognitiveservices\/v1$/ },
};

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let jwksCache = { keys: null, until: 0 };

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

async function jwks() {
  if (jwksCache.keys && Date.now() < jwksCache.until) return jwksCache.keys;
  const r = await fetch(JWKS_URL);
  const j = await r.json();
  const maxAge = Number((r.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys: j.keys, until: Date.now() + maxAge * 1000 };
  return j.keys;
}

/** Verifies a Firebase ID token (RS256, Google-signed) and returns its uid, or null. */
async function verifyFirebase(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const header = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[0])));
  const payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[1])));
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== "RS256") return null;
  if (payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (!payload.sub || payload.exp <= now || payload.iat > now + 300) return null;
  const jwk = (await jwks()).find((k) => k.kid === header.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlToBytes(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  return ok ? payload.sub : null;
}

const json = (status, obj) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/health") return json(200, { ok: true });
    if (req.method !== "POST" && req.method !== "GET") return json(405, { error: { message: "Method not allowed" } });

    const [, provider, ...rest] = url.pathname.split("/");
    const up = UPSTREAM[provider];
    const path = "/" + rest.join("/");
    if (!up || !up.allow.test(path)) return json(404, { error: { message: "Unknown route" } });
    if (!env[up.key]) return json(503, { error: { message: `${provider} isn't configured on the server` } });

    const auth = req.headers.get("Authorization") || "";
    const uid = auth.startsWith("Bearer ") ? await verifyFirebase(auth.slice(7), env.FIREBASE_PROJECT_ID).catch(() => null) : null;
    if (!uid) return json(401, { error: { message: "Sign in to MyFit to use online AI" } });

    if (env.LIMITER) {
      const { success } = await env.LIMITER.limit({ key: uid });
      if (!success) return json(429, { error: { message: "Too many requests — try again in a minute" } });
    }

    const headers = new Headers();
    for (const h of ["content-type", "accept", "http-referer", "x-title", "x-microsoft-outputformat", "user-agent"]) {
      const v = req.headers.get(h);
      if (v) headers.set(h, v);
    }
    up.auth(headers, env);
    const host = typeof up.host === "function" ? up.host(env) : up.host;
    const target = host + path + url.search;
    const res = await fetch(target, { method: req.method, headers, body: req.method === "POST" ? req.body : undefined });
    // pass the upstream response straight through (streams for TTS audio)
    const out = new Headers();
    for (const h of ["content-type", "content-length"]) { const v = res.headers.get(h); if (v) out.set(h, v); }
    return new Response(res.body, { status: res.status, headers: out });
  },
};
