# MyFit Tracker: research brief on cycle tracking and diabetes (Oct 2026)

Scope: Kotlin, offline-first, reads Health Connect (HC), users mainly in Pakistan, one user's private data on the device, optional cloud features.

## Key facts (read this first)
- **HC cycle types:** `MenstruationFlowRecord` (one point in time, flow level), `MenstruationPeriodRecord` (start and end), `OvulationTestRecord` (result), `CervicalMucusRecord` (appearance and sensation), `BasalBodyTemperatureRecord` (temperature and measurement location), `IntermenstrualBleedingRecord`, `SexualActivityRecord` (protection used). Each one needs its own read and write permission. `SkinTemperatureRecord` (a baseline plus changes from it) arrived in the HC release that began rolling out in Dec 2025. Pixel Watch 4 and Fitbit are the known writers.
- **HC glucose:** `BloodGlucoseRecord` has these required fields: level (mg/dL or mmol/L), `specimenSource` (capillary, interstitial fluid, plasma, serum, tears, whole blood), `relationToMeal` (general, fasting, before meal, after meal, unknown) and `mealType` (breakfast, lunch, dinner, snack, unknown). **HC has no insulin or medication record type.** Insulin and medicines must be stored in the app's own database.
- **Who writes glucose to HC:** the Dexcom app can share glucose to HC (Settings → Connections), but with a **3-hour delay** and only on supported phones and systems. I could not confirm in 2026 documentation that LibreLink, Accu-Chek or Contour write to HC. Treat CGM data in HC as delayed and incomplete, and let users enter readings by hand.
- **Who writes cycle data to HC:** in practice Samsung Health cycle tracking (Galaxy Watch uses skin temperature, with Natural Cycles technology) and Fitbit/Pixel. Expect few other writers, and check the data source on a real device.
- **Pakistan burden:**
  - **Diabetes:** IDF 2025 (11th edition) puts adult diabetes prevalence at about **30.8%, around 36 million adults**. That is the highest rate in the world and third in absolute numbers. This comes from news reports of the Atlas, so check the figure on diabetesatlas.org before you quote it in the app.
  - **PCOS:** Pakistani studies report prevalence anywhere from **9% to 52%**, depending on criteria and setting. A 2025 Islamabad screening study estimated about 10%.

---

## Part A: Menstrual cycle tracking

### What competitors offer (summary)
- **Logging:** period days, flow, symptoms, mood, discharge or cervical mucus, BBT, ovulation (LH) tests, sex drive or activity, pain. Flo, Clue and Ovia log everything. Apple, Samsung, Fitbit and Garmin log periods, flow, symptoms and some fertility signs.
- **Predictions:**
  - Every app predicts the next period, the fertile window and an estimated ovulation day. Some add a PMS window.
  - The basic method uses averages from the calendar: the median of the last 3 to 6 cycles, with ovulation about 14 days before the next period. Natural Cycles uses BBT. Apple, Samsung Galaxy Watch and Oura use a sustained temperature shift measured at the wrist or finger to estimate ovulation after the fact.
  - Natural Cycles is the only one cleared by the FDA as contraception, including with wearable temperature data. Every other app's predictions are estimates and must not be used as birth control.
- **Insights:** cycle-length variation and flags for irregular, infrequent, long or prolonged periods (Apple). Clue shows trends in cycle and period length.
- **Modes:** trying to conceive, avoiding pregnancy (Natural Cycles only), pregnancy and postpartum (Flo, Ovia), perimenopause (Clue, Flo), teens (Flo).
- **Other:** reminders, partner sharing (Flo, Clue Connect), education content, and suggested workouts or nutrition by cycle phase.
- **Evidence on cycle-synced training:** weak. McNulty et al. 2020 (meta-analysis, *Sports Medicine*) found performance may be *trivially* lower in the early follicular phase, with large differences between individuals. The authors recommend a personalised approach, not general phase-based rules. ACE's 2023 summary calls cycle syncing under-evidenced.
- **Privacy:**
  - After Roe was overturned, Flo added an anonymous mode, Stardust announced end-to-end encryption (Mozilla found problems with its privacy claims), and Clue stressed EU/GDPR storage.
  - Good practice: store locally by default, offer an optional PIN or biometric lock, collect no analytics on cycle fields, provide a one-tap export and full delete, and make backup opt-in and encrypted.
