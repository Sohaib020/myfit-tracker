"""R32 international dishes for the 8 food regions. Values are MyFit typical estimates (basis "typical") —
recipes and portions vary. Run:  python3 tools/foodicon/world_dishes.py
It appends new ids to app/src/main/assets/foods_pk.json (existing ids untouched), adds icon prompts to
tools/foodicon/desc.json and writes tools/foodicon/only.txt so the Food icons workflow renders just these.

Tuple: (id, name, category, serving, unit, grams, kcal, protein, carbs, fat, fiber, aliases, regions, icon description)
Regions: pk Pakistan · in India · me Middle East & Gulf · uk UK · us USA & Canada · eu Mediterranean & Europe ·
ea East Asia · sea Southeast Asia
"""
import json, os, sys

ME, UK, US, EU, EA, SEA, IN, PK = "me", "uk", "us", "eu", "ea", "sea", "in", "pk"
D = [
# ---------------- Middle Eastern & Gulf
("hummus", "Hummus", "Middle Eastern", 1, "bowl", 150, 265, 11.8, 21, 14.4, 9, ["humus", "houmous", "chickpea dip"], [ME, EU, UK], "a shallow bowl of smooth creamy hummus swirled with olive oil, sprinkled with paprika and whole chickpeas"),
("falafel", "Falafel (4 pieces)", "Middle Eastern", 4, "piece", 100, 333, 13, 32, 18, 5, ["falafal", "taameya", "felafel"], [ME, EU], "four round crisp golden-brown falafel balls, one broken open showing green herby inside"),
("falafel_wrap", "Falafel wrap", "Middle Eastern", 1, "piece", 250, 560, 18, 70, 23, 9, ["falafel sandwich", "falafel roll"], [ME, EU, UK], "a rolled flatbread wrap filled with falafel, lettuce, tomato, pickles and white tahini sauce, cut in half"),
("chicken_kabsa", "Chicken kabsa", "Middle Eastern", 1, "plate", 400, 650, 35, 85, 18, 3, ["kabsa", "kapsa", "saudi rice"], [ME], "a plate of long-grain spiced orange kabsa rice topped with a roasted chicken quarter, raisins and toasted almonds"),
("chicken_machboos", "Chicken machboos", "Middle Eastern", 1, "plate", 400, 640, 34, 84, 18, 3, ["majboos", "machbous", "chicken majboos"], [ME], "a plate of spiced golden machboos rice with a tender chicken piece, fried onions and a slice of dried lime"),
("mansaf", "Mansaf (lamb with jameed)", "Middle Eastern", 1, "plate", 400, 780, 42, 70, 36, 2, ["mansaf lamb"], [ME], "a plate of yellow rice topped with lamb pieces, pine nuts and almonds, white yogurt jameed sauce on the side"),
("harees", "Harees", "Middle Eastern", 1, "bowl", 300, 420, 26, 48, 13, 6, ["harees gulf", "jareesh", "hareesa wheat"], [ME], "a bowl of creamy pounded wheat and chicken harees porridge, drizzled with melted ghee and cinnamon"),
("fattoush", "Fattoush salad", "Middle Eastern", 1, "bowl", 200, 180, 4, 20, 10, 5, ["fatoush"], [ME, EU], "a bowl of fattoush salad with chopped lettuce, tomato, cucumber, radish, mint and crisp toasted pita chips, sumac dusted"),
("tabbouleh", "Tabbouleh", "Middle Eastern", 1, "bowl", 150, 180, 3, 16, 12, 4, ["tabouli", "taboule"], [ME, EU], "a bowl of bright green finely chopped parsley tabbouleh with bulgur, diced tomato, mint and a lemon wedge"),
("moutabal", "Moutabal (baba ganoush)", "Middle Eastern", 1, "bowl", 150, 200, 4, 12, 16, 6, ["baba ganoush", "mutabbal", "baba ghanouj"], [ME, EU], "a shallow bowl of smoky grey-beige roasted eggplant dip with olive oil and pomegranate seeds on top"),
("labneh", "Labneh (2 tbsp)", "Middle Eastern", 1, "serving", 60, 100, 4, 3, 8, 0, ["labne", "strained yogurt"], [ME], "a small dish of thick white labneh yogurt cheese with an olive oil pool and a sprinkle of za'atar"),
("manakish_zaatar", "Manakish za'atar", "Middle Eastern", 1, "piece", 140, 420, 10, 52, 19, 4, ["manakeesh", "manaeesh", "zaatar bread"], [ME], "a round flatbread manakish covered with green-brown za'atar herb and olive oil, baked golden, cut in quarters"),
("manakish_cheese", "Manakish with cheese", "Middle Eastern", 1, "piece", 160, 500, 19, 50, 24, 2, ["cheese manakish", "jibneh manakish"], [ME], "a round flatbread manakish topped with melted bubbly white akkawi cheese, baked golden"),
("shakshuka", "Shakshuka", "Middle Eastern", 1, "plate", 300, 330, 18, 18, 21, 5, ["shakshouka", "eggs in tomato"], [ME, EU, UK], "a small skillet of red tomato and pepper sauce with three poached eggs, sprinkled with parsley"),
("foul_medames", "Ful medames", "Middle Eastern", 1, "bowl", 250, 300, 16, 38, 9, 13, ["foul", "ful", "fava beans"], [ME], "a bowl of stewed brown fava beans mashed with olive oil, cumin, chopped tomato and parsley"),
("mixed_grill", "Arabic mixed grill", "Middle Eastern", 1, "plate", 350, 780, 70, 8, 52, 1, ["mix grill", "mashawi"], [ME], "a platter of grilled meats: chicken tawook cubes, lamb kofta skewer and lamb chops with grilled tomato and onion"),
("shish_tawook", "Shish tawook", "Middle Eastern", 1, "plate", 200, 380, 48, 6, 18, 1, ["shish taouk", "chicken tikka arabic", "tawook"], [ME], "two skewers of marinated grilled white chicken cubes with charred edges, garlic toum on the side"),
("arabic_coffee", "Arabic coffee (1 finjan)", "Drinks", 1, "cup", 60, 5, 0.1, 1, 0, 0, ["gahwa", "qahwa", "kahwa arabi"], [ME], "a small handle-less finjan cup of pale golden arabic coffee beside a brass dallah coffee pot and two dates"),
("luqaimat", "Luqaimat (5 pieces)", "Sweets", 5, "piece", 100, 400, 5, 55, 18, 1, ["luqaimat", "lokma", "awamat"], [ME], "five small round golden fried dough balls luqaimat glazed with dark date syrup and sesame seeds"),
("umm_ali", "Umm Ali", "Sweets", 1, "bowl", 200, 470, 10, 52, 25, 1, ["om ali", "umali"], [ME], "a bowl of baked pastry bread pudding umm ali with golden crust, pistachios, raisins and coconut flakes"),
("kibbeh", "Kibbeh (3 pieces)", "Middle Eastern", 3, "piece", 150, 420, 18, 30, 25, 4, ["kibbe", "kubba"], [ME], "three torpedo-shaped crisp brown fried kibbeh, one cut open showing minced meat and pine nuts"),
("fatayer", "Spinach fatayer (3 pieces)", "Middle Eastern", 3, "piece", 150, 380, 9, 50, 16, 4, ["fatayer sabanekh", "spinach pies"], [ME], "three triangle-shaped baked spinach fatayer pastries, golden brown, one showing green spinach filling"),
("warak_enab", "Stuffed vine leaves (6)", "Middle Eastern", 6, "piece", 120, 210, 4, 28, 9, 4, ["dolma", "warak enab", "yalanji", "dolmades"], [ME, EU], "six neatly rolled stuffed grape vine leaves dolma glistening with olive oil, with a lemon wedge"),
("shorbat_adas", "Lentil soup (shorbat adas)", "Middle Eastern", 1, "bowl", 300, 230, 13, 34, 5, 8, ["adas soup", "red lentil soup", "mercimek"], [ME, EU], "a bowl of smooth orange red lentil soup with a lemon wedge, olive oil drizzle and crisp bread croutons"),
("karak_chai", "Karak chai", "Drinks", 1, "cup", 200, 120, 3.5, 18, 4, 0, ["karak", "karak tea"], [ME], "a glass cup of strong milky orange-brown karak tea with cardamom pods beside it"),
("mutabbaq", "Mutabbaq (savoury)", "Middle Eastern", 1, "piece", 200, 520, 16, 50, 28, 2, ["murtabak arabic", "motabbaq"], [ME], "a square folded crisp pan-fried mutabbaq pastry cut in pieces showing egg, minced meat and leek filling"),
("koshari", "Koshari", "Middle Eastern", 1, "plate", 400, 620, 20, 110, 12, 12, ["kushari", "koshary"], [ME], "a bowl of koshari: rice, lentils and macaroni topped with tomato sauce, chickpeas and crispy fried onions"),
("grilled_hammour", "Grilled hammour (fish)", "Middle Eastern", 1, "plate", 250, 330, 50, 2, 13, 0, ["hamour", "grouper", "samak mashwi"], [ME], "a grilled white fish fillet hammour with golden char marks, lemon slices and parsley on a plate"),
# ---------------- British
("full_english", "Full English breakfast (bacon & sausage)", "British", 1, "plate", 450, 850, 42, 52, 52, 8, ["fry up", "english breakfast", "full breakfast"], [UK], "a plate of full english breakfast: fried egg, bacon rashers, sausages, baked beans, grilled tomato, mushrooms and toast"),
("halal_full_english", "Full English (beef sausage, turkey rashers)", "British", 1, "plate", 450, 780, 45, 52, 44, 8, ["halal fry up", "halal english breakfast"], [UK], "a plate of breakfast with fried egg, two beef sausages, turkey rashers, baked beans, grilled tomato, mushrooms and toast"),
("beans_on_toast", "Beans on toast", "British", 1, "plate", 250, 380, 16, 62, 7, 11, ["baked beans toast"], [UK], "two slices of golden toast covered with orange baked beans in tomato sauce on a plate"),
("jacket_potato_beans", "Jacket potato with beans", "British", 1, "plate", 400, 450, 15, 85, 5, 14, ["baked potato beans", "jacket spud"], [UK], "a split crispy-skinned baked jacket potato topped with baked beans and a little butter"),
("jacket_potato_cheese", "Jacket potato with cheese", "British", 1, "plate", 330, 520, 18, 70, 19, 7, ["baked potato cheese"], [UK, US], "a split crispy baked jacket potato filled with melted grated cheddar cheese"),
("shepherds_pie", "Shepherd's pie", "British", 1, "plate", 350, 450, 24, 35, 24, 4, ["shepherds pie", "lamb pie"], [UK], "a portion of shepherd's pie: minced lamb and vegetables under a golden browned mashed potato top"),
("cottage_pie", "Cottage pie", "British", 1, "plate", 350, 460, 25, 36, 24, 4, ["beef cottage pie"], [UK], "a square portion of cottage pie with beef mince and gravy under golden ridged mashed potato"),
("roast_chicken_dinner", "Sunday roast (chicken)", "British", 1, "plate", 450, 700, 50, 55, 30, 7, ["roast dinner", "chicken roast dinner", "sunday dinner"], [UK], "a plate of roast chicken slices with roast potatoes, carrots, peas, broccoli, a yorkshire pudding and gravy"),
("roast_beef_dinner", "Roast beef & Yorkshire pudding", "British", 1, "plate", 450, 780, 48, 60, 38, 6, ["roast beef dinner", "beef roast"], [UK], "a plate of sliced pink roast beef, a puffy yorkshire pudding, roast potatoes, vegetables and brown gravy"),
("bangers_mash", "Bangers & mash (pork sausages)", "British", 1, "plate", 400, 680, 25, 55, 40, 5, ["sausage and mash", "bangers and mash"], [UK], "a plate of creamy mashed potato with three browned sausages, onion gravy and green peas"),
("cornish_pasty", "Cornish pasty", "British", 1, "piece", 230, 650, 16, 60, 38, 4, ["pasty", "steak pasty"], [UK], "a half-moon golden crimped cornish pasty pastry, one end broken showing beef, potato and swede"),
("sausage_roll", "Sausage roll (pork)", "British", 1, "piece", 130, 410, 11, 30, 27, 1.5, ["greggs sausage roll"], [UK], "a flaky golden puff pastry sausage roll with a scored top, cut to show the meat filling"),
("scone_jam_cream", "Scone with jam & cream", "British", 1, "piece", 120, 420, 6, 55, 20, 1.5, ["cream tea", "scone"], [UK], "a split golden scone topped with red strawberry jam and thick clotted cream"),
("crumpets", "Crumpets with butter (2)", "British", 2, "piece", 130, 300, 7, 48, 9, 2, ["crumpet"], [UK], "two round toasted crumpets with holes on top and melting butter"),
("tikka_masala_rice", "Chicken tikka masala with rice", "British", 1, "plate", 450, 820, 40, 90, 32, 4, ["ctm", "tikka masala"], [UK, IN], "a plate of creamy orange chicken tikka masala curry beside fluffy white basmati rice with coriander"),
("ploughmans", "Ploughman's lunch", "British", 1, "plate", 350, 750, 30, 60, 42, 5, ["ploughmans lunch"], [UK], "a board with crusty bread, a wedge of cheddar cheese, pickle chutney, apple slices, salad and pickled onions"),
("tea_milk", "Tea with milk (no sugar)", "Drinks", 1, "cup", 250, 40, 2, 3, 1.5, 0, ["builders tea", "english tea", "cuppa"], [UK], "a white mug of strong english tea with milk, light brown colour, a tea bag string on the side"),
("egg_muffin", "Egg & cheese muffin", "British", 1, "piece", 140, 300, 15, 28, 14, 1.5, ["breakfast muffin", "egg mcmuffin"], [UK, US], "a toasted english muffin sandwich with a round egg and melted cheese slice"),
("fish_finger_sandwich", "Fish finger sandwich", "British", 1, "piece", 200, 480, 20, 52, 20, 3, ["fish finger sarnie"], [UK], "a white bread sandwich filled with golden breaded fish fingers, lettuce and tartar sauce"),
("prawn_mayo_sandwich", "Prawn mayo sandwich", "British", 1, "piece", 180, 400, 17, 38, 20, 3, ["prawn sandwich", "meal deal sandwich"], [UK], "a triangle-cut brown bread sandwich filled with pink prawns in mayonnaise and lettuce"),
("sticky_toffee_pudding", "Sticky toffee pudding", "Sweets", 1, "serving", 150, 520, 5, 75, 23, 1.5, ["toffee pudding"], [UK], "a square of dark sticky toffee sponge pudding drenched in glossy caramel sauce with vanilla ice cream"),
("apple_crumble", "Apple crumble with custard", "Sweets", 1, "bowl", 200, 400, 5, 62, 15, 3, ["crumble"], [UK], "a bowl of baked apple crumble with golden crumbly topping and yellow custard poured over"),
("mushy_peas", "Mushy peas", "British", 1, "bowl", 150, 130, 8, 20, 1.5, 7, ["mushy pea"], [UK], "a small bowl of bright green mashed mushy peas with a knob of butter"),
# ---------------- American & Canadian
("pancakes", "Pancakes with syrup (3)", "American", 3, "piece", 230, 520, 12, 85, 14, 2, ["hotcakes", "american pancakes"], [US, UK], "a stack of three fluffy golden pancakes with a pat of butter and maple syrup dripping down"),
("waffles", "Waffles (2)", "American", 2, "piece", 150, 450, 10, 55, 21, 2, ["belgian waffle", "waffle"], [US, EU], "two golden square waffles with deep pockets, topped with strawberries and a dusting of powdered sugar"),
("bagel_cream_cheese", "Bagel with cream cheese", "American", 1, "piece", 140, 400, 13, 58, 13, 2.5, ["bagel", "cream cheese bagel"], [US], "a sliced toasted sesame bagel spread thickly with white cream cheese"),
("mac_cheese", "Mac & cheese", "American", 1, "bowl", 250, 500, 20, 50, 25, 2, ["macaroni and cheese", "mac n cheese"], [US, UK], "a bowl of creamy orange macaroni and cheese with a golden baked breadcrumb top"),
("bbq_ribs", "BBQ pork ribs (half rack)", "American", 1, "serving", 300, 820, 55, 25, 56, 0.5, ["ribs", "pork ribs", "spare ribs"], [US], "a half rack of glossy barbecue-sauced pork ribs with charred edges on a board"),
("buffalo_wings", "Buffalo wings (6)", "American", 6, "piece", 200, 520, 40, 4, 38, 0, ["hot wings", "wings buffalo"], [US], "six glossy red-orange buffalo chicken wings with celery sticks and a small cup of white blue cheese dip"),
("cobb_salad", "Cobb salad", "American", 1, "bowl", 350, 520, 38, 12, 36, 6, ["cobb"], [US], "a bowl of cobb salad with rows of grilled chicken, boiled egg, avocado, tomato, crumbled cheese and lettuce"),
("caesar_salad", "Chicken Caesar salad", "American", 1, "bowl", 300, 450, 32, 18, 28, 3, ["caesar salad"], [US, UK, EU], "a bowl of romaine lettuce with sliced grilled chicken, croutons, shaved parmesan and creamy caesar dressing"),
("chicken_burrito", "Chicken burrito", "American", 1, "piece", 350, 700, 38, 80, 25, 10, ["burrito"], [US, UK], "a large foil-wrapped chicken burrito cut in half showing rice, beans, chicken, salsa and cheese inside"),
("burrito_bowl", "Burrito bowl", "American", 1, "bowl", 450, 650, 40, 75, 20, 12, ["chipotle bowl", "rice bowl mexican"], [US, UK], "a bowl with rice, black beans, grilled chicken, corn, salsa, guacamole and lettuce in sections"),
("beef_tacos", "Beef tacos (3)", "American", 3, "piece", 270, 570, 28, 45, 30, 6, ["tacos", "taco"], [US], "three crunchy taco shells filled with seasoned beef mince, shredded lettuce, cheese and tomato"),
("chicken_quesadilla", "Chicken quesadilla", "American", 1, "piece", 230, 600, 34, 45, 32, 3, ["quesadilla"], [US], "a grilled flour tortilla quesadilla cut into triangles oozing melted cheese and chicken, sour cream on the side"),
("nachos", "Nachos with cheese", "American", 1, "plate", 250, 690, 18, 70, 38, 7, ["loaded nachos"], [US, UK], "a plate of tortilla chips covered in melted yellow cheese, jalapenos, salsa and sour cream"),
("pbj_sandwich", "Peanut butter & jelly sandwich", "American", 1, "piece", 110, 380, 13, 48, 16, 4, ["pb&j", "pbj", "peanut butter jam sandwich"], [US], "a white bread sandwich cut diagonally showing peanut butter and purple grape jelly layers"),
("granola_yogurt", "Granola with yogurt & berries", "American", 1, "bowl", 250, 420, 15, 58, 14, 6, ["yogurt parfait", "granola bowl"], [US, UK, EU], "a glass bowl of white yogurt topped with crunchy golden granola, blueberries and strawberries, honey drizzle"),
("chicken_noodle_soup", "Chicken noodle soup", "American", 1, "bowl", 350, 220, 15, 24, 7, 2, ["chicken soup noodles"], [US], "a bowl of clear chicken broth with egg noodles, carrot slices, celery and chicken pieces"),
("clam_chowder", "Clam chowder", "American", 1, "bowl", 300, 380, 14, 30, 22, 1.5, ["chowder", "new england chowder"], [US], "a bowl of thick creamy white clam chowder with potato cubes and oyster crackers"),
("poutine", "Poutine", "American", 1, "plate", 350, 740, 20, 75, 40, 6, ["canadian poutine"], [US], "a dish of thick-cut fries covered in brown gravy and melting white cheese curds"),
("philly_cheesesteak", "Philly cheesesteak", "American", 1, "piece", 300, 750, 40, 60, 38, 3, ["cheesesteak", "steak sandwich"], [US], "a long hoagie roll packed with thinly sliced beef, grilled onions and melted cheese"),
("meatloaf", "Meatloaf (1 slice)", "American", 1, "slice", 180, 380, 25, 14, 24, 1, ["meat loaf"], [US], "a thick slice of beef meatloaf with a glossy red tomato glaze on top"),
("pulled_chicken_sandwich", "BBQ pulled chicken sandwich", "American", 1, "piece", 250, 520, 35, 55, 16, 3, ["pulled chicken burger"], [US], "a brioche bun piled with saucy shredded barbecue chicken and coleslaw"),
("turkey_sandwich", "Turkey sandwich", "American", 1, "piece", 220, 350, 25, 38, 10, 4, ["turkey sub", "turkey club"], [US, UK], "a wholegrain bread sandwich with sliced turkey breast, lettuce, tomato and mustard, cut in half"),
("smoothie_bowl", "Acai smoothie bowl", "American", 1, "bowl", 350, 350, 8, 65, 8, 9, ["acai bowl", "smoothie bowl"], [US, EU, UK], "a purple acai smoothie bowl topped with banana slices, granola, blueberries and coconut flakes"),
("avocado_toast", "Avocado toast", "American", 1, "slice", 130, 280, 7, 26, 17, 7, ["avo toast"], [US, UK, EU], "a slice of toasted sourdough topped with smashed green avocado, chilli flakes and seeds"),
("avocado_egg_toast", "Avocado toast with egg", "American", 1, "slice", 180, 360, 13, 26, 22, 7, ["avo egg toast"], [US, UK, EU], "a slice of toast topped with smashed avocado and a poached egg with runny yolk"),
("apple_pie", "Apple pie (1 slice)", "Sweets", 1, "slice", 125, 300, 2.5, 43, 14, 2, ["apple pie slice"], [US, UK], "a triangular slice of golden lattice-top apple pie showing cinnamon apple filling"),
# ---------------- Mediterranean & European
("greek_salad", "Greek salad", "Mediterranean", 1, "bowl", 250, 280, 8, 12, 23, 4, ["horiatiki"], [EU, UK], "a bowl of greek salad with tomato wedges, cucumber, red onion, black olives and a block of white feta with oregano"),
("chicken_souvlaki", "Chicken souvlaki plate", "Mediterranean", 1, "plate", 400, 650, 45, 55, 26, 4, ["souvlaki"], [EU], "a plate with two grilled chicken souvlaki skewers, warm pita, tzatziki, tomato and red onion"),
("chicken_gyro", "Chicken gyro wrap", "Mediterranean", 1, "piece", 300, 600, 32, 55, 27, 4, ["gyros", "gyro"], [EU, US], "a pita wrap filled with sliced chicken gyro meat, tomato, onion, fries and white tzatziki"),
("moussaka", "Moussaka", "Mediterranean", 1, "plate", 350, 560, 25, 30, 38, 6, ["musaka"], [EU], "a square portion of moussaka showing layers of eggplant, minced meat and golden béchamel top"),
("spaghetti_bolognese", "Spaghetti bolognese", "Mediterranean", 1, "plate", 400, 620, 32, 78, 20, 6, ["spag bol", "bolognese"], [EU, UK, US], "a plate of spaghetti topped with rich red beef bolognese sauce and grated parmesan, basil leaf"),
("carbonara", "Spaghetti carbonara (pancetta)", "Mediterranean", 1, "plate", 350, 680, 27, 72, 30, 3, ["carbonara"], [EU, UK], "a plate of creamy pale yellow spaghetti carbonara with crispy pancetta pieces and black pepper"),
("penne_arrabbiata", "Penne arrabbiata", "Mediterranean", 1, "plate", 350, 480, 15, 85, 9, 6, ["arrabiata", "penne arabiata"], [EU], "a plate of penne pasta in spicy red tomato sauce with chilli flakes and parsley"),
("pizza_margherita", "Pizza margherita (2 slices)", "Mediterranean", 2, "slice", 220, 530, 22, 66, 19, 4, ["margherita", "margarita pizza"], [EU, US, UK], "two slices of neapolitan margherita pizza with tomato sauce, white mozzarella pools and basil leaves"),
("mushroom_risotto", "Mushroom risotto", "Mediterranean", 1, "bowl", 350, 520, 13, 70, 20, 3, ["risotto"], [EU], "a bowl of creamy arborio mushroom risotto with sliced mushrooms, parmesan shavings and parsley"),
("paella", "Seafood paella", "Mediterranean", 1, "plate", 400, 600, 32, 75, 18, 3, ["paella marisco"], [EU], "a pan of saffron yellow paella rice with prawns, mussels, peas, red pepper and lemon wedges"),
("minestrone", "Minestrone soup", "Mediterranean", 1, "bowl", 300, 180, 7, 28, 4.5, 6, ["minestrone"], [EU], "a bowl of chunky vegetable minestrone soup with beans, small pasta and tomato broth"),
("caprese_salad", "Caprese salad", "Mediterranean", 1, "plate", 200, 330, 18, 7, 26, 1.5, ["caprese", "insalata caprese"], [EU], "alternating slices of red tomato and white mozzarella with basil leaves and olive oil drizzle"),
("tzatziki", "Tzatziki", "Mediterranean", 1, "bowl", 100, 100, 4, 5, 7, 0.5, ["tzatziki dip", "cacik"], [EU], "a small bowl of thick white yogurt tzatziki with grated cucumber, dill and olive oil"),
("grilled_halloumi", "Grilled halloumi (3 slices)", "Mediterranean", 3, "slice", 90, 300, 20, 2, 23, 0, ["halloumi", "hellim"], [EU, ME, UK], "three slices of golden grilled halloumi cheese with dark grill marks and a lemon wedge"),
("ratatouille", "Ratatouille", "Mediterranean", 1, "bowl", 300, 210, 4, 24, 11, 7, ["ratatoui"], [EU], "a bowl of ratatouille stew with zucchini, eggplant, red peppers and tomato, thyme sprig"),
("pain_au_chocolat", "Pain au chocolat", "Mediterranean", 1, "piece", 75, 310, 6, 34, 17, 2, ["chocolate croissant"], [EU, UK], "a flaky golden rectangular pain au chocolat pastry with dark chocolate visible at the ends"),
("nutella_crepe", "Crêpe with Nutella", "Mediterranean", 1, "piece", 120, 380, 7, 48, 18, 2, ["crepe", "nutella crepe"], [EU], "a folded thin golden crepe oozing chocolate hazelnut spread, dusted with powdered sugar"),
("tiramisu", "Tiramisu", "Sweets", 1, "slice", 120, 360, 7, 32, 23, 1, ["tiramisu slice"], [EU], "a square slice of tiramisu with layers of coffee-soaked sponge and mascarpone, cocoa dusted top"),
("gnocchi", "Gnocchi in tomato sauce", "Mediterranean", 1, "plate", 300, 450, 10, 82, 9, 5, ["gnocchi"], [EU], "a plate of soft potato gnocchi in red tomato sauce with basil and parmesan"),
("doner_wrap", "Doner kebab wrap", "Mediterranean", 1, "piece", 350, 750, 35, 65, 38, 4, ["doner", "döner", "kebab wrap"], [EU, UK, ME], "a wrapped flatbread doner kebab packed with sliced meat, lettuce, tomato, onion and white garlic sauce"),
("lahmacun", "Lahmacun", "Mediterranean", 1, "piece", 180, 400, 17, 50, 14, 4, ["turkish pizza", "lahmajoun"], [EU, ME], "a thin round crisp lahmacun flatbread topped with spiced minced meat, parsley and a lemon wedge"),
("menemen", "Menemen (Turkish eggs)", "Mediterranean", 1, "plate", 250, 290, 14, 10, 22, 3, ["turkish scrambled eggs"], [EU, ME], "a small pan of soft scrambled eggs with tomatoes and green peppers, menemen, bread on the side"),
("greek_yogurt_honey", "Greek yogurt with honey & nuts", "Mediterranean", 1, "bowl", 200, 280, 18, 30, 10, 1, ["yogurt honey walnuts"], [EU, UK], "a bowl of thick white greek yogurt topped with golden honey drizzle and chopped walnuts"),
("olives", "Olives (10)", "Mediterranean", 10, "piece", 40, 60, 0.4, 1.5, 6, 1.3, ["zaitoon", "black olives", "green olives"], [EU, ME], "a small dish of mixed green and black olives glistening in oil"),
("salmon_veg", "Grilled salmon with vegetables", "Mediterranean", 1, "plate", 350, 520, 40, 18, 32, 5, ["salmon dinner", "salmon plate"], [EU, UK, US], "a grilled salmon fillet with crisp skin beside roasted broccoli, cherry tomatoes and a lemon wedge"),
("goulash", "Beef goulash", "Mediterranean", 1, "bowl", 350, 420, 30, 25, 22, 4, ["gulyas", "goulash soup"], [EU], "a bowl of deep red paprika beef goulash stew with potato chunks and sour cream"),
("chicken_schnitzel", "Chicken schnitzel", "Mediterranean", 1, "piece", 180, 420, 35, 22, 21, 1, ["schnitzel"], [EU], "a golden breaded flat chicken schnitzel with a lemon wedge and parsley"),
("pasta_pesto", "Pasta with pesto", "Mediterranean", 1, "plate", 350, 650, 18, 80, 28, 5, ["pesto pasta"], [EU, UK], "a plate of fusilli pasta coated in bright green basil pesto with pine nuts and parmesan"),
# ---------------- East Asian
("salmon_nigiri", "Salmon nigiri (6)", "East Asian", 6, "piece", 180, 330, 18, 48, 6, 0.5, ["nigiri", "sushi salmon"], [EA, US, UK], "six salmon nigiri sushi: orange salmon slices on oval white rice, on a slate with pickled ginger and wasabi"),
("salmon_sashimi", "Salmon sashimi (6 slices)", "East Asian", 6, "slice", 120, 250, 25, 0, 16, 0, ["sashimi"], [EA], "six thick slices of fresh orange salmon sashimi fanned out with shredded daikon and a shiso leaf"),
("chicken_ramen", "Chicken ramen", "East Asian", 1, "bowl", 550, 600, 32, 70, 20, 3, ["ramen", "shoyu ramen"], [EA, US, UK], "a bowl of ramen noodles in golden broth with sliced chicken, soft-boiled egg half, nori and green onion"),
("tonkotsu_ramen", "Tonkotsu ramen (pork)", "East Asian", 1, "bowl", 650, 800, 35, 75, 38, 3, ["tonkotsu"], [EA], "a bowl of creamy white pork broth ramen with chashu pork slices, egg, bamboo shoots and nori"),
("miso_soup", "Miso soup", "East Asian", 1, "bowl", 250, 60, 4, 6, 2, 1, ["miso"], [EA], "a lacquered bowl of cloudy miso soup with small tofu cubes, wakame seaweed and green onion"),
("teriyaki_chicken", "Teriyaki chicken with rice", "East Asian", 1, "plate", 400, 650, 38, 85, 15, 2, ["chicken teriyaki", "teriyaki bowl"], [EA, US], "a bowl of white rice topped with glossy sliced teriyaki chicken, sesame seeds and green onion"),
("katsu_curry", "Chicken katsu curry", "East Asian", 1, "plate", 500, 900, 38, 110, 32, 5, ["katsu", "japanese curry"], [EA, UK], "a plate of crispy panko chicken katsu slices with brown japanese curry sauce and white rice"),
("udon", "Udon noodle soup", "East Asian", 1, "bowl", 500, 420, 15, 75, 6, 3, ["kake udon", "udon soup"], [EA], "a bowl of thick white udon noodles in clear dashi broth with fish cake, green onion and tempura flakes"),
("onigiri", "Onigiri (rice ball)", "East Asian", 1, "piece", 110, 180, 4, 38, 1, 0.5, ["rice ball", "omusubi"], [EA], "a triangle onigiri rice ball wrapped with a strip of dark nori seaweed"),
("chicken_gyoza", "Chicken gyoza (6)", "East Asian", 6, "piece", 150, 300, 13, 30, 14, 2, ["gyoza", "potstickers"], [EA, UK, US], "six pan-fried gyoza dumplings with crisp golden bottoms and a small dish of dipping sauce"),
("edamame", "Edamame", "East Asian", 1, "cup", 155, 190, 17, 14, 8, 8, ["edamame beans", "soy beans"], [EA, US], "a bowl of bright green steamed edamame pods sprinkled with sea salt"),
("jiaozi", "Steamed dumplings (8)", "East Asian", 8, "piece", 200, 380, 18, 45, 13, 2, ["jiaozi", "chinese dumplings", "momos chicken"], [EA], "eight pleated white steamed dumplings in a bamboo steamer basket with soy dipping sauce"),
("kung_pao_chicken", "Kung pao chicken", "East Asian", 1, "plate", 300, 520, 32, 20, 34, 3, ["kung po chicken", "gong bao"], [EA, US], "a plate of kung pao chicken cubes with roasted peanuts, dried red chillies and green onion in glossy sauce"),
("sweet_sour_chicken", "Sweet & sour chicken", "East Asian", 1, "plate", 350, 620, 25, 70, 26, 2, ["sweet and sour chicken"], [EA, UK, US], "a plate of battered chicken pieces in bright red-orange sweet and sour sauce with pineapple and peppers"),
("mapo_tofu", "Mapo tofu", "East Asian", 1, "bowl", 300, 330, 18, 10, 24, 2, ["mapo doufu"], [EA], "a bowl of soft tofu cubes in spicy glossy red chilli bean sauce with minced meat and sichuan pepper"),
("beef_broccoli", "Beef & broccoli", "East Asian", 1, "plate", 300, 380, 30, 18, 20, 4, ["beef with broccoli"], [EA, US], "a plate of stir-fried sliced beef and bright green broccoli florets in brown garlic sauce"),
("congee", "Chicken congee", "East Asian", 1, "bowl", 400, 280, 14, 45, 4, 1, ["congee", "jook", "rice porridge"], [EA, SEA], "a bowl of silky white rice porridge congee with shredded chicken, ginger strips and green onion"),
("peking_duck", "Peking duck (4 pancakes)", "East Asian", 1, "serving", 200, 520, 25, 30, 33, 2, ["roast duck pancakes"], [EA, UK], "thin pancakes with crispy roast duck slices, cucumber and spring onion strips and hoisin sauce"),
("bibimbap", "Bibimbap", "East Asian", 1, "bowl", 500, 620, 25, 90, 17, 7, ["bibim bap"], [EA], "a stone bowl of rice topped with colourful vegetables, beef, a fried egg and red gochujang"),
("kimchi", "Kimchi", "East Asian", 1, "serving", 100, 25, 1.5, 4, 0.5, 2, ["kimchee"], [EA], "a small dish of red spicy fermented napa cabbage kimchi"),
("bulgogi_rice", "Beef bulgogi with rice", "East Asian", 1, "plate", 400, 650, 32, 80, 22, 3, ["bulgogi"], [EA], "a plate of marinated thin sliced beef bulgogi with onions and sesame beside white rice"),
("korean_fried_chicken", "Korean fried chicken (6)", "East Asian", 6, "piece", 250, 700, 40, 40, 42, 1, ["kfc korean", "yangnyeom chicken"], [EA, US, UK], "six crispy korean fried chicken pieces coated in glossy red sweet chilli glaze with sesame seeds"),
("tteokbokki", "Tteokbokki", "East Asian", 1, "bowl", 300, 450, 9, 90, 6, 3, ["ddeokbokki", "rice cakes spicy"], [EA], "a bowl of cylindrical rice cakes in thick bright red spicy sauce with fish cake and boiled egg"),
("kimbap", "Kimbap (1 roll)", "East Asian", 1, "serving", 230, 380, 12, 65, 8, 3, ["gimbap"], [EA], "slices of kimbap seaweed rice roll showing egg, carrot, spinach and pickled radish inside"),
("japchae", "Japchae", "East Asian", 1, "plate", 250, 380, 10, 55, 14, 3, ["glass noodles korean"], [EA], "a plate of glossy sweet potato glass noodles stir-fried with vegetables, beef strips and sesame"),
("tofu_stir_fry", "Tofu & vegetable stir-fry", "East Asian", 1, "plate", 300, 300, 18, 15, 18, 5, ["tofu stir fry"], [EA, SEA, US], "a plate of golden fried tofu cubes stir-fried with broccoli, peppers and snap peas in light sauce"),
("bubble_tea", "Bubble tea", "Drinks", 1, "glass", 500, 350, 3, 70, 7, 0, ["boba", "pearl milk tea"], [EA, SEA, US], "a tall plastic cup of milky brown bubble tea with black tapioca pearls and a wide straw"),
("matcha_latte", "Matcha latte", "Drinks", 1, "cup", 350, 190, 9, 24, 7, 0.5, ["green tea latte"], [EA, US, UK], "a cup of creamy pale green matcha latte with leaf latte art"),
# ---------------- Southeast Asian
("pad_thai", "Pad Thai (chicken)", "Southeast Asian", 1, "plate", 350, 620, 25, 80, 22, 3, ["pad thai"], [SEA, US, UK], "a plate of pad thai rice noodles with chicken, egg, bean sprouts, crushed peanuts and a lime wedge"),
("green_curry", "Thai green curry with rice", "Southeast Asian", 1, "plate", 450, 720, 30, 75, 33, 4, ["green curry"], [SEA, UK], "a bowl of creamy green thai curry with chicken and eggplant, beside a dome of jasmine rice"),
("tom_yum", "Tom yum soup", "Southeast Asian", 1, "bowl", 350, 180, 15, 10, 8, 2, ["tom yam", "tom yum goong"], [SEA], "a bowl of clear orange-red spicy tom yum soup with prawns, mushrooms, lemongrass and chilli"),
("massaman_curry", "Massaman curry with rice", "Southeast Asian", 1, "plate", 450, 820, 32, 85, 38, 5, ["massaman"], [SEA], "a bowl of rich brown massaman curry with potato chunks, chicken and peanuts beside jasmine rice"),
("pad_kra_pao", "Thai basil chicken with rice & egg", "Southeast Asian", 1, "plate", 400, 650, 35, 70, 25, 2, ["pad kra pao", "kra pow", "basil chicken"], [SEA], "a plate of minced chicken stir-fried with holy basil and chillies, jasmine rice and a crispy fried egg"),
("mango_sticky_rice", "Mango sticky rice", "Sweets", 1, "plate", 250, 480, 6, 85, 14, 3, ["khao niao mamuang"], [SEA], "a plate of white sticky rice with coconut cream drizzle beside ripe yellow mango slices"),
("nasi_lemak", "Nasi lemak", "Southeast Asian", 1, "plate", 350, 700, 25, 75, 33, 4, ["nasi lemak ayam"], [SEA], "a plate of coconut rice with red sambal, fried anchovies, peanuts, cucumber slices and a boiled egg half"),
("nasi_goreng", "Nasi goreng", "Southeast Asian", 1, "plate", 350, 620, 22, 80, 22, 3, ["indonesian fried rice"], [SEA], "a plate of dark sweet soy fried rice nasi goreng topped with a fried egg, prawn crackers and cucumber"),
("mee_goreng", "Mee goreng", "Southeast Asian", 1, "plate", 350, 600, 18, 80, 22, 4, ["mi goreng", "fried noodles malay"], [SEA], "a plate of spicy stir-fried yellow noodles with egg, vegetables, tofu and a lime wedge"),
("laksa", "Laksa", "Southeast Asian", 1, "bowl", 550, 680, 28, 60, 36, 4, ["curry laksa", "curry mee"], [SEA], "a bowl of orange coconut curry laksa broth with noodles, prawns, tofu puffs, egg and bean sprouts"),
("chicken_satay", "Chicken satay (6 sticks)", "Southeast Asian", 6, "piece", 200, 420, 35, 12, 26, 2, ["satay", "sate ayam"], [SEA], "six grilled chicken satay skewers with charred edges beside a bowl of peanut sauce and cucumber"),
("chicken_rice", "Hainanese chicken rice", "Southeast Asian", 1, "plate", 400, 600, 35, 70, 20, 1, ["hainan chicken rice"], [SEA, EA], "a plate of sliced poached chicken over fragrant rice with cucumber slices and chilli and ginger sauces"),
("beef_rendang", "Beef rendang", "Southeast Asian", 1, "serving", 200, 470, 35, 8, 33, 2, ["rendang"], [SEA], "a bowl of dark brown dry-cooked beef rendang chunks coated in caramelised coconut spices"),
("pho_bo", "Pho (beef)", "Southeast Asian", 1, "bowl", 650, 450, 30, 60, 10, 2, ["pho", "beef pho"], [SEA, US], "a big bowl of clear beef broth with rice noodles, thin beef slices, herbs, bean sprouts and lime"),
("pho_ga", "Pho ga (chicken)", "Southeast Asian", 1, "bowl", 650, 400, 30, 55, 7, 2, ["chicken pho"], [SEA], "a big bowl of clear chicken broth pho with rice noodles, shredded chicken, herbs and lime"),
("banh_mi", "Banh mi (chicken)", "Southeast Asian", 1, "piece", 250, 500, 24, 60, 18, 3, ["banh mi"], [SEA, US], "a crusty vietnamese baguette sandwich with grilled chicken, pickled carrot, cucumber, coriander and chilli"),
("goi_cuon", "Fresh spring rolls (2)", "Southeast Asian", 2, "piece", 200, 220, 12, 34, 4, 2, ["summer rolls", "goi cuon", "rice paper rolls"], [SEA], "two translucent rice paper summer rolls showing prawns, herbs and noodles inside, peanut dip"),
("chicken_adobo", "Chicken adobo with rice", "Southeast Asian", 1, "plate", 400, 650, 35, 65, 26, 1, ["adobo"], [SEA], "a plate of dark soy-vinegar braised chicken adobo pieces with garlic, beside white rice"),
("roti_canai", "Roti canai with dhal (2)", "Southeast Asian", 2, "piece", 220, 450, 10, 55, 21, 4, ["roti prata", "paratha malaysian"], [SEA], "two flaky round roti canai flatbreads beside a small bowl of yellow dhal curry"),
("thai_iced_tea", "Thai iced tea", "Drinks", 1, "glass", 350, 250, 3, 40, 8, 0, ["cha yen"], [SEA], "a tall glass of bright orange thai iced tea with a creamy milk layer swirling on top and ice"),
# ---------------- India (more)
("dal_makhani", "Dal makhani", "Indian", 1, "bowl", 250, 360, 13, 32, 20, 9, ["daal makhni", "maa ki daal"], [IN, PK], "a bowl of creamy dark black lentil dal makhani with a swirl of cream and butter"),
("uttapam", "Uttapam", "Indian", 1, "piece", 180, 300, 8, 48, 8, 3, ["uthappam", "onion uttapam"], [IN], "a thick round south indian uttapam pancake topped with onion, tomato and green chilli, coconut chutney"),
("misal_pav", "Misal pav", "Indian", 1, "plate", 350, 520, 18, 70, 18, 12, ["misal"], [IN], "a bowl of spicy orange sprouted bean misal topped with crunchy farsan and onion, with two soft pav buns"),
("pongal", "Ven pongal", "Indian", 1, "bowl", 250, 330, 9, 48, 11, 3, ["khara pongal"], [IN], "a bowl of soft rice and moong dal pongal with ghee, black pepper, cashews and curry leaves"),
("appam_stew", "Appam with stew (2)", "Indian", 2, "piece", 300, 420, 12, 58, 15, 4, ["appam", "hoppers"], [IN], "two lacy bowl-shaped appam pancakes beside a creamy white coconut vegetable stew"),
("paneer_paratha", "Paneer paratha", "Breads", 1, "piece", 150, 380, 14, 42, 17, 4, ["paneer stuffed paratha"], [IN, PK], "a thick round stuffed paratha cut open to show crumbled white paneer filling, golden and buttery"),
("chicken_chettinad", "Chicken chettinad", "Indian", 1, "bowl", 250, 420, 30, 10, 29, 3, ["chettinad chicken"], [IN], "a bowl of dark brown spicy chettinad chicken curry with curry leaves and black pepper"),
("kerala_fish_curry", "Kerala fish curry", "Indian", 1, "bowl", 250, 320, 26, 8, 21, 2, ["meen curry", "fish molee"], [IN], "a clay bowl of red-orange coconut fish curry with kokum and curry leaves"),
("veg_thali", "Veg thali", "Indian", 1, "plate", 600, 850, 25, 120, 30, 16, ["thali", "vegetarian thali"], [IN], "a round steel thali plate with small bowls of dal, sabzi, paneer curry, raita, rice, two rotis and a sweet"),
("pani_puri", "Pani puri (6)", "Indian", 6, "piece", 150, 230, 5, 36, 7, 4, ["golgappa", "puchka"], [IN], "six hollow crisp puris filled with potato and chickpeas beside a glass of green spiced water"),
("kadhi_pakora", "Kadhi pakora", "Indian", 1, "bowl", 250, 280, 10, 22, 17, 3, ["punjabi kadhi", "kadhi"], [IN, PK], "a bowl of yellow yogurt gram-flour kadhi with soft fried onion pakoras and a red chilli tadka"),
# ---------------- Global staples
("quinoa", "Quinoa (cooked)", "Basics", 1, "cup", 185, 222, 8, 39, 3.6, 5, ["quinoa cooked"], [US, UK, EU], "a bowl of fluffy cooked quinoa grains, pale beige with tiny curls"),
("brown_rice", "Brown rice (cooked)", "Rice", 1, "cup", 195, 216, 5, 45, 1.8, 3.5, ["brown rice"], [US, UK, EU, EA, SEA, IN, PK, ME], "a bowl of cooked brown rice grains, light brown and fluffy"),
("avocado", "Avocado (half)", "Fruit", 0.5, "piece", 100, 160, 2, 9, 15, 7, ["butter fruit", "avocado half"], [US, UK, EU], "half a ripe avocado with the brown seed, creamy green flesh and dark skin"),
("broccoli", "Broccoli (steamed)", "Vegetables", 1, "cup", 150, 52, 3.6, 10, 0.6, 5, ["broccoli steamed"], [US, UK, EU, EA], "a small bowl of bright green steamed broccoli florets"),
("blueberries", "Blueberries", "Fruit", 1, "cup", 150, 85, 1.1, 21, 0.5, 3.6, ["blueberry"], [US, UK, EU], "a small bowl heaped with fresh plump blueberries"),
("kiwi", "Kiwi", "Fruit", 1, "piece", 75, 46, 0.9, 11, 0.4, 2.3, ["kiwifruit"], [US, UK, EU, EA], "a whole fuzzy brown kiwi beside a halved kiwi showing bright green flesh and black seeds"),
("tofu", "Tofu (firm)", "Basics", 100, "g", 100, 145, 15.7, 3.5, 8.7, 2.3, ["bean curd", "tofu firm"], [EA, SEA, US], "a white block of firm tofu with a few cubes cut on a small plate"),
("tortilla", "Tortilla wrap", "Breads", 1, "piece", 60, 180, 5, 30, 4.5, 2, ["flour tortilla", "wrap bread"], [US, UK, EU], "a soft round flour tortilla wrap, slightly folded, pale with light toasted spots"),
("sourdough", "Sourdough bread (slice)", "Breads", 1, "slice", 50, 130, 5, 25, 0.8, 1.2, ["sourdough slice"], [US, UK, EU], "a thick slice of rustic sourdough bread with open crumb and dark crust"),
("almond_milk", "Almond milk (unsweetened)", "Drinks", 1, "glass", 250, 40, 1.5, 1.5, 3, 0.5, ["almond milk"], [US, UK, EU], "a glass of white almond milk beside a few whole almonds"),
("oat_milk", "Oat milk", "Drinks", 1, "glass", 250, 120, 3, 16, 5, 2, ["oat milk"], [US, UK, EU], "a glass of creamy pale oat milk beside a small pile of rolled oats"),
]

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
FOODS = os.path.join(ROOT, "app/src/main/assets/foods_pk.json")

