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

## Where numbers come from

**Estimate**:
A Dish's macros as judged by the model, from a photo or a description. Stays
an Estimate when its Portion is corrected.
_Avoid_: guess, AI value

**Weighed value**:
A Dish's macros taken from a label or a scale.
_Avoid_: exact value, real value

**Advisory**:
A warning that a result is well-formed but nutritionally suspect, shown to
the user before anything is logged.
_Avoid_: error, validation failure

## Days

**Valid day**:
A day with at least two Meals logged. The only kind of day the plan learns
maintenance calories from.
_Avoid_: logged day, complete day

**Streak day**:
A Valid day, counted toward the streak. Never awarded for a bigger deficit.
_Avoid_: active day