- **Muslim users:**
  - A small group of Islamic apps exists, mostly on iOS: MyHayd, Sila, flowdays, My Muslimah, Tuhr. They track hayd/haiz against istihada and tuhr (purity) days and show prayer and fasting status.
  - A 2024/25 Malaysian study on what Muslim women need from these apps found the same priorities: rulings by madhhab (school of Islamic law), counting missed fasts, and privacy.
  - Mainstream apps (Flo, Clue, Samsung) do not offer this. That makes it a real way to stand out in Pakistan.

### (a) MVP (must-have), ranked
1. **Log period start and end plus daily flow.** This is the core data, and it maps directly to the HC period and flow records.
2. **Predict the next period from calendar averages,** showing a range (for example "likely 3–6 Oct") rather than a single day. It is simple to explain and to defend.
3. **Log symptoms, mood and pain as multi-select chips.** These are what users log most often, and they link to the app's existing mood and sleep data.
4. **App lock (PIN or biometric) and local-only storage by default.** Trust is make-or-break for this feature in Pakistan.
5. **Discreet mode:** neutral labels ("Cycle"), notifications with no content, and an optional alternative launcher icon. This matters for shared phones and family settings.
6. **Insights on cycle length and variability, with gentle prompts to see a doctor.** Show a "consider seeing a doctor" card for:
   - cycles shorter than 21 or longer than 35 days,
   - cycle length varying by more than 7–9 days,
   - periods lasting more than 7 days, or very heavy bleeding (soaking a pad every 1–2 hours),
   - no period for 90 days or more,
   - bleeding between periods,
   - signs of PCOS together with irregular cycles (excess hair growth, acne, weight gain).

   This is cheap to build, valuable, and important given how common PCOS is in Pakistan.
7. **Two-way HC sync** for period, flow and intermenstrual bleeding. This brings in Samsung and Fitbit history.
8. **Period-due reminder and a "log today" nudge,** off by default and with discreet wording.
9. **Export (CSV or PDF) and "delete all cycle data."** Users control their own data.
10. **Persistent disclaimer:** "Predictions are estimates. They are not contraception or a diagnosis."

### (b) Nice to have
- **Haiz/istihada and tuhr mode:**
  - Mark days with no prayer or fast, count missed Ramadan fasts (qaza), and show the minimum and maximum hayd days as defaults that can be changed for each madhhab (Hanafi by default for Pakistan).
  - Wording: "for guidance, consult a scholar." This is the strongest local point of difference.
- **Fertility signs:** cervical mucus, BBT, LH tests and sexual activity (with a protection field). Read and write them through HC, and show a "fertile window (estimate)" only.
- **Ovulation confirmed by skin temperature:** read `SkinTemperatureRecord` or BBT and mark a sustained rise of about 0.2–0.3 °C after ovulation. Label it as looking back, not forward.
- **Modes:** trying to conceive, pregnancy (week counter only), perimenopause (symptom set and irregularity tolerance).
- **Training and nutrition by phase as *self-tracking*:** for example, show "your energy and run pace by phase" from the user's own data. Do not prescribe workouts, because the evidence is weak.
- **Urdu localisation and Urdu education cards** (written by a clinician, with citations).
- **Partner sharing** only through optional cloud with end-to-end encryption. Defer it.

