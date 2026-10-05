# MyFit AI proxy (Cloudflare Worker)

Keeps the AI provider keys off the phone. The app calls this Worker; the Worker checks the user's Firebase
sign-in, rate-limits them (20 requests/minute) and forwards the request with the real key.

## One-time setup (≈10 minutes, free)
1. Create a free account at https://dash.cloudflare.com/sign-up.
2. Cloudflare dashboard → **My Profile → API Tokens → Create Token → "Edit Cloudflare Workers"** template → Create. Copy it.
3. Note your **Account ID** (dashboard → Workers & Pages, right sidebar).
4. In GitHub → repo **Settings → Secrets and variables → Actions**, add:
   - `CLOUDFLARE_API_TOKEN` — the token from step 2
   - `CLOUDFLARE_ACCOUNT_ID` — from step 3
   - `FIREBASE_PROJECT_ID` — Firebase console → Project settings → Project ID
   (The provider keys — `GEMINI_API_KEY`, `GROQ_API_KEY`, … — are already secrets; the workflow copies them into the Worker.)
5. GitHub → **Actions → "Deploy AI proxy" → Run workflow**. The log ends with the Worker URL, e.g.
   `https://myfit-ai.<your-subdomain>.workers.dev`.
6. Add that URL as the secret **`AI_PROXY_URL`**. The next app build uses it.

After that you can delete the old provider-key secrets from the app build — the app no longer embeds them.
