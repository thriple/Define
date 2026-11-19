package com.thriple.define.engine

import kotlin.math.roundToInt
import kotlin.random.Random

enum class Rarity(val label: String, val surgeWeight: Double, val podWeight: Double, val power: Int, val argb: Long) {
    STANDARD("Standard", 70.0, 55.0, 5, 0xFFFFFFFF),
    ENHANCED("Enhanced", 21.0, 28.0, 15, 0xFF00E5FF),
    ELITE("Elite", 7.0, 12.0, 40, 0xFF9D6BFF),
    LEGENDARY("Legendary", 2.0, 4.5, 120, 0xFFFFC233),
    COSMIC("Cosmic", 0.0, 0.5, 400, 0xFFFF2E93),
}

enum class GearSlot(val label: String) { CAPE("Cape"), EMBLEM("Emblem"), VISOR("Visor"), GAUNTLETS("Gauntlets"), BOOTS("Boots"), AURA("Aura") }

data class Gear(val id: String, val name: String, val slot: GearSlot, val rarity: Rarity)

object GearCatalog {
    val all: List<Gear> = listOf(
        Gear("cape_canvas", "Canvas Cape", GearSlot.CAPE, Rarity.STANDARD),
        Gear("boots_street", "Street Boots", GearSlot.BOOTS, Rarity.STANDARD),
        Gear("visor_tint", "Tinted Visor", GearSlot.VISOR, Rarity.STANDARD),
        Gear("gaunt_tape", "Taped Gauntlets", GearSlot.GAUNTLETS, Rarity.STANDARD),
        Gear("emblem_chalk", "Chalk Emblem", GearSlot.EMBLEM, Rarity.STANDARD),
        Gear("aura_warm", "Warm-up Glow", GearSlot.AURA, Rarity.STANDARD),
        Gear("cape_storm", "Storm Cape", GearSlot.CAPE, Rarity.ENHANCED),
        Gear("visor_cyan", "Cyan Scanner", GearSlot.VISOR, Rarity.ENHANCED),
        Gear("boots_spring", "Spring-loaded Boots", GearSlot.BOOTS, Rarity.ENHANCED),
        Gear("gaunt_iron", "Iron Grip Gauntlets", GearSlot.GAUNTLETS, Rarity.ENHANCED),
        Gear("aura_ripple", "Pool Ripple Aura", GearSlot.AURA, Rarity.ENHANCED),
        Gear("cape_sunrise", "Sunrise Cape", GearSlot.CAPE, Rarity.ELITE),
        Gear("emblem_neon", "Neon Emblem", GearSlot.EMBLEM, Rarity.ELITE),
        Gear("visor_hud", "Rep Counter HUD", GearSlot.VISOR, Rarity.ELITE),
        Gear("aura_volt", "Voltage Aura", GearSlot.AURA, Rarity.ELITE),
        Gear("cape_comet", "Comet Tail Cape", GearSlot.CAPE, Rarity.LEGENDARY),
        Gear("gaunt_titan", "Titan Gauntlets", GearSlot.GAUNTLETS, Rarity.LEGENDARY),
        Gear("aura_solar", "Solar Flare Aura", GearSlot.AURA, Rarity.LEGENDARY),
        Gear("emblem_prism", "Prismatic Emblem", GearSlot.EMBLEM, Rarity.COSMIC),
        Gear("aura_nova", "Supernova Aura", GearSlot.AURA, Rarity.COSMIC),
    )
    val byId: Map<String, Gear> = all.associateBy { it.id }
    fun ofRarity(r: Rarity): List<Gear> = all.filter { it.rarity == r }
}

data class SurgeResult(val rarity: Rarity, val power: Int)

data class PodResult(
    val rarity: Rarity,
    val gear: Gear,
    val duplicate: Boolean,
    val power: Int,
    val scrap: Int,
    val xp: Int,
    val pityTriggered: Boolean,
)

object Ranks {
    val names = listOf("Rookie", "Vigilante", "Hero", "Champion", "Legend", "Defined")
    val thresholds = listOf(0, 1500, 5000, 12000, 24000, 42000)

    fun index(xp: Int): Int = thresholds.indexOfLast { xp >= it }.coerceAtLeast(0)
    fun name(xp: Int): String = names[index(xp)]

    /** 0..1 progress toward the next rank (1 at max rank). */
    fun progress(xp: Int): Float {
        val i = index(xp)
        if (i >= thresholds.lastIndex) return 1f
        return (xp - thresholds[i]).toFloat() / (thresholds[i + 1] - thresholds[i])
    }
}

object Rewards {
    const val ELITE_PITY = 10
    const val LEGENDARY_PITY = 40

    /** Streak multiplier: 1.0x at day 0, 3.0x at 30 days. */
    fun momentum(streak: Int): Double = minOf(3.0, 1.0 + streak * (2.0 / 30.0))

    private fun pick(rng: Random, options: List<Rarity>, weight: (Rarity) -> Double): Rarity {
        val total = options.sumOf(weight)
        var r = rng.nextDouble() * total
        for (o in options) {
            r -= weight(o)
            if (r <= 0) return o
        }
        return options.last()
    }

    fun rollSurge(rng: Random, streak: Int): SurgeResult {
        val rarity = pick(rng, Rarity.entries.filter { it.surgeWeight > 0 }) { it.surgeWeight }
        return SurgeResult(rarity, (rarity.power * momentum(streak)).roundToInt())
    }

    /** Opens a supply pod and returns the updated state, with bad-luck protection. */
    fun openPod(state: AppState, rng: Random, bonusXp: Int = 0): Pair<AppState, PodResult> {
        val legendaryDue = state.sinceLegendary + 1 >= LEGENDARY_PITY
        val eliteDue = state.sinceElite + 1 >= ELITE_PITY
        val options = when {
            legendaryDue -> listOf(Rarity.LEGENDARY, Rarity.COSMIC)
            eliteDue -> listOf(Rarity.ELITE, Rarity.LEGENDARY, Rarity.COSMIC)
            else -> Rarity.entries
        }
        val rolled = pick(rng, options) { it.podWeight }
        val pity = legendaryDue || eliteDue
        val pool = GearCatalog.ofRarity(rolled)
        val gear = pool[rng.nextInt(pool.size)]
        val owned = state.gear[gear.id] ?: 0
        val duplicate = owned > 0
        val scrap = if (duplicate) rolled.power / 2 else 0
        val power = (rolled.power * momentum(state.streak)).roundToInt()
        val xp = 40 + rolled.ordinal * 30 + bonusXp
        val next = state.copy(
            sinceElite = if (rolled >= Rarity.ELITE) 0 else state.sinceElite + 1,
            sinceLegendary = if (rolled >= Rarity.LEGENDARY) 0 else state.sinceLegendary + 1,
            gear = state.gear + (gear.id to owned + 1),
            scrap = state.scrap + scrap,
            power = state.power + power,
            xp = state.xp + xp,
        )
        return next to PodResult(rolled, gear, duplicate, power, scrap, xp, pity)
    }
}
