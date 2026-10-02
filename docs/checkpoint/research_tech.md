# MyFit Tracker — Tech Research Brief (wearables, vitals, free AI)
*Researched 2 Oct 2026. "Verify" = I could not find a primary source; test on a real device before promising it in the UI.*

---

## TL;DR
1. **Health Connect (HC) already covers most brands Pakistani users actually own.** Samsung, Fitbit/Pixel, Garmin (since mid-2025), Xiaomi Mi Fitness, Amazfit/Zepp, OnePlus/Oppo (OHealth), Polar, Withings, Oura and Ultrahuman all write to it. **Huawei does not.** The cheap Indian bands (boAt, Noise, Fire-Boltt) show **no evidence of writing to HC**, and on Android NoiseFit syncs to Google Fit instead.
2. **Skip the brand cloud APIs for now.** Garmin's is business-only. Fitbit Web API shuts down **30 Oct 2026**, and its replacement (Google Health API) needs a restricted-scope security review. Oura bans feeding its data to AI. Whoop and Oura both cap you at 10 users until they approve you. Build a **source picker + per-metric priority + brand setup guides** on top of HC instead.
3. **Vitals:** HC exposes HR, resting HR, HRV (RMSSD), SpO2, respiratory rate, BP, body and skin temperature, VO2 max and blood glucose. **It has no ECG type.** Camera-based heart rate from the phone works in the lab but is unreliable in real use. At most, offer it as an optional "rough check" with a disclaimer.
4. **AI:** **Gemma 4 E2B** (Apache 2.0, released Apr 2026, takes text + image + audio input, ~2.6 GB file) running through **LiteRT-LM** makes "unlimited at $0" realistic on recent 8 GB+ phones. For everyone else, pair a **small on-device food classifier** with the 455-dish database, and keep a **capped cloud fallback** (Gemini 3.1 Flash-Lite costs about **$0.8 per 1,000 food photos**). Gemini Nano / ML Kit Prompt API is **not** a mass-market option: it supports few devices and **not the S23 Ultra**.

---

## Part 1 — Wearable brands

### 1.1 Who writes to Health Connect (2026)

| Brand / app | Writes to HC? | Data types (reported) | Notes |
|---|---|---|---|
| Samsung Health (Galaxy Watch/Ring) | **Yes** | Steps, HR, sleep stages, exercise, SpO2, BP, glucose, weight/body comp, calories | Strongest coverage. [Sahha HC] |
| Fitbit / Pixel Watch | **Yes** | Steps, HR, sleep, exercise, calories, weight | Its cloud API is ending (see 1.2). [Sahha HC] |
| Garmin Connect | **Yes** (one-way, rolled out from mid-2025) | Steps, HR (activity + resting), sleep, calories, workouts, weight/body fat, SpO2, cycling speed/cadence, swim strokes | User must turn it on in Garmin Connect. [Notebookcheck], [Sonar] |
| Xiaomi Mi Fitness / Zepp Life (Redmi, Mi Band) | **Yes** | Steps, HR, resting HR, sleep stages, active kcal, weight (with scale); SpO2 on some models | Varies by model. [Sahha Xiaomi], [FitMesh] |
| Amazfit / Zepp | **Yes** (26 types since Jan 2025) | Steps, distance, HR, SpO2, sleep, calories… | PAI and stress not exported. [G&W] |
| Huawei Health | **No** | none | Still no official route as of mid-2026. [Sahha Huawei], [FitMesh Huawei] |
| Oppo / OnePlus / Realme (OHealth) | **Yes** (narrow) | Steps, HR, sleep | [Sahha OHealth] |
| Withings Health Mate | **Yes** | Weight/body comp, BP, HR, sleep, steps | Showcase HC partner since 2023. [Android Blog] |
| Oura | **Yes** | Sleep sessions, HR, HRV (RMSSD), steps, active kcal | No SpO2 or temperature. [FitMesh Oura] |
| Whoop | **Yes** (two-way) | Reads exercise/weight from HC; writes workouts/sleep (verify) | [Whoop community] |
| Polar Flow | **Yes** | Steps, active/total kcal, exercise + route, HR, resting HR, SpO2, sleep stages, VO2 max, weight | Best-documented list. [Polar] |
| Ultrahuman | **Yes** | Sleep stages, HR, resting HR, HRV, SpO2, skin temp, steps, workouts | [Sahha Ultrahuman] |
| Coros, Suunto | Verify | Most likely workouts only | No primary source found. |
| Strava | Yes (workouts), verify | Exercise sessions | Treat as a source of workouts only. |
| Google Fit | Legacy | Steps/HR it already holds | **Fit APIs shut down end of 2026.** Don't build on them. [Sahha Fit] |
| boAt Crest / Fire-Boltt | **No evidence** | none | Not listed by aggregators. Test in-house. |
| NoiseFit | **No** on Android | none (syncs to Google Fit) | Apple Health only on iOS. [Sahha NoiseFit] |

