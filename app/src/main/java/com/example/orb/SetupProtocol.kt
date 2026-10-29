package com.example.orb

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

sealed interface SetupCommand {
    data object Scan : SetupCommand
    data object Networks : SetupCommand
    data object Settings : SetupCommand
    data object Catalog : SetupCommand
    data object CharacterGet : SetupCommand
    data class CharacterSet(val characterId: String) : SetupCommand
    data object AvatarCaps : SetupCommand
    data object AvatarGet : SetupCommand
    data class AvatarSet(val dna: String) : SetupCommand { override fun toString() = "AvatarSet(redacted DNA)" }
    data object AvatarDelete : SetupCommand
    class Save(val ssid: String, val password: String) : SetupCommand {
        override fun toString() = "Save(redacted credentials)"
    }
    data class Forget(val ssid: String) : SetupCommand
    data class Volume(val value: Int) : SetupCommand
    data class Brightness(val value: Int) : SetupCommand
    data class Sleep(val seconds: Int) : SetupCommand
}

data class AccessPoint(val ssid: String, val rssi: Int, val saved: Boolean, val secure: Boolean)
data class DeviceSettings(val volume: Int, val brightness: Int, val sleepSeconds: Int)
data class CharacterOption(val id: String, val name: String)
data class SetupReply(
    val id: Int, val state: String, val code: Int? = null,
    val accessPoints: List<AccessPoint>? = null, val savedNetworks: List<String>? = null,
    val currentNetwork: String? = null, val wifiConnected: Boolean? = null, val settings: DeviceSettings? = null,
    val characters: List<CharacterOption>? = null, val selected: String? = null,
    val avatarCaps: Boolean? = null, val avatarPresent: Boolean? = null, val avatarDna: String? = null,
    val characterId: String? = null
)

object SetupProtocol {
    val SERVICE: UUID = UUID.fromString("4d4b0000-62ad-4ea3-8431-645be89e0001")
    val COMMAND: UUID = UUID.fromString("4d4b0000-62ad-4ea3-8431-645be89e0002")
    val STATUS: UUID = UUID.fromString("4d4b0000-62ad-4ea3-8431-645be89e0003")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val NAME = "SI-M Orb"
    const val MAX_REQUEST = 512
    const val MAX_STATUS = 511
    // Compatibility includes pixel; only the live catalog supplies selectable options.
    val CHARACTER_IDS = setOf("flame", "fox_spirit", "crystal_dragon", "celestial_owl", "starlight_axolotl", "pixel", "custom")
    private fun characterId(id: String) {
        require(id in CHARACTER_IDS) { "Unbekannte Orb-Figur." }
    }

