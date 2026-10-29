package com.example.model

import com.example.orb.AvatarDnaWire
import java.util.UUID

/** Visual-only local entry: identifier/name never become device character IDs. */
data class AvatarFavorite(val id: String, val name: String, val dna: String)

object AvatarDraft {
    val traitNames = listOf("Ohren", "Krone", "Flügel", "Schwanz", "Bauch", "Augen", "Mund", "Partikel")
    val traitMax = listOf(7, 4, 2, 3, 5, 4, 3, 2)
    val colorNames = listOf("Körper", "Haut", "Akzent", "Iris", "Wangen")
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun generate(seed: Int): String = hex(checkNotNull(AvatarNative.generate(seed)))
    fun editTrait(hex: String, index: Int, value: Int): String {
        require(index in 0..7 && value in 0..traitMax[index])
        val bytes = AvatarDnaWire.decode(hex)
        bytes[8 + index] = value.toByte()
        return hex(checkNotNull(AvatarNative.seal(bytes)))
    }
    fun editColor(hex: String, index: Int, color: String): String {
        require(index in 0..4 && color.matches(Regex("#[0-9a-fA-F]{6}")))
        val bytes = AvatarDnaWire.decode(hex)
        for (i in 0..2) bytes[16 + index * 3 + i] = color.substring(1 + 2*i, 3 + 2*i).toInt(16).toByte()
        return hex(checkNotNull(AvatarNative.seal(bytes)))
    }
    fun color(hex: String, index: Int): String {
        require(index in 0..4)
        val bytes = AvatarDnaWire.decode(hex)
        return "#" + hex(bytes.copyOfRange(16 + index*3, 19 + index*3)).uppercase()
    }
    fun migrate(legacy: CreatureDna): String {
        val bytes = checkNotNull(AvatarNative.generate(legacy.seed.takeIf { it != 0 } ?: 1))
        val traits = listOf(legacy.ears, legacy.crown, legacy.wings, legacy.tail, legacy.belly, legacy.eyes, legacy.mouth, legacy.particle)
        traits.forEachIndexed { i, v -> bytes[8+i] = v.coerceIn(0, traitMax[i]).toByte() }
        listOf(legacy.bodyHex, legacy.skinHex, legacy.cyanHex, legacy.irisHex, legacy.blushHex).forEachIndexed { i, color ->
            if (color.matches(Regex("#[0-9a-fA-F]{6}"))) for (j in 0..2) bytes[16+i*3+j] = color.substring(1+2*j,3+2*j).toInt(16).toByte()
        }
        val proportions = listOf(legacy.bodyRx to (13.8f to 2f), legacy.bodyRy to (17.5f to 2.5f),
            legacy.headWiden to (0.08f to .16f), legacy.neckPinch to (0.08f to .16f), legacy.bellyBulge to (.06f to .14f))
        proportions.forEachIndexed { i, (v, range) -> bytes[31+i] = (((v-range.first)/range.second*255).toInt().coerceIn(0,255)).toByte() }
        return hex(checkNotNull(AvatarNative.seal(bytes)))
    }
    fun favorite(name: String, dna: String) = AvatarFavorite(UUID.randomUUID().toString(), name.trim().take(32), dna)
}