**Market implication:** in Pakistan the cheap bands (boAt, Noise, Fire-Boltt, unbranded "Da Fit" style watches) plus Huawei are the gap. You can't close that gap cheaply. A "manual / phone-only" mode covers those users.

### 1.2 Brands outside HC: cloud APIs and bridges

| Option | Cost / approval | Solo-dev feasibility |
|---|---|---|
| **Garmin Health API** | No fee, but **businesses only**; requires approval and an integration call | Low. Unnecessary now that Garmin writes to HC. [Garmin FAQ] |
| **Fitbit Web API → Google Health API** | Fitbit API shuts down **30 Oct 2026**. Google Health scopes are *Restricted*: privacy review plus **CASA security assessment** (often paid), and every user must re-consent | Not worth it. Fitbit already writes to HC. [Sahha Fitbit] |
| **Oura API v2** | Personal tokens deprecated Dec 2025. OAuth capped at 10 users until approved. Gen3+ needs a membership. **Terms forbid feeding Oura data to AI models** | No. That conflicts with the AI buddy. [Terra Oura] |
| **Whoop API** | Free. 10 users until approved. 100 req/min, 10k/day | Possible, but HC covers it. [Terra Whoop] |
| **Polar AccessLink / Withings API** | Free registration, OAuth plus a backend for webhooks | Possible, but HC covers both. |
| **Huawei Health Kit** | HMS developer account and app review. Only works where HMS Core is installed | Medium–high effort for one brand. Defer. |
| **Bridge apps** (e.g. *Health Sync*, paid after trial) | User installs and pays for it. Huawei → HC is partial (steps/sleep; HR limited; no SpO2) | **Best Huawei answer:** link to it from a guide screen. [HealthSync], [FitMesh Huawei] |

All the cloud APIs need a backend for OAuth token storage, refresh and webhooks. That means ongoing cost and privacy-policy work, which runs against the "$0" goal.

### 1.3 What to build in the app
- **"Connect your watch" screen.** A brand grid with an "on HC / via bridge / not supported" badge, a 3-step illustrated guide per brand, and **deep links**. For HC settings use `HealthConnectClient.getHealthConnectManageDataIntent()` (or the `ACTION_HEALTH_CONNECT_SETTINGS` intent on Android 14+); otherwise use the brand app's Play Store `market://details?id=` link.
- **Source picker per data type.** Read `metadata.dataOrigin.packageName` and show "Steps from: Samsung Health ▾".
- **Per-metric priority list** (for example, watch > phone for steps). This avoids double-counting when phone and watch both write steps. Use HC aggregate reads, which already de-duplicate by HC's own priority; your own list overrides it.
- **"Last synced from X, 3 h ago"** freshness hint, plus a nudge to open the brand app (most brands write only after their app syncs).
- Request `READ_HEALTH_DATA_HISTORY` (data older than 30 days) and `READ_HEALTH_DATA_IN_BACKGROUND` only where needed. Both add to Play review scrutiny. [HC data types]

