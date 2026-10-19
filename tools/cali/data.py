"""Calisthenics skill tree: chains, steps, unlock goals, poses and the new catalogue exercises.

Run gen.py to write app/src/main/assets/calisthenics.json, append new exercises to exercise_catalog.json
and render a preview sheet of every pose. Progression rules follow the r/bodyweightfitness Recommended
Routine idea: work a step at 3 sets of `start`, move on when you hit 3 sets of `goal` (reps or seconds).
"""

# ---------------------------------------------------------------- base poses (see figure.py for angles)
P = {}
P["stand"] = dict(t=180, a=0, f=0, l=0, s=0)
P["stand_arms"] = dict(t=180, a=90, f=90, l=0, s=0)
P["squat_bot"] = dict(t=145, a=90, f=95, l=80, s=345)
P["pu_top"] = dict(t=108, a=0, f=0, l=288, s=288, ft=18)
P["pu_bot"] = dict(t=97, a=277, f=0, l=277, s=277, ft=7)
P["wall_pu_top"] = dict(t=158, a=100, f=100, l=338, s=338)
P["wall_pu_bot"] = dict(t=150, a=60, f=135, l=330, s=330)
P["knee_pu_top"] = dict(t=123, a=0, f=0, l=303, s=255, ft=225)
P["knee_pu_bot"] = dict(t=104, a=277, f=0, l=284, s=255, ft=225)
P["inc_pu_top"] = dict(t=136, a=0, f=0, l=316, s=316, ft=46)
P["inc_pu_bot"] = dict(t=122, a=290, f=8, l=302, s=302, ft=32)
P["dec_pu_top"] = dict(t=94, a=0, f=0, l=274, s=274, ft=4)
P["dec_pu_bot"] = dict(t=82, a=277, f=355, l=262, s=262, ft=352)
P["ppp_top"] = dict(t=106, a=338, f=338, l=286, s=286, ft=16)
P["ppp_bot"] = dict(t=96, a=292, f=345, l=276, s=276, ft=6)
P["arch_top"] = dict(t=108, a1=0, f1=0, a2=35, f2=35, l=288, s=288, ft=18)
P["arch_bot"] = dict(t=97, a1=277, f1=0, a2=62, f2=62, l=277, s=277, ft=7)
P["oap_top"] = dict(t=108, a1=0, f1=0, a2=290, f2=305, l=288, s=288, ft=18)
P["oap_bot"] = dict(t=97, a1=277, f1=0, a2=280, f2=295, l=277, s=277, ft=7)

P["hang"] = dict(t=180, a=180, f=180, l=6, s=356, ft=30)
P["hang_scap"] = dict(t=186, a=172, f=178, l=10, s=0, ft=30)
P["pull_top"] = dict(t=188, a=40, f=180, l=16, s=12, ft=40)
P["pull_top_chin"] = dict(t=186, a=30, f=180, l=16, s=12, ft=40)
P["arch_pull_top"] = dict(t=188, a1=40, f1=180, a2=140, f2=140, l=16, s=12, ft=40)
P["mu_top"] = dict(t=172, a=0, f=0, l=12, s=2, ft=30)
P["oac_top"] = dict(t=188, a1=40, f1=180, a2=10, f2=10, l=16, s=12, ft=40)

P["incrow_bot"] = dict(t=225, a=135, f=135, l=45, s=45, ft=135)
P["incrow_top"] = dict(t=215, a=350, f=150, l=35, s=35, ft=125)
P["invrow_bot"] = dict(t=252, a=162, f=162, l=72, s=72, ft=162)
P["invrow_top"] = dict(t=248, a=355, f=162, l=68, s=68, ft=158)
P["archrow_top"] = dict(t=248, a1=355, f1=162, a2=200, f2=200, l=68, s=68, ft=158)
P["tflrow_bot"] = dict(t=270, a=178, f=178, l=225, s=95, ft=95)
P["tflrow_top"] = dict(t=270, a=5, f=165, l=225, s=95, ft=95)

P["support"] = dict(t=178, a=0, f=0, l=355, s=322, ft=20)
P["dip_bot"] = dict(t=152, a=275, f=0, l=345, s=300, ft=10)
P["sbd_top"] = dict(t=166, a=18, f=18, l=355, s=322, ft=20)
P["sbd_bot"] = dict(t=140, a=290, f=22, l=345, s=300, ft=10)
P["bdip_top"] = dict(t=180, a=345, f=345, l=78, s=78, ft=168)
P["bdip_bot"] = dict(t=176, a=245, f=0, l=78, s=78, ft=168)

