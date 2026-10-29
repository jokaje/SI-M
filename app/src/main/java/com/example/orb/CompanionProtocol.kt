package com.example.orb

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** A status read is evidence only when the GATT link is bound to the persisted bonded owner. */
data class CompanionStatus(val characterId: String, val mode: String, val setupOpen: Boolean,
    val owned: Boolean, val seq: Int, val proof: String)

object CompanionProtocol {
    val SERVICE: UUID = UUID.fromString("4d4b1000-62ad-4ea3-8431-645be89e0001")
    val STATUS: UUID = UUID.fromString("4d4b1003-62ad-4ea3-8431-645be89e0001")
    val ENROLL: UUID = UUID.fromString("4d4b1004-62ad-4ea3-8431-645be89e0001")
    val AUTH: UUID = UUID.fromString("4d4b1005-62ad-4ea3-8431-645be89e0001")
    val CONFIRM_OWNER = "CONFIRM_OWNER_V1".toByteArray(Charsets.US_ASCII)
    private val characters = setOf("flame", "fox_spirit", "crystal_dragon", "celestial_owl", "starlight_axolotl", "custom")
    fun acceptedCharacterId(id: String): Boolean = id in characters
    private val modes = setOf("boot", "idle", "listening", "thinking", "speaking", "error", "off")

    fun decode(bytes: ByteArray): CompanionStatus {
        require(bytes.isNotEmpty() && bytes.size <= 511)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        // kotlinx Json accepts duplicate keys; reject duplicates before accepting security fields.
        val keys = Regex("\"([a-z_]+)\"\\s*:").findAll(text).map { it.groupValues[1] }.toList()
        require(keys.size == keys.toSet().size)
        val obj = Json.parseToJsonElement(text).jsonObject
        require(obj.keys == setOf("v", "kind", "character_id", "mode", "setup_open", "owned", "seq", "proof"))
        fun str(key: String): String = obj.getValue(key).jsonPrimitive.let { require(it.isString); it.content }
        fun bool(key: String): Boolean = obj.getValue(key).jsonPrimitive.let {
            require(!it.isString && it.content in setOf("true", "false")); it.boolean
        }
        val version = obj.getValue("v").jsonPrimitive
        require(!version.isString && version.content == "2" && str("kind") == "companion")
        val sequence = obj.getValue("seq").jsonPrimitive.let {
            require(!it.isString && it.content.matches(Regex("(0|[1-9][0-9]*)"))); it.int
        }
        val proof = str("proof")
        require(if (sequence == 0) proof.isEmpty() && !bool("owned") else proof.matches(Regex("[0-9a-f]{64}")))
        return CompanionStatus(str("character_id").also { require(it in characters) },
            str("mode").also { require(it in modes) }, bool("setup_open"), bool("owned"), sequence, proof)
    }
}