---

## Part 2 — Vitals

**Exposed by HC:** HeartRate (series), RestingHeartRate, HeartRateVariabilityRmssd, OxygenSaturation, RespiratoryRate, BloodPressure, BodyTemperature, SkinTemperature (feature-flagged), Vo2Max, BloodGlucose. Also Mindfulness, ActivityIntensity and FHIR medical records. **There is no ECG record type.** "Sleeping SpO2" is just OxygenSaturation samples that fall inside a SleepSession window. [HC data types]

**Who writes what (typical):**
- **HR / resting HR:** almost every brand.
- **HRV:** Samsung, Oura, Ultrahuman, Fitbit (verify), Garmin (verify).
- **SpO2:** Samsung, Garmin, Polar, Zepp, Ultrahuman, some Xiaomi models.
- **Respiratory rate:** Samsung, Fitbit (verify).
- **BP:** Samsung (Galaxy Watch, after calibration), Withings cuffs.
- **Skin temp:** Ultrahuman, Samsung (verify).
- **VO2 max:** Polar, Garmin (verify), Samsung.
- **Glucose:** CGM apps and Samsung (manual entry).

**Can the phone measure vitals itself?**
- **Fingertip camera PPG** (finger over the lens with the flash on) agrees well with ECG for **resting** HR, with r = 0.98–1.0. But that held only under tightly controlled lab conditions, in small samples (1–50 people), and skin tone was almost never reported. No study came from a lower-middle-income country. [Frontiers 2024]
- Google Fit's camera HR/respiratory feature was always labelled "may be removed" and limited to Pixels. [Android Central]
- Samsung phones lost their HR/SpO2 sensor after the S10 generation (2019). [SamMobile]
- **No phone camera method is credible for SpO2, BP or HRV.**

**Disclaimer norms:** label the app as wellness, not medical ("Not a medical device; not for diagnosis"). Show readings without diagnosing them, e.g. say "above your usual range" rather than "hypertension". Add an "urgent symptoms → see a doctor" line next to BP/SpO2. Never claim accuracy figures. Play's Health apps policy requires the health declaration form and a privacy policy.

**MVP vitals screen:** cards for **Resting HR, HRV, SpO2 (incl. overnight), Respiratory rate, BP, Body/Skin temp**. Each card shows today's value, a 7/30-day sparkline, the source app, and a "no data — connect a device / log manually" state. Allow manual entry for BP, glucose, temperature and weight. Put camera HR behind an "experimental" toggle, or leave it out of v1.

---

## Part 3 — AI without quotas

### 3.1 On-device LLMs (Android, 2026)

| Model / runtime | Download | RAM | Speed | License | Verdict |
|---|---|---|---|---|---|
| **Gemma 4 E2B** (LiteRT-LM) | ~2.6 GB | ~0.7 GB (GPU) / ~1.7 GB (CPU) | ~47–52 tok/s decode on S26 Ultra | **Apache 2.0** | **Best pick.** Takes image + text, 128K context. [HF Gemma4], [Implicator] |
| Gemma 3n E2B / E4B | ~1.2 GB (E2B Q4) / larger E4B | 2+ GB | E4B ≈ 9 tok/s decode on S24 Ultra | Gemma Terms (commercial OK, must pass on use policy) | Superseded by Gemma 4. [HF 3n], [llmrun] |
| Gemma 3 1B (int4) | ~0.53 GB | ~0.6–2 GB | 55 tok/s CPU on S24U | Gemma Terms | Text-only chat on low-end phones. [HF 1B] |
| Llama 3.2 1B/3B, Qwen 3 small (llama.cpp Q4) | 0.6 / 1.7 GB | 0.8 / 2.2 GB | SD 8 Gen 2: 25–45 / 12–22 tok/s. Mid-range SD7+ Gen 3: 22–35 / 10–18 tok/s | Llama licence / Apache (Qwen) | Fine fallback for text. [Ertas] |
| **Gemini Nano (AICore, ML Kit Prompt API)** | System-managed, $0 | – | fast | – | Prompt API in beta, supports image input, <4K input tokens, per-app quota. Supported only on a **handful of flagships** (Pixel 9/10, Galaxy S25/S26 for some APIs, Z Fold7). **S23 Ultra is not supported**, and phones with unlocked bootloaders are excluded. Nano 4 reaches ~1–3% of devices. Use only as an opportunistic bonus. [ML Kit GenAI], [Prompt API], [923] |