P["split_top"] = dict(t=180, a=0, f=0, l1=22, s1=352, l2=342, s2=342, ft1=90, ft2=40)
P["split_bot"] = dict(t=180, a=0, f=0, l1=86, s1=0, l2=2, s2=285, ft1=90, ft2=355)
P["bulg_top"] = dict(t=180, a=0, f=0, l1=20, s1=352, l2=335, s2=270, ft1=90, ft2=270)
P["bulg_bot"] = dict(t=168, a=0, f=0, l1=86, s1=355, l2=355, s2=215, ft1=90, ft2=270)
P["one_leg_top"] = dict(t=180, a=90, f=95, l1=0, s1=0, l2=55, s2=55, ft1=90, ft2=145)
P["boxpis_bot"] = dict(t=140, a=92, f=95, l1=86, s1=352, l2=86, s2=86, ft1=90, ft2=176)
P["pistol_bot"] = dict(t=138, a=92, f=95, l1=74, s1=325, l2=86, s2=86, ft1=90, ft2=176)
P["shrimp_top"] = dict(t=180, a1=90, f1=95, a2=350, f2=340, l1=0, s1=0, l2=355, s2=225, ft1=90, ft2=250)
P["shrimp_bot"] = dict(t=145, a1=92, f1=95, a2=320, f2=330, l1=76, s1=328, l2=345, s2=262, ft1=90, ft2=280)

P["bridge_bot"] = dict(t=270, hd=270, a=90, f=90, l=128, s=12)
P["bridge_top"] = dict(t=292, hd=280, a=88, f=88, l=112, s=12)
P["slbridge_bot"] = dict(t=270, hd=270, a=90, f=90, l1=128, s1=12, l2=128, s2=128, ft2=218)
P["slbridge_top"] = dict(t=292, hd=280, a=88, f=88, l1=112, s1=12, l2=112, s2=112, ft2=202)
P["slrdl_top"] = dict(t=180, a=0, f=0, l1=2, s1=0, l2=345, s2=345, ft1=90, ft2=40)
P["slrdl_bot"] = dict(t=96, a=0, f=0, l1=6, s1=0, l2=274, s2=274, ft1=90, ft2=4)
P["nordic_top"] = dict(t=180, a=0, f=0, l=0, s=270, ft=270)
P["nordic_bot"] = dict(t=118, a=60, f=95, l=298, s=270, ft=270)

P["plank"] = dict(t=98, a=0, f=90, l=278, s=278, ft=8)
P["hollow"] = dict(t=250, hd=248, a=250, f=250, l=72, s=72, ft=72)
P["hkr_top"] = dict(t=180, a=180, f=180, l=92, s=2, ft=40)
P["hlr_top"] = dict(t=180, a=180, f=180, l=92, s=92, ft=100)
P["tuck_l"] = dict(t=180, a=0, f=0, l=150, s=15, ft=60)
P["l_sit"] = dict(t=178, a=0, f=0, l=90, s=90, ft=100)
P["tfl"] = dict(t=270, hd=272, a=172, f=172, l=228, s=95, ft=95)
P["atfl"] = dict(t=270, hd=272, a=170, f=170, l=180, s=90, ft=90)
P["fl"] = dict(t=270, hd=272, a=164, f=164, l=90, s=90, ft=90)

P["pike_top"] = dict(t=40, hd=40, a=40, f=40, l=318, s=318, ft=48)
P["pike_bot"] = dict(t=22, hd=50, a=332, f=12, l=314, s=314, ft=44)
P["epike_top"] = dict(t=12, hd=12, a=12, f=12, l=276, s=276, ft=6)
P["epike_bot"] = dict(t=6, hd=30, a=322, f=14, l=272, s=272, ft=2)
P["hs_wall"] = dict(t=0, hd=0, a=0, f=0, l=176, s=176, ft=176)
P["hs_back"] = dict(t=0, hd=0, a=0, f=0, l=184, s=184, ft=184)
P["hspu_bot"] = dict(t=0, hd=10, a=322, f=18, l=182, s=182, ft=182)
P["hs_free"] = dict(t=0, hd=0, a=0, f=0, l=180, s=180, ft=180)
P["hs_free2"] = dict(t=2, hd=2, a=0, f=0, l=186, s=186, ft=186)