### (c) Avoid / risky
- **Marketing as birth control or offering an "avoid pregnancy / safe days" mode.** That makes the app a regulated medical device (as with Natural Cycles' FDA De Novo clearance) and creates liability if someone gets pregnant.
- **Prescriptive cycle-synced workout or diet plans.** The evidence is trivial or individual, so the claims are misleading.
- **Cloud sync on by default, third-party analytics or ad SDKs on cycle screens.** Post-Roe and family-privacy risk, and Play Store health-data policy problems.
- **Diagnosing PCOS or any other condition.** Flag patterns and suggest seeing a doctor. Never label a condition.
- **A teens mode at launch.** Children's data adds COPPA/UK Children's Code duties and Play Families policy. Set a minimum age of 18 for now, or 16 with care.
- **Religious rulings stated as fact.** Make every rule a setting, and attribute it to the madhhab or a scholar.

---

## Part B: Diabetes management

### What competitors offer (summary)
- **Logging:**
  - mySugr, Glucose Buddy, One Drop, Contour and Glooko: fingerstick readings with tags (fasting, before or after meal, bedtime), carbs, insulin (basal and bolus), oral medicines, HbA1c, activity and notes.
  - Dexcom G7, LibreLink and Clarity: CGM streams, alerts for low and high glucose and fast rise or fall, AGP/overlay charts, time in range (TIR) and GMI. Clarity and LibreView produce PDF reports for clinics.
  - mySugr's bolus calculator is a regulated, CE-marked medical device available in some regions only.
- **Coaching apps (India):** BeatO (connected glucometer plus coaching) and Sugar.fit (CGM plus food-response coaching, "see your glucose response to each meal") show how much South Asian users will pay for coaching. That is a separate business model.
- **Metrics:**
  - TIR targets (Battelino 2019 international consensus): **for T1/T2, TIR 70–180 mg/dL above 70%, below 70 mg/dL under 4%, below 54 mg/dL under 1%, above 180 mg/dL under 25%, above 250 mg/dL under 5%**. Older or high-risk people: TIR above 50% and below 70 mg/dL under 1%. Pregnancy: 63–140 mg/dL, with stricter goals.
  - **GMI (%) = 3.31 + 0.02392 × mean glucose (mg/dL)** (Bergenstal 2018). It needs at least 14 days of CGM data, and the app should not call it HbA1c.
- **Units:** mg/dL is standard in Pakistan and India, and mmol/L in the UK and EU. 1 mmol/L = 18.016 mg/dL.
- **Ramadan:**
  - **IDF-DAR Practical Guidelines 2021** sort fasting risk into high, moderate and low with a points score, and advise a pre-Ramadan medical review.
  - They say to **break the fast** if glucose falls **below 70 mg/dL (3.9 mmol/L)** or rises **above 300 mg/dL (16.6 mmol/L)**, or if the person feels unwell.
  - They recommend checking glucose several times a day (checking does not break the fast).
  - This is highly relevant in Pakistan.
- **Low glucose (ADA):**
  - Level 1 is below 70 mg/dL. Level 2 is below 54 mg/dL. Level 3 is a severe event needing help from another person.
  - The **15-15 rule:** take 15 g of fast-acting carbohydrate, recheck after 15 minutes, and repeat until glucose is back in range. People at risk should have glucagon.

### (a) MVP (must-have), ranked
1. **Manual glucose log** with unit choice (mg/dL by default for Pakistan), a meal tag (the HC `relationToMeal` and `mealType` values) and a note. It maps exactly to `BloodGlucoseRecord`.
2. **HC glucose read (and optional write).** This pulls in Dexcom data (with its 3-hour delay), Samsung or meter data, and labels each value with its source and `specimenSource`.
3. **User-set target ranges and colour coding.** Defaults: fasting 80–130, two hours after a meal under 180, CGM range 70–180. They must be editable, with the note "set with your doctor."
4. **Low-glucose safety card.** It appears automatically when a reading is under 70 and shows the 15-15 rule, "if unconscious, do not give food; call 1122 (Rescue) or 115 (Edhi)," and glucagon if prescribed. The card gives information only and never suggests a dose.
5. **Daily, weekly and 14/30/90-day trends:** mean, readings above and below range, TIR, and an overlay by time of day. Once the user has enough CGM-density data, add the AGP-style chart.
6. **Medicine log (oral and insulin):** name, units taken and time. It records what the user did and **calculates nothing**.
7. **HbA1c log** (lab values), with estimated A1c or GMI shown separately and clearly labelled.
8. **Doctor report as a PDF:** 14 or 90 days, a readings table, TIR, mean, lows, medicines, and the share sheet. Patients in Pakistan bring printed notes to clinics.
9. **Reminders:** test times, medicines, an HbA1c every 3 months, and yearly eye and foot checks.
10. **Disclaimer and diabetes type setting** (type 1, type 2, gestational, prediabetes, unsure). The type adjusts default targets and copy, not the logic.

### (b) Nice to have
- **Meal impact:** link a food-diary entry to the readings before it and 1–2 hours after it ("this meal: +62 mg/dL"). This builds on the existing nutrition features and is the most distinctive feature you could have.
- **Carb totals from the existing food diary** next to each meal. Show grams of carbohydrate per meal for the user to see; the app must not use them in any dose calculation.
- **Desi glycemic index labels (high, medium or low)** from published data:
  - the international GI tables (Atkinson et al. 2021, *Am J Clin Nutr*), which include Indian and Pakistani rice, chapati and roti,
  - the DHRMA study's subcontinent foods (Glasgow),
  - the JPMA Pakistani food GI paper,
  - the Glasgow theses on GI in South Asians.

  No single complete GI table for South Asian foods exists, so show GI as low, medium or high with its source, and do not imply false precision.
- **Ramadan mode:**
  - Suhoor and iftar times; prompts to test before suhoor, mid-day, before iftar and two hours after iftar.
  - A banner when the IDF-DAR break-fast thresholds are crossed.
  - A "pre-Ramadan check-up with your doctor" reminder 6–8 weeks ahead.
  - Do not build the risk calculator itself (leave that to clinicians).
- **Exercise impact:** link the app's workouts and HC exercise data to glucose readings after the workout.
- **Prediabetes or "metabolic health" mode** for non-diabetic users: weight, waist, activity and fasting glucose trends. Given how common diabetes is in Pakistan, this could reach the largest audience.
- **Urdu UI and education** (Diabetic Association of Pakistan or IDF material).

### (c) Avoid / risky
- **Insulin dose, bolus or correction calculators, or any "take X units" advice.** These are regulated medical devices (mySugr's bolus module; the Lilly and Sanofi calculators are FDA-cleared). Getting one wrong can kill someone.
- **Predictive or real-time low and high alarms presented as reliable.** HC data from Dexcom arrives 3 hours late. Never imply real-time monitoring. Leave alarms to the CGM's own app.
- **Treatment advice** ("change your metformin", "stop insulin while fasting"). Always say "discuss with your doctor."
- **Calling GMI or estimated A1c your "A1c"** or using it for diagnosis.
- **Diagnosing diabetes or prediabetes** from logged values. Flag the reading and suggest a lab test instead.
- **Uploading glucose to the cloud by default,** or selling or sharing it.

---

## Decision questions for the product owner
(My recommendation is listed first in each.)
1. **Target audience at launch?** (a) Adults 18+ only (b) 16+ (c) Include teens.
2. **Cycle data storage?** (a) Device only, opt-in encrypted backup (b) Device plus optional cloud sync (c) Cloud by default.
3. **Fertility features in v1?** (a) Show period and fertile-window estimates only, with a "not contraception" label (b) Full TTC mode with BBT, LH and mucus (c) Avoid-pregnancy mode (do not choose this).
4. **Haiz/istihada mode?** (a) v1.1, optional, Hanafi defaults that can be changed for other madhhabs, reviewed by a scholar (b) v1 (c) Never.
5. **Discreet mode scope?** (a) Neutral labels, notifications with no content, app lock (b) Plus an alternative launcher icon (c) None.
6. **Cycle-phase training?** (a) Self-insights from the user's own data only (b) Suggest workouts per phase (c) Full cycle-synced plans.
7. **Diabetes feature scope in v1?** (a) Logging, trends, doctor PDF, reminders (b) Plus meal-impact analysis (c) Plus CGM-style alerts.
8. **Insulin handling?** (a) Log doses only, never calculate (b) Do not log insulin at all (c) Bolus advisor (do not choose this; regulated).
9. **Default glucose unit?** (a) Detect from locale: mg/dL for PK/IN/US, mmol/L for UK/EU, with a toggle (b) Always mg/dL (c) Ask at onboarding.
10. **Ramadan mode?** (a) v1 for diabetes (test prompts plus IDF-DAR break-fast banner), with cycle fasting integration later (b) Both in v1 (c) Not planned.
11. **Desi GI data?** (a) Low, medium or high labels from published sources, with citations (b) Exact GI numbers (c) None.
12. **Medical content review?** (a) Have a Pakistani clinician (gynaecologist or endocrinologist) review all wording and thresholds before launch (b) Internal review only.

---

## Sources
- Health Connect data types: https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types
- HC records package: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/package-summary
- BloodGlucoseRecord: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/BloodGlucoseRecord
- RelationToMealType: https://developer.android.com/reference/android/health/connect/datatypes/BloodGlucoseRecord.RelationToMealType
- HC skin temperature (Dec 2025, Pixel Watch 4 / Fitbit): https://jetstream.blog/en/android-health-connect-skin-temperature-support/
- Dexcom to Health Connect (3-hour delay): https://www.dexcom.com/en-gb/faqs/how-do-i-share-my-glucose-data-s-health-app ; https://www.dexcom.com/en-us/faqs/can-i-share-glucose-data-with-google-health-connect
- Samsung Health cycle tracking / Galaxy Watch skin temperature (Natural Cycles): https://www.samsung.com/sg/support/apps-services/samsung-health-cycle-tracking/ ; https://m.gsmarena.com/samsung_galaxy_watch5_watch5_pro_temperature_based_cycle_tracking-amp-57538.php
- Natural Cycles FDA clearance with wearables: https://www.biospace.com/natural-cycles-receives-fda-clearance-to-integrate-its-birth-control-app-with-data-measured-by-apple-watch ; https://wearable-technologies.com/news/natural-cycles-gets-fda-clearance-to-use-its-birth-control-app-with-wearables
- Flo anonymous mode post-Roe: https://www.mobihealthnews.com/news/period-tracking-app-flo-adds-anonymous-mode-after-roe-decision
- Stardust privacy review (Mozilla): https://www.mozillafoundation.org/es/nothing-personal/stardust-privacy-review/
- McNulty et al. 2020, cycle phase and performance: https://rgu-repository.worktribe.com/output/940959 ; https://rrmacademy.org/library/the-effects-of-menstrual-cycle-phase-on-exercise-performance-in-eumenorrheic-wom-ouqt6anh/
- ACE, "Cycle syncing" (2023): https://acefitness.org/continuing-education/certified/december-2023/8507/hot-topic-cycle-syncing
- PCOS in Pakistan (Khan et al. 2025; reported range 9–52%): https://link.springer.com/article/10.1186/s43043-025-00248-3
- Muslim cycle apps: https://apps.apple.com/us/app/myhayd-app/id6739942459 ; https://apps.apple.com/app/id6760947127 ; https://apps.apple.com/it/app/flowdays/id6756862056 ; https://apps.apple.com/us/app/id1423422477
- Muslim women's menstrual app requirements study: https://myjict.uis.edu.my/index.php/journal/article/view/227
- Battelino 2019 TIR consensus (summaries): https://dagensdiabetes.se/index.php/alla-senaste-nyheter/3104-ada-report-international-consensus-on-time-in-range-tir-cgm-based-targets ; https://primarycarenotebook.com/en-AU/pages/diabetes-and-endocrinology/target-time-in-range-tir-in-diabetes (primary source: Diabetes Care 2019;42:1593–1603)
- GMI formula: https://search.r-project.org/CRAN/refmans/iglu/html/gmi.html ; https://www.healthline.com/health/diabetes/what-is-gmi (primary source: Bergenstal, Diabetes Care 2018;41:2275)
- ADA hypoglycemia / 15-15: https://professional.diabetes.org/sites/dpro/files/2023-12/hypoglycemia_in_diabetes.pdf ; https://www.cdc.gov/diabetes/treatment/treatment-low-blood-sugar-hypoglycemia.html
- IDF-DAR Ramadan Guidelines 2021: https://www.medbox.org/document/diabetes-and-ramadan-practical-guidelines-2021 ; https://diabetesonthenet.com/wp-content/uploads/27-29.-How-to_Ramadan-2023.pdf
- IDF Atlas Pakistan: https://diabetesatlas.org/data-by-location/country/pakistan ; https://www.bolnews.com/latest-news/pakistan-records-worlds-highest-diabetes-rate-in-2025/ ; https://pakobserver.net/idf-report-reveals-diabetes-prevalence-in-pakistan/
- Insulin dose calculators as regulated devices: https://www.mobihealthnews.com/news/eli-lilly-gets-fda-clearance-insulin-dose-calculator-app ; https://www.mobihealthnews.com/news/sanofi-gets-fda-clearance-insulin-dose-calculator-app
- South Asian food GI: https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3259037/ (DHRMA) ; https://archive.jpma.org.pk/article-details/5055 ; https://theses.gla.ac.uk/6600/