The MediaPipe LLM Inference API is **maintenance-only**; migrate to **LiteRT-LM**. [MediaPipe]

**Rule of thumb:** an E2B-class model needs about 6–8 GB of total phone RAM to run comfortably. That's realistic on an SD 8 Gen 2 such as the S23 Ultra, but not on 3–4 GB budget phones, which are common in Pakistan. Expect a 2.6 GB optional download over Wi-Fi.

### 3.2 On-device food recognition
- **Datasets:**
  - Food-101 (101 classes, Western).
  - UEC Food-256 and ISIA Food-500.
  - **Khana**: 80 Indian classes, ~131k images, but **CC BY-NC-ND**, so no commercial training. [Khana]
  - A 2026 **Pakistani-cuisine** dataset: 85 dishes in 10 groups, ~20k images, YOLOv8 at ~79% top-1. Available only "on request" from the authors. [Preprints]
- **Ready model:** Google's **AIY food classifier V1** (TFLite, about 2,000 mostly Western dish classes, MobileNet-sized). It's light but weak on desi food; verify the label list on its Kaggle page. [Kaggle AIY]
- **Recommended pipeline:**
  1. Photo goes to Gemma 4 E2B vision if the device can run it, otherwise to a small classifier you fine-tune yourself (MobileNet/EfficientNet-Lite, about 5–15 MB).
  2. Return the top 3 candidates and fuzzy-match them to the **455-dish database**.
  3. The user taps the right dish and picks a portion (katori / plate / piece).
  4. Calories come from the database, **never** from the model's own estimate.
- **How to build the classifier:** fine-tune on a self-collected dataset of your 455 dishes. You can bootstrap labels from your existing cloud-AI logs, if users consent.
- **Expected accuracy:** about 75–85% top-1 and over 90% top-3 for a fine-tuned desi classifier. Portion estimation from one photo stays rough (MAE about ±20 g in research), which is why the user confirms the portion.

### 3.3 Server-side options
- **Free tiers:**
  - **Cloudflare Workers AI**: 10,000 neurons/day free, about 280K output tokens on Llama 3.1 8B, roughly 1,000 short chat replies a day. After that, $0.011 per 1k neurons. Includes Llama 3.2 11B Vision. Cloudflare commits not to train on your data. [CF], [AIReiter]
  - **Groq** free tier: 30 RPM / 14.4k RPD on Llama 3.1 8B, limits per organization. [AIReiter], [Groq]
  - **Google AI Studio** free tier: Flash and Flash-Lite only, unpublished per-project caps, cut sharply in Dec 2025, and prompts may be used for training. [AgentDeals]
  - **OpenRouter** free models: 50 requests/day.

  **No free tier scales to a large audience.** All limits are per project or organization, not per user.
- **Cheapest paid option:** **Gemini 3.1 Flash-Lite** at $0.25 per 1M input tokens and $1.50 per 1M output, with 50% off in batch mode. [Gemini pricing]
  - **1,000 food photos** (about 1.3k input + 300 output tokens each): **≈ $0.78**.
  - **1,000 chat messages** (about 1.5k input incl. history + 250 output): **≈ $0.75**.
  - So 10k daily active users × 2 photos × 5 chats ≈ **$55/day** if everything went to the cloud. That's the argument for going on-device first.
