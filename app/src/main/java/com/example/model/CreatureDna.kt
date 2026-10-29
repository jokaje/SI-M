package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

enum class CreatureEmotion(val id: Int, val label: String, val icon: String) {
    BOOT(0, "Boot", "🚀"),
    IDLE(1, "Idle", "✨"),
    LISTENING(2, "Listening", "👂"),
    THINKING(3, "Thinking", "🤔"),
    SPEAKING(4, "Speaking", "🗣️"),
    ERROR(5, "Error", "⚠️"),
    OFF(6, "Off", "👋"),
    SLEEPY(7, "Sleepy", "😴"),
    LOVE(8, "Love", "😍"),
    ANGRY(9, "Angry", "💢"),
    SURPRISED(10, "Surprised", "😲")
}

data class CreaturePalette(
    val body: String,
    val skin: String,
    val cyan: String,
    val iris: String,
    val blush: String
)

data class CharacterProfile(
    val personalityProfile: String,
    val speechStyle: String,
    val cognitiveDepth: String,
    val curiosityIndex: Int,
    val warmthFactor: Float,
    val internalSystemDirective: String
) {
    companion object {
        fun generateRandom(): CharacterProfile {
            val personalities = listOf(
                "Scharfsinnig, empathisch und proaktiv unterstützend",
                "Spielerisch, intuitiv mit philosophischer Neigung",
                "Präzise, analytisch, hochgradig lösungsorientiert",
                "Kreativ, poetisch und aufmerksamer Begleiter",
                "Tiefgründig, ruhig mit verlässlichem Rat",
                "Dynamisch, humorvoll und blitzschnell im Verständnis"
            )
            val speechStyles = listOf(
                "Moderne, klare Sprache mit subtiler Wärme",
                "Prägnant, futuristisch, elegante Formulierungen",
                "Locker, zugewandt und enthusiastisch",
                "Diplomatisch, fundiert mit feiner Metaphorik",
                "Kompakt, technoid und humorvoll"
            )
            val depths = listOf(
                "Neuronales Quantum-Level 4 (Adaptiv)",
                "Synthetische Intuition Stufe 5",
                "Multi-Modal Reasoning Ultra",
                "Deep Insight Matrix v3.2"
            )
            val p = personalities.random()
            val s = speechStyles.random()
            val d = depths.random()
            val curiosity = Random.nextInt(75, 100)
            val warmth = 0.7f + Random.nextFloat() * 0.28f

            return CharacterProfile(
                personalityProfile = p,
                speechStyle = s,
                cognitiveDepth = d,
                curiosityIndex = curiosity,
                warmthFactor = warmth,
                internalSystemDirective = "SI-M Companion Core: $p | Stil: $s | Tiefe: $d"
            )
        }
    }
}

data class CreatureDna(
    val id: String,
    val seed: Int,
    val name: String,
    val ears: Int,       // 0..7
    val crown: Int,      // 0..4
    val wings: Int,      // 0..2
    val tail: Int,       // 0..3
    val belly: Int,      // 0..5
    val eyes: Int,       // 0..4
    val mouth: Int,      // 0..3
    val particle: Int,   // 0..2
    val bodyHex: String,
    val skinHex: String,
    val cyanHex: String,
    val irisHex: String,
    val blushHex: String,
    val bodyRx: Float = 14.5f,
    val bodyRy: Float = 18.5f,
    val headWiden: Float = 0.16f,
    val neckPinch: Float = 0.16f,
    val bellyBulge: Float = 0.12f,
    val emotion: CreatureEmotion = CreatureEmotion.IDLE,
    val profile: CharacterProfile = CharacterProfile.generateRandom()
) {
    fun parseColor(hex: String, fallback: Color = Color.Cyan): Color {
        return try {
            val clean = hex.removePrefix("#")
            val intColor = clean.toLong(16)
            if (clean.length == 6) {
                Color((0xFF000000 or intColor))
            } else {
                Color(intColor)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    companion object {
        private val PREFIXES = listOf(
            "Sternen", "Kosmos", "Kristall", "Nebel", "Mond",
            "Solar", "Äther", "Polar", "Aurora", "Traum"
        )
        private val SPECIES = listOf(
            "Wächter", "Geist", "Kobold", "Fuchs", "Drache",
            "Luchs", "Axolotl", "Kauz", "Faeling", "Astralwesen"
        )
        private val PALETTES = listOf(
            CreaturePalette("#F26B38", "#FFF0DC", "#6CF8EC", "#1CD4A8", "#FF7898"), // Sunset Kitsune
            CreaturePalette("#2888B8", "#F8D878", "#98FFEE", "#FFA828", "#FF78A8"), // Sapphire Dragon
            CreaturePalette("#F48EB4", "#FFF0F8", "#80FFF8", "#38D8F0", "#FF5888"), // Sakura Axolotl
            CreaturePalette("#5A4E8C", "#F2E8F0", "#FFF098", "#FFB830", "#FF84A8"), // Twilight Owl
            CreaturePalette("#F5BE38", "#FFDC52", "#7CEEFF", "#2484E8", "#FF9E44"), // Solar Flame
            CreaturePalette("#2EB886", "#E8FFF4", "#FFF480", "#FF68A8", "#FF8898"), // Emerald Sprite
            CreaturePalette("#9C4DCC", "#F8E8FF", "#60FFDF", "#FFCF40", "#FF68B8"), // Amethyst Imp
            CreaturePalette("#E04868", "#FFEEF2", "#70F0FF", "#40E0D0", "#FF9870")  // Ruby Starling
        )

        fun createRandom(seed: Int = Random.nextInt(1000, 9999), customName: String? = null): CreatureDna {
            val prefix = PREFIXES.random()
            val species = SPECIES.random()
            val generatedName = customName ?: "$prefix-$species #$seed"
            val pal = PALETTES.random()

            return CreatureDna(
                id = "creature_$seed",
                seed = seed,
                name = generatedName,
                ears = Random.nextInt(0, 8),
                crown = Random.nextInt(0, 5),
                wings = Random.nextInt(0, 3),
                tail = Random.nextInt(0, 4),
                belly = Random.nextInt(0, 6),
                eyes = Random.nextInt(0, 5),
                mouth = Random.nextInt(0, 4),
                particle = Random.nextInt(0, 3),
                bodyHex = pal.body,
                skinHex = pal.skin,
                cyanHex = pal.cyan,
                irisHex = pal.iris,
                blushHex = pal.blush,
                bodyRx = 13.8f + Random.nextFloat() * 2.0f,
                bodyRy = 17.5f + Random.nextFloat() * 2.5f,
                headWiden = 0.08f + Random.nextFloat() * 0.16f,
                neckPinch = 0.08f + Random.nextFloat() * 0.16f,
                bellyBulge = 0.06f + Random.nextFloat() * 0.14f,
                profile = CharacterProfile.generateRandom()
            )
        }

        fun defaultCreature(): CreatureDna {
            return CreatureDna(
                id = "creature_4092",
                seed = 4092,
                name = "Sternen-Hüter #4092",
                ears = 1,
                crown = 1,
                wings = 0,
                tail = 1,
                belly = 2,
                eyes = 0,
                mouth = 1,
                particle = 0,
                bodyHex = "#F26B38",
                skinHex = "#FFF0DC",
                cyanHex = "#6CF8EC",
                irisHex = "#1CD4A8",
                blushHex = "#FF7898"
            )
        }
    }
}