    private fun ascii(value: String, min: Int, max: Int, label: String) {
        require(value.length in min..max && value.all { it.code in 0x20..0x7e } && !value.contains("\\u")) {
            "$label: $min–$max druckbare ASCII-Zeichen, keine Backslash-u-Folge."
        }
    }
    fun encode(id: Int, command: SetupCommand): ByteArray {
        require(id > 0) { "Ungültige Anfrage-ID." }
        val raw = buildJsonObject {
            put("v", 1); put("id", id)
            when (command) {
                SetupCommand.Scan -> put("op", "scan")
                SetupCommand.Networks -> put("op", "networks")
                SetupCommand.Settings -> put("op", "settings_get")
                SetupCommand.Catalog -> put("op", "catalog")
                SetupCommand.CharacterGet -> put("op", "character_get")
                is SetupCommand.CharacterSet -> { characterId(command.characterId); require(command.characterId != "custom"); put("op", "character_set"); put("character_id", command.characterId) }
                SetupCommand.AvatarCaps -> put("op", "avatar_caps")
                SetupCommand.AvatarGet -> put("op", "avatar_get")
                is SetupCommand.AvatarSet -> { AvatarDnaWire.decode(command.dna); put("op", "avatar_set"); put("dna", command.dna) }
                SetupCommand.AvatarDelete -> put("op", "avatar_delete")
                is SetupCommand.Save -> {
                    ascii(command.ssid, 1, 32, "SSID"); ascii(command.password, 8, 63, "WLAN-Passwort")
                    put("op", "save"); put("ssid", command.ssid); put("password", command.password)
                }
                is SetupCommand.Forget -> { ascii(command.ssid, 1, 32, "SSID"); put("op", "forget"); put("ssid", command.ssid) }
                is SetupCommand.Volume -> { require(command.value in 10..80) { "Lautstärke: 10–80." }; put("op", "settings_set"); put("volume", command.value) }
                is SetupCommand.Brightness -> { require(command.value in 10..100) { "Helligkeit: 10–100." }; put("op", "settings_set"); put("brightness", command.value) }
                is SetupCommand.Sleep -> { require(command.seconds == 0 || command.seconds in 30..1800) { "Schlafzeit: 0 oder 30–1800 Sekunden." }; put("op", "settings_set"); put("sleep_s", command.seconds) }
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(raw.size <= MAX_REQUEST) { "Anfrage überschreitet 512 Bytes." }
        return raw
    }

    /** Full read only: a truncated notification must never be treated as persisted success. */
    fun decode(bytes: ByteArray): SetupReply {
        require(bytes.isNotEmpty() && bytes.size <= MAX_STATUS) { "Orb-Status fehlt oder ist zu groß." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val obj = Json.parseToJsonElement(text).jsonObject
        uniqueKeys(text)
        require(setOf("v", "id", "state").all { it in obj }) { "Unvollständiger Orb-Status." }
        val allowed = setOf("v", "id", "state", "code", "aps", "saved", "current", "connected", "volume", "brightness", "sleep_s", "characters", "selected", "avatar_caps", "avatar", "character_id", "ok")
        require(obj.keys.all { it in allowed }) { "Unbekannter Orb-Status." }
        fun int(key: String): Int = obj.getValue(key).jsonPrimitive.let {
            require(!it.isString && Regex("-?[0-9]+").matches(it.content)) { "Ungültiger Orb-Zahlenwert." }
            it.int
        }
        fun string(value: JsonElement): String = value.jsonPrimitive.let { require(it.isString); it.content }
        fun bool(value: JsonElement): Boolean = value.jsonPrimitive.let { require(!it.isString && it.content in setOf("true", "false")); it.boolean }
        require(int("v") == 1) { "Unbekannte Orb-Protokollversion." }
        val id = int("id"); require(id >= 0)
        val state = string(obj.getValue("state")); require(state in setOf("closed", "pending", "done", "error"))
        require(state == "closed" || id > 0)
        val aps = obj["aps"]?.jsonArray?.map { entry ->
            val ap = entry.jsonObject
            require(ap.keys == setOf("ssid", "rssi", "saved", "secure"))
            val name = string(ap.getValue("ssid")); require(name.toByteArray(Charsets.UTF_8).size <= 32 && name.none { it.code < 32 })
            val rssi = ap.getValue("rssi").jsonPrimitive; require(!rssi.isString)
            AccessPoint(name, rssi.int, bool(ap.getValue("saved")), bool(ap.getValue("secure")))
        }
        require(aps == null || aps.size <= 4)
        val saved = obj["saved"]?.jsonArray?.map { string(it) }
        val settingsKeys = setOf("volume", "brightness", "sleep_s")
        val settings = if (obj.keys.any { it in settingsKeys }) {
            require(settingsKeys.all { it in obj })
            DeviceSettings(int("volume"), int("brightness"), int("sleep_s")).also {
                // Existing NVS values are read as-is; settings_set keeps its narrower safety policy.
                require(it.volume in 0..100 && it.brightness in 10..100 && (it.sleepSeconds == 0 || it.sleepSeconds in 30..1800))
            }
        } else null
        if (state == "error") require("code" in obj)
        val code = if (state == "error") int("code") else null
        if (state == "pending" || state == "closed") require(obj.keys == setOf("v", "id", "state"))
        if (state == "error") require(obj.keys == setOf("v", "id", "state", "code"))
        val characters = obj["characters"]?.jsonArray?.map { entry ->
            val item = entry.jsonObject
            require(item.keys == setOf("id", "name"))
            val key = string(item.getValue("id")); characterId(key); require(key != "custom")
            val name = string(item.getValue("name"))
            require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 32 && name.none { it.isISOControl() })
            CharacterOption(key, name)
        }
        require(characters == null || characters.size in 1..8 && characters.map { it.id }.distinct().size == characters.size)
        val selected = obj["selected"]?.let(::string)?.also(::characterId)
        val caps = obj["avatar_caps"]?.jsonObject?.let { cap ->
            require(cap.keys == setOf("v", "slots", "dna_bytes", "renderer"))
            require(listOf("v", "slots", "dna_bytes", "renderer").map { key ->
                cap.getValue(key).jsonPrimitive.let { require(!it.isString); it.int }
            } == listOf(1, 1, 48, 1))
            true
        }
        val avatar = obj["avatar"]?.jsonObject
        val present = avatar?.let { a ->
            require(a.keys == setOf("v", "present", "dna"))
            val version = a.getValue("v").jsonPrimitive
            require(!version.isString && version.int == 1)
            bool(a.getValue("present"))
        }
        val dna = avatar?.let { string(it.getValue("dna")) }
        if (present == true) AvatarDnaWire.decode(checkNotNull(dna))
        if (present == false) require(dna == "")
        val customId = obj["character_id"]?.let(::string)?.also { require(it == "custom") }
        if ("ok" in obj) require(state == "done" && bool(obj.getValue("ok")) &&
            (caps != null || avatar != null || customId != null || obj.keys == setOf("v", "id", "state", "ok")))
        if (characters != null) require(selected != null && (selected == "custom" || characters.any { it.id == selected }))
        if (state == "done") {
            val payload = obj.keys - setOf("v", "id", "state", "ok")
            require(payload in setOf(
                emptySet(), setOf("aps"), setOf("saved", "current", "connected"),
                setOf("volume", "brightness", "sleep_s"), setOf("characters", "selected"), setOf("selected"),
                setOf("avatar_caps"), setOf("avatar"), setOf("character_id")
            )) { "Unpassende Orb-Antwortfelder." }
        }
        return SetupReply(id, state, code, aps, saved, obj["current"]?.let(::string), obj["connected"]?.let(::bool), settings, characters, selected, caps, present, dna, customId)
    }

    fun validateReply(command: SetupCommand, reply: SetupReply) {
        if (reply.state != "done") return
        val valid = when (command) {
            SetupCommand.Scan -> reply.accessPoints != null
            SetupCommand.Networks -> reply.savedNetworks != null && reply.currentNetwork != null && reply.wifiConnected != null
            SetupCommand.Settings -> reply.settings != null
            SetupCommand.Catalog -> reply.characters != null && reply.selected != null
            SetupCommand.CharacterGet -> reply.characters == null && reply.selected != null
            is SetupCommand.CharacterSet -> reply.characters == null && reply.selected == command.characterId
            SetupCommand.AvatarCaps -> reply.avatarCaps == true
            SetupCommand.AvatarGet -> reply.avatarPresent != null
            is SetupCommand.AvatarSet -> reply.characterId == "custom"
            SetupCommand.AvatarDelete -> reply.avatarCaps == null && reply.avatarPresent == null && reply.selected == null && reply.characterId == null
            else -> reply.accessPoints == null && reply.savedNetworks == null && reply.settings == null && reply.selected == null
        }
        require(valid) { "Orb-Antwort passt nicht zum Befehl." }
    }

    fun selectedInCatalog(selected: String, characters: List<CharacterOption>): String {
        require(selected == "custom" || characters.any { it.id == selected }) { "Gemeldete Figur fehlt im Katalog." }
        return selected
    }

    private fun uniqueKeys(text: String) {
        val objects = java.util.ArrayDeque<MutableSet<String>>()
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> objects.addLast(mutableSetOf())
                '}' -> objects.removeLast()
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') { if (text[i] == '\\') i++; i++ }
                    var next = i + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val key = Json.parseToJsonElement(text.substring(start, i + 1)).jsonPrimitive.content
                        require(objects.last.add(key)) { "Doppelter Orb-Statusschlüssel." }
                    }
                }
            }
            i++
        }
    }
}