- **Self-hosting:**
  - RunPod RTX A5000 from $0.27/h ≈ **$195/month** always-on. [RunPod]
  - Hetzner GEX44 (RTX 4000 Ada, 20 GB) **€184/month + €79 setup**; it fits 7–14B models such as Gemma 4 / Qwen vision. [Effloow]
  - One such GPU can serve many thousands of requests a day, but it's a fixed cost plus ops work. Worth it only once paid API spend passes about $200/month.
  - CPU-only servers are too slow for vision models.

### 3.4 Recommended architecture: "unlimited for users, ~$0 for you"
1. **Tier A, capable phones** (≥8 GB RAM, Android 12+, GPU/NPU): optional Gemma 4 E2B download. Unlimited chat and food photos fully offline. Use ML Kit Prompt API instead when Gemini Nano is present, since that needs no download.
2. **Tier B, everyone else:** a tiny bundled food classifier, the dish database and portion picker, plus template or rule-based coaching tips (no LLM needed for "you're 300 kcal under target").
3. **Cloud fallback:**
   - Gemini Flash-Lite through **your own thin proxy** (Cloudflare Worker, free) so the API key isn't in the APK.
   - **Per-user daily cap** (e.g. 5 photos + 20 chat messages), with the Workers AI free quota used first.
   - **Cache** by dish/image hash and common questions.
   - Optional rewarded ad or "Pro" tier to unlock more.
4. **Keep TTS on-device** (Supertonic). Treat ElevenLabs/Azure as paid extras only.
5. **Trade-offs:**
   - 2.6 GB download and storage.
   - Battery and heat during inference.
   - Small models get facts and calories wrong, so always ground answers in your database.
   - 3–4 GB phones get the lighter Tier B experience.
   - Gemma 4 is Apache 2.0; Gemma 3/3n would need the use policy passed through in your terms.

---

## Decision questions for the product owner
1. **Wearable scope for v1?** (a) HC-only, with brand guide screens and a Huawei → Health Sync guide **[rec]** · (b) HC + Whoop/Polar cloud APIs · (c) HC + Huawei Health Kit
2. **Cheap Indian bands (boAt/Noise/Fire-Boltt)?** (a) Tag as "not supported — use phone steps + manual" and test 2–3 models in-house **[rec]** · (b) Reverse-engineer BLE · (c) Ignore
3. **Duplicate data handling?** (a) Per-metric source priority, defaulting to watch over phone **[rec]** · (b) Trust HC's default priority · (c) Single global source
4. **Vitals MVP?** (a) Resting HR, HRV, SpO2, respiratory rate, BP, temp cards + manual BP/glucose entry **[rec]** · (b) HR + SpO2 only · (c) Full set incl. VO2 max and glucose trends
5. **Camera heart-rate feature?** (a) Don't ship in v1 **[rec]** · (b) Ship as "experimental, not medical" · (c) Ship as a headline feature
6. **On-device AI model?** (a) Gemma 4 E2B via LiteRT-LM, as an optional download **[rec]** · (b) Gemma 3 1B text-only (smaller) · (c) None, cloud only
7. **Food recognition approach?** (a) On-device top-3 → match to 455 dishes → user confirms portion; Gemma vision when available **[rec]** · (b) Always cloud LLM · (c) Manual search only
8. **Cloud fallback budget?** (a) Gemini Flash-Lite via a Cloudflare Worker proxy with per-user daily cap, hard monthly spend cap ~$20 **[rec]** · (b) Free tiers only (expect outages at scale) · (c) Uncapped
9. **Train your own desi food classifier?** (a) Yes: collect consented photos of your 455 dishes, fine-tune MobileNet/EfficientNet-Lite **[rec]** · (b) Request the Pakistani research dataset and use it · (c) Use AIY food V1 as-is
10. **Monetize heavier cloud use?** (a) Free cap + optional "Pro" or rewarded ads for more **[rec]** · (b) Everything free, founder absorbs costs · (c) Paywall all AI

---