# ---------------------------------------------------------------- chains
# step fields: k = catalogue key (new ones start with "cali_"), n = name, t = reps|hold, start/goal (3 sets of …),
# eq = equipment shown, pr = props drawn, p = pose keyframes, cue = one-line tip.
# New exercises also carry catalogue fields: g (group), pm (primary muscle), s (secondary), l (level), i (how-to).
CHAINS = [
  dict(id="push", name="Push-up", sub="Chest · triceps · shoulders", color="#FF8A3D", steps=[
    dict(k="cali_wall_push_up", n="Wall Push-Up", t="reps", start=10, goal=15, eq="Wall", pr=["wall_f"], p=["wall_pu_top", "wall_pu_bot"],
         cue="Body straight from head to heels, chest to the wall.", g="Chest", pm="Chest", s=["Triceps", "Shoulders"], l="beginner",
         i=["Stand an arm's length from a wall and place your hands on it at shoulder height, a little wider than your shoulders.",
            "Keep your body in one straight line from head to heels and brace your stomach.",
            "Bend your elbows to bring your chest towards the wall, elbows about 45 degrees from your body.",
            "Push back until your arms are straight. Step further from the wall to make it harder."]),
    dict(k="Incline_Push-Up", n="Incline Push-Up", t="reps", start=8, goal=12, eq="Bench or table", pr=["box_h"], p=["inc_pu_top", "inc_pu_bot"],
         cue="Lower the surface as you get stronger."),
    dict(k="cali_knee_push_up", n="Knee Push-Up", t="reps", start=8, goal=12, eq="None", pr=[], p=["knee_pu_top", "knee_pu_bot"],
         cue="Straight line from shoulders to knees — no sagging hips.", g="Chest", pm="Chest", s=["Triceps", "Shoulders"], l="beginner",
         i=["Start on your hands and knees, hands a little wider than your shoulders.",
            "Walk your knees back until your body makes a straight line from your shoulders to your knees.",
            "Lower your chest to just above the floor with your elbows about 45 degrees from your body.",
            "Press back up to straight arms without letting your hips sag or pike."]),
    dict(k="Pushups", n="Push-Up", t="reps", start=5, goal=10, eq="None", pr=[], p=["pu_top", "pu_bot"],
         cue="Chest to a fist's height off the floor, lock out at the top."),
    dict(k="Push-Ups_-_Close_Triceps_Position", n="Diamond Push-Up", t="reps", start=5, goal=10, eq="None", pr=[], p=["pu_top", "pu_bot"],
         cue="Hands close together under your chest, elbows brush your ribs."),
    dict(k="Push-Ups_With_Feet_Elevated", n="Decline Push-Up", t="reps", start=5, goal=10, eq="Bench or step", pr=["box_f"], p=["dec_pu_top", "dec_pu_bot"],
         cue="Feet up on a step, keep the hips in line."),
    dict(k="cali_pseudo_planche_push_up", n="Pseudo Planche Push-Up", t="reps", start=5, goal=8, eq="None", pr=[], p=["ppp_top", "ppp_bot"],
         cue="Lean your shoulders past your hands, fingers turned out.", g="Chest", pm="Shoulders", s=["Chest", "Triceps", "Abdominals"], l="intermediate",
         i=["Get into a push-up position with your hands beside your waist and your fingers turned out to the sides.",
            "Lean forward so your shoulders are well in front of your hands. Keep your arms straight and your body rigid.",
            "Keeping the lean, lower your chest towards the floor with your elbows tucked in.",
            "Press back up without losing the forward lean. Lean further to make it harder."]),
    dict(k="cali_archer_push_up", n="Archer Push-Up", t="reps", start=4, goal=8, eq="None", pr=[], p=["arch_top", "arch_bot"],
         cue="Reps per side. The straight arm only helps a little.", g="Chest", pm="Chest", s=["Triceps", "Shoulders"], l="intermediate",
         i=["Set up in a push-up with your hands placed very wide.",
            "Shift your weight towards one hand and bend that elbow as you lower, keeping the other arm straight.",
            "Lower until your chest is near the working hand, then press back to the middle.",
            "Alternate sides. Count reps per side."]),
    dict(k="Single-Arm_Push-Up", n="One-Arm Push-Up", t="reps", start=3, goal=6, eq="None", pr=[], p=["oap_top", "oap_bot"],
         cue="Feet wide, free hand behind your back, hips square."),
  ]),
  dict(id="pull", name="Pull-up", sub="Lats · biceps · grip", color="#3FA9FF", steps=[
    dict(k="cali_dead_hang", n="Dead Hang", t="hold", start=15, goal=30, eq="Pull-up bar", pr=["bar"], p=["hang", "hang_scap"],
         cue="Arms straight, shoulders pulled a little down.", g="Back", pm="Forearms", s=["Lats", "Shoulders"], l="beginner",
         i=["Grip a pull-up bar with your hands about shoulder-width apart, palms facing away.",
            "Hang with your arms fully straight and your feet off the floor.",
            "Pull your shoulders slightly down away from your ears and keep your legs still.",
            "Breathe steadily and hold for time. Step down with control."]),
    dict(k="Scapular_Pull-Up", n="Scapular Pull-Up", t="reps", start=8, goal=12, eq="Pull-up bar", pr=["bar"], p=["hang", "hang_scap"],
         cue="Arms stay straight — only the shoulder blades move."),
    dict(k="cali_negative_pull_up", n="Negative Pull-Up", t="reps", start=3, goal=5, eq="Pull-up bar", pr=["bar"], p=["pull_top", "hang"],
         cue="Jump to the top and take 5 seconds to lower.", g="Back", pm="Lats", s=["Biceps", "Forearms"], l="beginner",
         i=["Use a box or jump so your chin starts above the bar.",
            "Hold the top for a moment with your chest up.",
            "Lower yourself as slowly as you can — aim for 5 seconds — until your arms are fully straight.",
            "Step back up to the top and repeat."]),
    dict(k="Band_Assisted_Pull-Up", n="Band-Assisted Pull-Up", t="reps", start=5, goal=8, eq="Bar + band", pr=["bar", "band"], p=["hang", "pull_top"],
         cue="Use a lighter band each time you hit the goal."),
    dict(k="Chin-Up", n="Chin-Up", t="reps", start=4, goal=8, eq="Pull-up bar", pr=["bar"], p=["hang", "pull_top_chin"],
         cue="Palms facing you, chin over the bar, full hang at the bottom."),
    dict(k="Pullups", n="Pull-Up", t="reps", start=5, goal=10, eq="Pull-up bar", pr=["bar"], p=["hang", "pull_top"],
         cue="Drive elbows to your ribs, no kipping."),
    dict(k="cali_archer_pull_up", n="Archer Pull-Up", t="reps", start=3, goal=6, eq="Pull-up bar", pr=["bar"], p=["hang", "arch_pull_top"],
         cue="Reps per side. Pull towards one hand, the other arm stays straight.", g="Back", pm="Lats", s=["Biceps", "Forearms"], l="intermediate",
         i=["Take a wide overhand grip on the bar.",
            "Pull yourself up towards one hand while the other arm straightens out along the bar.",
            "Get your chin over the working hand, then lower with control to a full hang.",
            "Alternate sides. Count reps per side."]),
    dict(k="Muscle_Up", n="Muscle-Up", t="reps", start=2, goal=5, eq="Pull-up bar", pr=["bar"], p=["pull_top", "mu_top"],
         cue="Pull high to your chest, then lean over the bar and press."),
    dict(k="One_Arm_Chin-Up", n="One-Arm Chin-Up", t="reps", start=1, goal=3, eq="Pull-up bar", pr=["bar"], p=["hang", "oac_top"],
         cue="The final boss. Hollow body, no swinging."),
  ]),
  dict(id="row", name="Row", sub="Upper back · rear shoulders", color="#22D3B4", steps=[
    dict(k="cali_incline_row", n="Incline Row", t="reps", start=8, goal=12, eq="Low bar or table edge", pr=["bar", "floor"], p=["incrow_bot", "incrow_top"],
         cue="The more upright you are, the easier it is.", g="Back", pm="Middle Back", s=["Lats", "Biceps"], l="beginner",
         i=["Hold a sturdy bar or table edge at about chest height and walk your feet forward so you lean back with straight arms.",
            "Keep your body straight from head to heels.",
            "Pull your chest to the bar, squeezing your shoulder blades together.",
            "Lower with control. Walk your feet further forward to make it harder."]),
    dict(k="Inverted_Row", n="Inverted Row", t="reps", start=6, goal=10, eq="Low bar or rings", pr=["bar", "floor"], p=["invrow_bot", "invrow_top"],
         cue="Body rigid like a plank, chest to the bar."),
    dict(k="cali_archer_row", n="Archer Row", t="reps", start=4, goal=8, eq="Low bar or rings", pr=["bar", "floor"], p=["invrow_bot", "archrow_top"],
         cue="Reps per side. Pull to one hand, the other arm straight.", g="Back", pm="Middle Back", s=["Lats", "Biceps"], l="intermediate",
         i=["Set up under a low bar like an inverted row, with a wide grip.",
            "Pull your chest towards one hand while the other arm stays straight.",
            "Lower with control and switch sides.",
            "Count reps per side."]),
    dict(k="cali_tuck_front_lever_row", n="Tuck Front Lever Row", t="reps", start=3, goal=6, eq="Pull-up bar", pr=["bar"], p=["tflrow_bot", "tflrow_top"],
         cue="Hold a tuck front lever and row your hips to the bar.", g="Back", pm="Lats", s=["Middle Back", "Abdominals", "Biceps"], l="expert",
         i=["Hang from the bar and pull into a tuck front lever: back flat and horizontal, knees tucked to your chest.",
            "Keeping your body horizontal, pull the bar towards your hips by bending your elbows.",
            "Lower back to straight arms without dropping out of the tuck.",
            "Rest between sets — quality over quantity."]),
  ]),
  dict(id="dip", name="Dip", sub="Chest · triceps · shoulders", color="#FF6FB5", steps=[
    dict(k="cali_support_hold", n="Support Hold", t="hold", start=15, goal=30, eq="Parallel bars", pr=["pbars"], p=["support", "support"],
         cue="Arms locked, shoulders pushed down.", g="Triceps", pm="Triceps", s=["Shoulders", "Chest"], l="beginner",
         i=["Grip parallel bars (or two sturdy chairs) and press up so your arms are straight.",
            "Push your shoulders down away from your ears and keep your body still.",
            "Hold for time with steady breathing.",
            "Step down with control."]),
    dict(k="Bench_Dips", n="Bench Dip", t="reps", start=8, goal=15, eq="Bench or chair", pr=["box_h"], p=["bdip_top", "bdip_bot"],
         cue="Keep your back close to the bench, elbows pointing back."),
    dict(k="cali_negative_dip", n="Negative Dip", t="reps", start=3, goal=5, eq="Parallel bars", pr=["pbars"], p=["support", "dip_bot"],
         cue="Take 5 seconds to lower, step back up.", g="Triceps", pm="Triceps", s=["Chest", "Shoulders"], l="beginner",
         i=["Start at the top of a dip on parallel bars with straight arms.",
            "Lean slightly forward and lower yourself as slowly as you can — aim for 5 seconds.",
            "Stop when your shoulders are just below your elbows or you feel a strong stretch.",
            "Step back up to the top and repeat."]),
    dict(k="Parallel_Bar_Dip", n="Dip", t="reps", start=5, goal=10, eq="Parallel bars", pr=["pbars"], p=["support", "dip_bot"],
         cue="Lower to 90 degrees at the elbow, press to lockout."),
    dict(k="cali_straight_bar_dip", n="Straight Bar Dip", t="reps", start=4, goal=8, eq="Pull-up bar", pr=["bar"], p=["sbd_top", "sbd_bot"],
         cue="Lean over the bar — the second half of a muscle-up.", g="Triceps", pm="Triceps", s=["Chest", "Shoulders"], l="intermediate",
         i=["Jump to the top of a straight bar with your arms locked and the bar at your hips.",
            "Lean your chest forward over the bar and lower until the bar is near your chest.",
            "Press back up to straight arms.",
            "Keep your legs slightly forward to stay balanced."]),
    dict(k="Ring_Dips", n="Ring Dip", t="reps", start=4, goal=8, eq="Gymnastic rings", pr=["rings"], p=["support", "dip_bot"],
         cue="Rings turned out at the top, stay tight."),
  ]),
  dict(id="legs", name="Squat", sub="Quads · glutes · balance", color="#2FD37A", steps=[
    dict(k="Bodyweight_Squat", n="Squat", t="reps", start=10, goal=20, eq="None", pr=[], p=["stand_arms", "squat_bot"],
         cue="Thighs at least parallel, heels down."),
    dict(k="cali_split_squat", n="Split Squat", t="reps", start=8, goal=12, eq="None", pr=[], p=["split_top", "split_bot"],
         cue="Reps per leg. Back knee to just above the floor.", g="Legs", pm="Quadriceps", s=["Glutes", "Hamstrings"], l="beginner",
         i=["Stand in a long split stance, one foot forward and the other back on its toes.",
            "Keep your torso tall and lower straight down until your back knee is just above the floor.",
            "Drive up through your front heel.",
            "Finish all reps, then switch legs."]),
    dict(k="cali_bulgarian_split_squat", n="Bulgarian Split Squat", t="reps", start=6, goal=10, eq="Bench or chair", pr=["box_f2"], p=["bulg_top", "bulg_bot"],
         cue="Reps per leg. Back foot on a bench, front knee over toes.", g="Legs", pm="Quadriceps", s=["Glutes", "Hamstrings"], l="intermediate",
         i=["Stand in front of a bench and place the top of your back foot on it.",
            "Lower until your front thigh is about parallel to the floor, keeping your chest up.",
            "Drive up through your front foot.",
            "Finish all reps, then switch legs."]),
    dict(k="cali_box_pistol_squat", n="Box Pistol Squat", t="reps", start=5, goal=8, eq="Bench or box", pr=["box_hip"], p=["one_leg_top", "boxpis_bot"],
         cue="Reps per leg. Sit to the box on one leg, stand without rocking.", g="Legs", pm="Quadriceps", s=["Glutes", "Calves"], l="intermediate",
         i=["Stand on one leg in front of a box or bench, the other leg held out in front.",
            "Reach your arms forward and slowly sit back onto the box.",
            "Without rocking, drive up through your standing foot to stand tall.",
            "Use a lower box as you get stronger."]),
    dict(k="cali_pistol_squat", n="Pistol Squat", t="reps", start=3, goal=6, eq="None", pr=[], p=["one_leg_top", "pistol_bot"],
         cue="Reps per leg. Heel down, free leg straight out front.", g="Legs", pm="Quadriceps", s=["Glutes", "Calves", "Abdominals"], l="expert",
         i=["Stand on one leg with the other leg straight out in front and your arms forward.",
            "Sit down slowly as low as you can control, keeping your heel flat.",
            "Drive back up through your whole foot without letting the free leg touch the floor.",
            "Hold a door frame for balance while you learn it."]),
    dict(k="cali_shrimp_squat", n="Shrimp Squat", t="reps", start=3, goal=6, eq="None", pr=[], p=["shrimp_top", "shrimp_bot"],
         cue="Reps per leg. Hold your back foot, touch the knee down softly.", g="Legs", pm="Quadriceps", s=["Glutes"], l="expert",
         i=["Stand on one leg and hold the other foot behind you with the same-side hand.",
            "Lower slowly until the back knee lightly touches the floor.",
            "Drive up through the standing leg.",
            "Lean your chest forward a little to stay balanced."]),
  ]),
  dict(id="hinge", name="Hinge", sub="Glutes · hamstrings", color="#FFB13B", steps=[
    dict(k="Butt_Lift_Bridge", n="Glute Bridge", t="reps", start=12, goal=20, eq="None", pr=[], p=["bridge_bot", "bridge_top"],
         cue="Squeeze your glutes at the top, don't arch your back."),
    dict(k="cali_single_leg_glute_bridge", n="Single-Leg Glute Bridge", t="reps", start=8, goal=12, eq="None", pr=[], p=["slbridge_bot", "slbridge_top"],
         cue="Reps per leg. Hips level, free leg straight.", g="Glutes", pm="Glutes", s=["Hamstrings"], l="beginner",
         i=["Lie on your back with one knee bent and that foot flat, the other leg straight.",
            "Push through the planted heel to lift your hips until your body is straight from shoulders to knee.",
            "Keep your hips level and squeeze your glute at the top.",
            "Lower slowly. Finish all reps, then switch legs."]),
    dict(k="cali_single_leg_rdl", n="Single-Leg Romanian Deadlift", t="reps", start=6, goal=10, eq="None", pr=[], p=["slrdl_top", "slrdl_bot"],
         cue="Reps per leg. Hips square, back flat, reach long.", g="Legs", pm="Hamstrings", s=["Glutes", "Lower Back"], l="beginner",
         i=["Stand on one leg with a soft knee.",
            "Hinge at the hips, reaching your hands towards the floor while your free leg goes straight back.",
            "Keep your back flat and hips square until your body is about parallel to the floor.",
            "Squeeze your glute to stand tall. Finish all reps, then switch legs."]),
    dict(k="cali_nordic_curl_negative", n="Nordic Curl Negative", t="reps", start=3, goal=6, eq="Anchor for your heels", pr=[], p=["nordic_top", "nordic_bot"],
         cue="Lower as slowly as you can, catch yourself with your hands.", g="Legs", pm="Hamstrings", s=["Glutes", "Calves"], l="intermediate",
         i=["Kneel on a mat with your heels anchored under a sofa or held by a partner.",
            "Keep your body straight from knees to head and slowly lean forward.",
            "Resist with your hamstrings as long as you can, then catch yourself with your hands.",
            "Push back up with your hands and repeat."]),
    dict(k="cali_nordic_curl", n="Nordic Curl", t="reps", start=2, goal=5, eq="Anchor for your heels", pr=[], p=["nordic_bot", "nordic_top"],
         cue="Lower and pull yourself back up with your hamstrings.", g="Legs", pm="Hamstrings", s=["Glutes", "Calves"], l="expert",
         i=["Kneel with your heels anchored firmly.",
            "Lower your straight body forward under control.",
            "Before you touch the floor, curl yourself back up using your hamstrings.",
            "Use a light push from your hands only if you must."]),
  ]),
  dict(id="core", name="Core & L-sit", sub="Abs · hip flexors · levers", color="#FFD34D", steps=[
    dict(k="Plank", n="Plank", t="hold", start=30, goal=60, eq="None", pr=[], p=["plank", "plank"],
         cue="Glutes tight, ribs down, straight line."),
    dict(k="cali_hollow_body_hold", n="Hollow Body Hold", t="hold", start=15, goal=30, eq="None", pr=[], p=["hollow", "hollow"],
         cue="Lower back glued to the floor, arms and legs long.", g="Core", pm="Abdominals", s=["Hip Flexors"], l="beginner",
         i=["Lie on your back with your arms overhead and legs straight.",
            "Press your lower back into the floor and lift your shoulders and legs a little off the ground.",
            "Keep your arms by your ears and toes pointed. Bend your knees to make it easier.",
            "Hold for time with steady breathing."]),
    dict(k="cali_hanging_knee_raise", n="Hanging Knee Raise", t="reps", start=8, goal=12, eq="Pull-up bar", pr=["bar"], p=["hang", "hkr_top"],
         cue="No swinging — lift your knees above your hips.", g="Core", pm="Abdominals", s=["Hip Flexors", "Forearms"], l="beginner",
         i=["Hang from a pull-up bar with straight arms.",
            "Without swinging, lift your knees until your thighs are above parallel.",
            "Lower slowly to a full hang.",
            "Keep your shoulders active and pulled slightly down."]),
    dict(k="Hanging_Leg_Raise", n="Hanging Leg Raise", t="reps", start=6, goal=10, eq="Pull-up bar", pr=["bar"], p=["hang", "hlr_top"],
         cue="Straight legs to hip height, lower slowly."),
    dict(k="cali_tuck_l_sit", n="Tuck L-Sit", t="hold", start=10, goal=20, eq="Floor or parallettes", pr=["plt"], p=["tuck_l", "tuck_l"],
         cue="Arms locked, knees to chest, hips off the floor.", g="Core", pm="Abdominals", s=["Triceps", "Hip Flexors"], l="intermediate",
         i=["Sit between two parallettes or books with your hands beside your hips.",
            "Press down to lock your arms and lift your hips off the floor.",
            "Pull your knees up towards your chest and keep your feet off the ground.",
            "Push your shoulders down and hold for time."]),
    dict(k="cali_l_sit", n="L-Sit", t="hold", start=10, goal=20, eq="Floor or parallettes", pr=["plt"], p=["l_sit", "l_sit"],
         cue="Legs straight at hip height, toes pointed.", g="Core", pm="Abdominals", s=["Triceps", "Hip Flexors", "Quadriceps"], l="expert",
         i=["From a tuck L-sit, straighten your legs out in front of you.",
            "Keep your legs together at hip height and your toes pointed.",
            "Arms locked, shoulders pushed down.",
            "Hold for time. Bend one knee if you need to."]),
    dict(k="cali_tuck_front_lever", n="Tuck Front Lever", t="hold", start=8, goal=15, eq="Pull-up bar", pr=["bar"], p=["tfl", "tfl"],
         cue="Straight arms, back flat and level, knees tucked.", g="Back", pm="Lats", s=["Abdominals", "Shoulders"], l="intermediate",
         i=["Hang from the bar with straight arms.",
            "Pull your knees to your chest and lift your hips until your back is flat and horizontal.",
            "Keep your arms straight and press the bar down towards your hips.",
            "Hold for time and lower with control."]),
    dict(k="cali_advanced_tuck_front_lever", n="Advanced Tuck Front Lever", t="hold", start=6, goal=12, eq="Pull-up bar", pr=["bar"], p=["atfl", "atfl"],
         cue="Open the hips to 90 degrees, back stays flat.", g="Back", pm="Lats", s=["Abdominals", "Shoulders"], l="expert",
         i=["From a tuck front lever, push your knees away from your chest until your hips are at 90 degrees.",
            "Keep your back flat and level and your arms straight.",
            "Hold for time.",
            "Rest well between sets."]),
    dict(k="cali_front_lever", n="Front Lever", t="hold", start=3, goal=8, eq="Pull-up bar", pr=["bar"], p=["fl", "fl"],
         cue="One straight line, parallel to the floor.", g="Back", pm="Lats", s=["Abdominals", "Shoulders", "Lower Back"], l="expert",
         i=["Hang with straight arms and lift your whole body until it is horizontal.",
            "Squeeze your glutes and legs together so your body is one straight line.",
            "Press the bar down towards your hips and hold.",
            "Use a straddle (legs apart) as a stepping stone."]),
  ]),
  dict(id="hand", name="Handstand", sub="Shoulders · balance", color="#B57CFF", steps=[
    dict(k="cali_pike_push_up", n="Pike Push-Up", t="reps", start=5, goal=10, eq="None", pr=[], p=["pike_top", "pike_bot"],
         cue="Hips high, head down between your hands.", g="Shoulders", pm="Shoulders", s=["Triceps", "Chest"], l="beginner",
         i=["Start in a downward-dog shape: hands and feet on the floor, hips high.",
            "Bend your elbows to lower the top of your head towards the floor in front of your hands.",
            "Press back up until your arms are straight.",
            "Walk your feet closer to your hands to make it harder."]),
    dict(k="cali_elevated_pike_push_up", n="Elevated Pike Push-Up", t="reps", start=4, goal=8, eq="Bench or box", pr=["box_f"], p=["epike_top", "epike_bot"],
         cue="Feet on a box, hips stacked over your shoulders.", g="Shoulders", pm="Shoulders", s=["Triceps"], l="intermediate",
         i=["Place your feet on a box or bench and walk your hands back until your hips are high above your shoulders.",
            "Lower your head towards the floor, elbows pointing back.",
            "Press back up to straight arms.",
            "The higher the box, the harder it is."]),
    dict(k="cali_wall_handstand", n="Chest-to-Wall Handstand", t="hold", start=20, goal=45, eq="Wall", pr=["wall_f"], p=["hs_wall", "hs_wall"],
         cue="Walk up the wall, arms locked, body in one line.", g="Shoulders", pm="Shoulders", s=["Triceps", "Abdominals"], l="intermediate",
         i=["Start in a push-up position with your feet against a wall.",
            "Walk your feet up the wall and your hands in until your body is nearly vertical, chest facing the wall.",
            "Lock your arms, push the floor away and squeeze your legs together.",
            "Hold for time. Walk down slowly — never jump down sideways."]),
    dict(k="cali_wall_hspu_negative", n="Wall Handstand Push-Up Negative", t="reps", start=3, goal=5, eq="Wall", pr=["wall_b"], p=["hs_back", "hspu_bot"],
         cue="Lower slowly to a cushion, then come down.", g="Shoulders", pm="Shoulders", s=["Triceps"], l="intermediate",
         i=["Kick up into a handstand with your back to the wall. Place a folded towel or cushion under your head.",
            "Lower yourself as slowly as you can until your head lightly touches the cushion.",
            "Come down from the handstand, rest briefly and repeat.",
            "Stop if you feel any neck pain."]),
    dict(k="Handstand_Push-Ups", n="Wall Handstand Push-Up", t="reps", start=3, goal=6, eq="Wall", pr=["wall_b"], p=["hs_back", "hspu_bot"],
         cue="Head to the cushion and press back to straight arms."),
    dict(k="cali_freestanding_handstand", n="Freestanding Handstand", t="hold", start=5, goal=20, eq="Open floor", pr=[], p=["hs_free", "hs_free2"],
         cue="Fingers grip the floor to balance. Cartwheel out to bail.", g="Shoulders", pm="Shoulders", s=["Forearms", "Abdominals"], l="expert",
         i=["Kick up to a handstand away from walls, on a soft surface.",
            "Lock your arms, stack your hips over your shoulders and squeeze your legs together.",
            "Balance by pressing with your fingers (falling forward) or the heels of your hands (falling back).",
            "Learn to bail safely first: turn a hand and cartwheel out."]),
  ]),
]

# Level test → starting step per chain. Each entry: chain -> list of (min value, step index), checked top-down.
PLACEMENT = {
  "push": ("pushups", [(30, 5), (20, 4), (10, 3), (3, 2), (0, 1)]),
  "pull": ("pullups", [(12, 6), (6, 5), (3, 4), (1, 3)]),        # 0 pull-ups → hang time decides (see app)
  "row": ("pullups", [(10, 2), (3, 1), (0, 0)]),
  "dip": ("pushups", [(30, 3), (20, 2), (10, 1), (0, 0)]),
  "legs": ("squats", [(50, 3), (30, 2), (15, 1), (0, 0)]),
  "hinge": ("squats", [(40, 2), (20, 1), (0, 0)]),
  "core": ("plank", [(90, 3), (60, 2), (30, 1), (0, 0)]),
  "hand": ("pushups", [(25, 1), (0, 0)]),
}
