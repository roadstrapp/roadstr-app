package app.roadstr.core.journey

data class JourneyAchievement(
    val id: String,
    val level: Int,
    val name: String,
    val thresholdMeters: Long,
    val iconKey: String,
    /** Stable NIP-58 `d` identifier; the issuer pubkey and hosted assets are deployment configuration. */
    val nip58Identifier: String,
    val descriptionEn: String,
    val descriptionIt: String,
    val catalogVersion: Int = AchievementCatalog.VERSION,
) {
    fun description(languageCode: String): String =
        if (languageCode.lowercase().startsWith("it")) descriptionIt else descriptionEn
}

data class JourneyAchievementState(
    val definition: JourneyAchievement,
    val unlockedAtEpochMillis: Long?,
) {
    val unlocked: Boolean get() = unlockedAtEpochMillis != null
}

data class JourneyProgress(
    val currentMeters: Long,
    val previousThresholdMeters: Long,
    val next: JourneyAchievement?,
) {
    val fraction: Float
        get() {
            val target = next ?: return 1f
            val span = target.thresholdMeters - previousThresholdMeters
            if (span <= 0L) return 1f
            return ((currentMeters - previousThresholdMeters).toDouble() / span)
                .coerceIn(0.0, 1.0).toFloat()
        }
}

object AchievementCatalog {
    const val VERSION = 1

    val achievements: List<JourneyAchievement> = listOf(
        badge("ignition", 1, "Ignition", 1, "ignition", "Every journey starts somewhere.", "Ogni viaggio comincia da qualche parte."),
        badge("street_rookie", 2, "Street Rookie", 5, "street_rookie", "The street knows your name now.", "Adesso la strada conosce il tuo nome."),
        badge("detour_hunter", 3, "Detour Hunter", 15, "detour_hunter", "The wrong turn was the right idea.", "La svolta sbagliata era l’idea giusta."),
        badge("wanderlust", 4, "Wanderlust", 30, "wanderlust", "Curiosity has the wheel.", "La curiosità è al volante."),
        badge("wayfinder", 5, "Wayfinder", 60, "wayfinder", "You find roads others miss.", "Trovi strade che gli altri ignorano."),
        badge("road_rebel", 6, "Road Rebel", 120, "road_rebel", "Routes are suggestions, freedom is not.", "Le rotte sono consigli. La libertà no."),
        badge("asphalt_nomad", 7, "Asphalt Nomad", 250, "asphalt_nomad", "Home is wherever the road continues.", "Casa è dove la strada continua."),
        badge("horizon_chaser", 8, "Horizon Chaser", 500, "horizon_chaser", "The horizon keeps moving. So do you.", "L’orizzonte si sposta. Anche tu."),
        badge("long_hauler", 9, "Long Hauler", 1_000, "long_hauler", "A thousand kilometers carved into memory.", "Mille chilometri incisi nella memoria."),
        badge("borderless", 10, "Borderless", 2_000, "borderless", "Borders are just lines on a map.", "I confini sono soltanto linee su una mappa."),
        badge("road_warrior", 11, "Road Warrior", 4_000, "road_warrior", "Weathered, relentless, still moving.", "Temprato, instancabile, ancora in movimento."),
        badge("atlas_runner", 12, "Atlas Runner", 8_000, "atlas_runner", "Maps struggle to keep up with you.", "Le mappe faticano a starti dietro."),
        badge("continental_drifter", 13, "Continental Drifter", 16_000, "continental_drifter", "One continent is never enough.", "Un continente non basta mai."),
        badge("planet_roamer", 14, "Planet Roamer", 32_000, "planet_roamer", "The whole planet feels like open road.", "L’intero pianeta sembra una strada aperta."),
        badge("earthshaker", 15, "Earthshaker", 60_000, "earthshaker", "Your tracks echo around the world.", "Le tue tracce risuonano intorno al mondo."),
        badge("roadstr_myth", 16, "Roadstr Myth", 100_000, "roadstr_myth", "One hundred thousand kilometers. You've become part of the road.", "Centomila chilometri. Sei diventato parte della strada."),
        badge("impossible_road", 17, "The Impossible Road", 1_000_000, "impossible_road", "A million kilometers. Beyond maps. Beyond legends.", "Un milione di chilometri. Oltre le mappe. Oltre le leggende."),
    )

    init {
        require(achievements.map { it.id }.distinct().size == achievements.size)
        require(achievements.map { it.level } == (1..17).toList())
        require(achievements.zipWithNext().all { (a, b) -> a.thresholdMeters < b.thresholdMeters })
    }

    fun states(totalMeters: Long, unlocks: Iterable<JourneyAchievementUnlock>): List<JourneyAchievementState> {
        require(totalMeters >= 0L)
        val byId = unlocks.associateBy { it.achievementId }
        return achievements.map { definition ->
            JourneyAchievementState(
                definition,
                byId[definition.id]?.unlockedAtEpochMillis?.takeIf { totalMeters >= definition.thresholdMeters },
            )
        }
    }

    fun reconcile(
        totalMeters: Long,
        unlocks: Iterable<JourneyAchievementUnlock>,
        unlockedAtEpochMillis: Long,
    ): List<JourneyAchievementUnlock> {
        require(totalMeters >= 0L && unlockedAtEpochMillis >= 0L)
        val existing = unlocks.associateBy { it.achievementId }
        return achievements.filter { totalMeters >= it.thresholdMeters }.map { definition ->
            existing[definition.id] ?: JourneyAchievementUnlock(definition.id, unlockedAtEpochMillis)
        }
    }

    fun progress(totalMeters: Long): JourneyProgress {
        require(totalMeters >= 0L)
        val next = achievements.firstOrNull { totalMeters < it.thresholdMeters }
        val previous = achievements.lastOrNull { totalMeters >= it.thresholdMeters }?.thresholdMeters ?: 0L
        return JourneyProgress(totalMeters, previous, next)
    }

    private fun badge(
        id: String,
        level: Int,
        name: String,
        thresholdKilometers: Long,
        iconKey: String,
        en: String,
        it: String,
    ) = JourneyAchievement(
        id = "roadstr.journey.$id.v1",
        level = level,
        name = name,
        thresholdMeters = Math.multiplyExact(thresholdKilometers, 1_000L),
        iconKey = iconKey,
        nip58Identifier = "roadstr.journey.$id.v1",
        descriptionEn = en,
        descriptionIt = it,
    )
}