## Sources
- [Sahha HC] https://sahha.ai/integrations/health-connect/
- [Sahha Xiaomi] https://sahha.ai/integrations/xiaomi/
- [Sahha Huawei] https://sahha.ai/integrations/huawei-health/
- [Sahha OHealth] https://sahha.ai/integrations/in/heytap-ohealth
- [Sahha Ultrahuman] https://sahha.ai/integrations/ultrahuman/
- [Sahha NoiseFit] https://sahha.ai/integrations/noisefit/
- [Sahha Fitbit] https://sahha.ai/blog/fitbit-api-sunset-migration/
- [Sahha Fit] https://sahha.ai/blog/google-fit-api-sunset-migration/
- [Notebookcheck] https://www.notebookcheck.net/Garmin-expands-Google-Health-Connect-compatibility-to-wearables.1047349.0.html
- [Sonar] https://www.sonarhealth.co/en-AU/blog/sync-health-connect-to-garmin/
- [G&W] https://gadgetsandwearables.com/2025/01/24/zepp-health-connect/
- [FitMesh] https://www.fitmesh.fit/en/blog/xiaomi-amazfit-health-connect-data-dashboard
- [FitMesh Huawei] https://www.fitmesh.fit/en/blog/huawei-health-health-connect-sync
- [FitMesh Oura] https://www.fitmesh.fit/en/blog/oura-ring-health-connect-android
- [Polar] https://support.polar.com/en/flow-app-health-connect
- [Android Blog] https://android-developers.googleblog.com/2023/03/withings-reduces-data-sync-code-with--health-and-fitness-api-health-connect.html
- [Whoop community] https://www.community.whoop.com/t/health-connect-fields-on-android-google/14287
- [Garmin FAQ] https://developer.garmin.com/gc-developer-program/program-faq
- [Terra Oura] https://tryterra.co/blog/oura-api-ai-apps-access-permissions-commercial-restrictions
- [Terra Whoop] https://tryterra.co/blog/whoop-api-data-access-permissions-limitations-2026
- [HealthSync] https://healthsync.app/
- [HC data types] https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types
- [Frontiers 2024] https://frontiersin.org/articles/10.3389/fdgth.2024.1326511/full
- [Android Central] https://www.androidcentral.com/pixel-6-gets-google-fit-heart-respiratory-rate-tracking
- [SamMobile] https://www.sammobile.com/2019/03/29/galaxy-s10e-no-heart-rate-sensor
- [ML Kit GenAI] https://developers.google.com/ml-kit/genai
- [Prompt API] https://developers.google.com/ml-kit/genai/prompt/android/get-started
- [923] https://www.ninetwothree.co/blog/gemini-nano-4-device-support
- [MediaPipe] https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
- [HF Gemma4] https://huggingface.co/huggingworld/gemma-4-E2B-it-litert-lm
- [Implicator] https://www.implicator.ai/google-releases-gemma-4-under-apache-2-0-dropping-its-custom-ai-license/
- [HF 3n] https://huggingface.co/google/gemma-3n-E4B-it-litert-lm
- [llmrun] https://llmrun.dev/model/google-gemma-3n-e2b-it-litert-lm
- [HF 1B] https://huggingface.co/litert-community/Gemma3-1B-IT
- [Gemma terms] https://ai.google.dev/gemma/terms
- [Ertas] https://www.ertas.ai/blog/llm-android-benchmarks-snapdragon-tensor
- [Khana] https://arxiv.org/html/2509.06006v1
- [Preprints] https://www.preprints.org/manuscript/202608.0821
- [Kaggle AIY] https://www.kaggle.com/models/google/aiy
- [CF] https://developers.cloudflare.com/workers-ai/platform/pricing/
- [AIReiter] https://aireiter.com/blog/best-free-ai-api
- [Groq] https://console.groq.com/docs/rate-limits
- [AgentDeals] https://agentdeals.dev/gemini-api-pricing-changes
- [Gemini pricing] https://ai.google.dev/gemini-api/docs/pricing
- [RunPod] https://www.runpod.io/gpu-cloud/pricing
- [Effloow] https://effloow.com/articles/hetzner-cloud-ai-gpu-server-guide-2026