def main():
    foods = json.load(open(FOODS))
    have = {f["id"] for f in foods}
    names = {f["name"].lower() for f in foods}
    desc_p = os.path.join(os.path.dirname(__file__), "desc.json")
    desc = json.load(open(desc_p))
    added, warn = [], []
    for t in D:
        i, n, cat, sv, unit, g, kcal, p, c, f, fib, al, reg, d = t
        if i in have or n.lower() in names: warn.append("skip existing " + i); continue
        est = 4 * p + 4 * c + 9 * f
        if abs(est - kcal) / kcal > 0.18: warn.append(f"kcal check {i}: {kcal} vs macros {est:.0f}")
        foods.append({"id": i, "name": n, "category": cat, "serving": sv, "unit": unit, "grams": g, "kcal": kcal, "p": p, "c": c, "f": f,
                      "fiber": fib, "aliases": al, "basis": "typical", "regions": reg})
        desc[i] = d; added.append(i)
    print("\n".join(warn))
    if "--write" in sys.argv:
        json.dump(foods, open(FOODS, "w"), ensure_ascii=False, indent=1)
        json.dump(desc, open(desc_p, "w"), ensure_ascii=False, indent=0)
        open(os.path.join(os.path.dirname(__file__), "only.txt"), "w").write("# R32 world dishes\n" + "\n".join(added) + "\n")
    print("added", len(added), "total", len(foods))

if __name__ == "__main__":
    main()
