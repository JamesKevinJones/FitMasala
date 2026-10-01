# FitMasala

One person's cutting plan: what they eat, what they lift, and what the scale
says, reconciled into a daily calorie target.

## Eating

**Meal**:
One eating occasion on a day: breakfast, lunch, dinner or snack. Several
dishes eaten together are one Meal.
_Avoid_: using "meal" for a single logged item or row

**Dish**:
One item eaten within a Meal, with its own portion and macros. The unit of
logging and of estimation.
_Avoid_: item, entry, food

**Portion**:
How much of a Dish was eaten, in an Indian household unit first (katori,
roti, piece, plate, glass), grams last.
_Avoid_: serving size, weight

**Katori**:
A fixed 150 ml bowl, the same for every dish. Each catalogue dish turns it
into grams by its own density, so a katori of dal outweighs a katori of rice.
_Avoid_: bowl, cup

## Where numbers come from

**Dish catalogue**:
The sourced table every number comes from: ingredients, each citing USDA
FoodData Central or the label of what is actually bought, and the catalogue
dishes built from them.
_Avoid_: nutrition database, IFCT table, presets

**Catalogue dish**:
One dish in the dish catalogue, defined as ingredient grams per Portion. A
logged Dish takes its macros from one.
_Avoid_: dish (for the definition), preset, food

**Estimate**:
A Dish's macros when they did not come from a label or a scale: from the dish
catalogue, or, on older Dishes, as judged by a cloud model. Stays an Estimate
when its Portion is corrected.
_Avoid_: guess, AI value

**Weighed value**:
A Dish's macros taken from a label or a scale.
_Avoid_: exact value, real value

**Advisory**:
A warning that a result is well-formed but nutritionally suspect, shown to
the user before anything is logged.
_Avoid_: error, validation failure

## Photos

**Recognition**:
The phone suggesting a meal photo's Dishes by comparing it with confirmed
photos. It names dishes; it never produces a number.
_Avoid_: photo estimate, detection, scan

**Suggestion**:
A Dish recognition proposes for a photo, pre-filled on the review sheet or
offered as a chip. Nothing until the user keeps it.
_Avoid_: prediction, match, result

**Confirmed photo**:
A meal photo whose Dishes the user kept on the review sheet. The only thing
recognition learns from; forgotten once every Dish from it is deleted.
_Avoid_: training data, sample, label

## Cooking

**Recipe**:
A catalogue dish that also has a written method.
_Avoid_: generated recipe, AI recipe

**Recipe library**:
All recipes, chosen from and never generated.
_Avoid_: chef, AI chef, on-device generation

## Days

**Valid day**:
A day with at least two Meals logged. The only kind of day the plan learns
maintenance calories from.
_Avoid_: logged day, complete day

**Streak day**:
A Valid day, counted toward the streak. Never awarded for a bigger deficit.
_Avoid_: active day
