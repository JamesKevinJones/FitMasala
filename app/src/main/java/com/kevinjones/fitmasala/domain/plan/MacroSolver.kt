package com.kevinjones.fitmasala.domain.plan

import kotlin.math.roundToInt

/**
 * Turns a calorie target into grams, in a fixed priority order:
 *
 *   1. Protein — the variable that decides whether the weight lost is fat or
 *      muscle. Set first and never sacrificed.
 *   2. Fat — floored for hormonal function, not optimised.
 *   3. Carbs — whatever is left. Carbs are the training-performance lever, so
 *      they absorb the deficit rather than dictating it.
 *
 * Filling carbs first and letting protein take the remainder is the standard way
 * a cut turns into muscle loss, and at 12% body fat there is no spare muscle to
 * pay with.
 */
object MacroSolver {

    /**
     * Protein per kg of LEAN mass, not bodyweight.
     *
     * Anchoring to bodyweight over-feeds protein to someone carrying a lot of
     * fat and under-feeds a lean person — the opposite of what's needed, since
     * protein requirement rises as body fat falls and the deficit deepens.
     */
    private const val PROTEIN_G_PER_KG_LBM_BASE = 2.2
    private const val PROTEIN_G_PER_KG_LBM_MAX = 2.8

    /** Below roughly 0.5 g/kg bodyweight, hormonal function suffers. 0.6 is the working floor. */
    private const val FAT_G_PER_KG_BW_FLOOR = 0.6
    private const val FAT_G_PER_KG_BW_TARGET = 0.85

    fun solve(
        calories: Int,
        body: BodyComposition,
        deficitFraction: Double,
    ): Pair<MacroTarget, List<PlanWarning>> {
        val warnings = mutableListOf<PlanWarning>()

        // Protein scales up with how aggressive the deficit is: the steeper the
        // cut, the more protein it takes to hold onto lean mass.
        val aggressionScale = (deficitFraction / 0.25).coerceIn(0.0, 1.0)
        val proteinPerKgLbm = PROTEIN_G_PER_KG_LBM_BASE +
            (PROTEIN_G_PER_KG_LBM_MAX - PROTEIN_G_PER_KG_LBM_BASE) * aggressionScale
        val proteinG = (body.leanMassKg * proteinPerKgLbm).roundToInt()

        var fatG = (body.weightKg * FAT_G_PER_KG_BW_TARGET).roundToInt()
        val fatFloorG = (body.weightKg * FAT_G_PER_KG_BW_FLOOR).roundToInt()

        var carbKcal = calories - (proteinG * 4) - (fatG * 9)

        // Squeeze fat toward its floor before touching protein.
        if (carbKcal < 0) {
            val deficitKcal = -carbKcal
            val fatReducibleG = (fatG - fatFloorG).coerceAtLeast(0)
            val fatToCutG = minOf(fatReducibleG, (deficitKcal / 9.0).roundToInt())
            fatG -= fatToCutG
            carbKcal = calories - (proteinG * 4) - (fatG * 9)
        }

        // Still negative: the calorie target genuinely cannot hold this protein
        // plus a survivable fat floor. Flag it — the plan needs a smaller
        // deficit, not a quietly broken macro split.
        if (carbKcal < 0) {
            warnings += PlanWarning.CARBS_SQUEEZED_TO_ZERO
            carbKcal = 0
        }

        val carbsG = (carbKcal / 4.0).roundToInt()

        return MacroTarget(
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
        ) to warnings
    }
}
