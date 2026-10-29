package com.example.orb

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** No backend, account or navigation dependency. Credentials never appear in diagnostic strings. */
sealed interface AgentCommand {
    data object Get : AgentCommand
    data object Caps : AgentCommand
    data object Reset : AgentCommand
    class Set(val url: String, val token: String) : AgentCommand {
        override fun toString() = "AgentSet(redacted)"
    }
}
data class AgentProfile(val source: String, val configured: Boolean, val adapter: String, val url: String)
data class AgentReply(val id: Int, val state: String, val code: Int? = null,
    val profile: AgentProfile? = null, val resetUnconfigures: Boolean? = null)

object AgentProtocol {
    private const val route = "/v1/turn-stream"
    /** Old firmware's built-in profile cannot be removed with neutral semantics. */
    fun canRemove(profile: AgentProfile?): Boolean = profile != null && profile.source != "builtin"
    fun validUrl(url: String): Boolean {
        if (url.length !in 1..192 || !url.startsWith("https://") || !url.endsWith(route) ||
            url.any { it.code !in 0x21..0x7e || it in "\\\"'@?#%" }) return false
        val rest = url.removePrefix("https://")
        val authority = rest.substringBefore('/')
        val hostname = authority.substringBefore(':')
        if (hostname.isEmpty() || hostname.length > 253 || !hostname.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() && it.code < 128 || it == '-' }
        }) return false
        if (authority.count { it == ':' } > 1) return false
        if (':' in authority) {
            val port = authority.substringAfter(':')
            if (port.isEmpty() || !port.all(Char::isDigit) || port.toIntOrNull() !in 1..65535) return false
        }
        val path = rest.removePrefix(authority).removePrefix("/")
        val segments = path.split('/')
        return segments.all { it.isNotEmpty() && it != "." && it != ".." &&
            it.all { c -> c.isLetterOrDigit() && c.code < 128 || c in "-._~" } }
    }
    fun validToken(token: String): Boolean = token.length in 32..128 &&
        token.matches(Regex("[A-Za-z0-9._~+/-]+={0,}"))

    fun encode(id: Int, command: AgentCommand): ByteArray {
        require(id > 0) { "Ungültige Anfrage-ID." }
        val json = buildJsonObject {
            put("v", 1); put("id", id)
            when (command) {
                AgentCommand.Get -> put("op", "agent_get")
                AgentCommand.Caps -> put("op", "agent_caps")
                AgentCommand.Reset -> put("op", "agent_reset")
                is AgentCommand.Set -> {
                    require(validUrl(command.url) && validToken(command.token)) { "Ungültige Agent-Konfiguration." }
                    put("op", "agent_set"); put("adapter", "kim-stream-v1")
                    put("url", command.url); put("token", command.token)
                }
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(json.size <= 512) { "Agent-Konfiguration zu lang." }
        return json
    }
    fun decode(bytes: ByteArray): AgentReply {
        require(bytes.isNotEmpty() && bytes.size <= 511) { "Ungültiger Orb-Status." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        // The firmware's generic parser disallows escaped Unicode; mirror it before parsing.
        require(!text.contains(Regex("\\\\[uU]"))) { "Ungültiger Orb-Status." }
        uniqueKeys(text)
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.keys.containsAll(setOf("v", "id", "state")))
        fun number(e: JsonElement): Int = e.jsonPrimitive.let {
            require(!it.isString && it.content.matches(Regex("[0-9]+"))); it.int
        }
        fun string(e: JsonElement): String = e.jsonPrimitive.let { require(it.isString); it.content }
        require(number(root.getValue("v")) == 1)
        val id = number(root.getValue("id")); require(id > 0)
        val state = string(root.getValue("state")); require(state in setOf("pending", "done", "error"))
        if (state == "pending") require(root.keys == setOf("v", "id", "state"))
        if (state == "error") require(root.keys == setOf("v", "id", "state", "code"))
        if (state == "done") require(root.keys == setOf("v", "id", "state") || root.keys == setOf("v", "id", "state", "agent") || root.keys == setOf("v", "id", "state", "agent_caps"))
        val capability = root["agent_caps"]?.jsonObject?.let { caps ->
            require(caps.keys == setOf("v", "reset_unconfigures") && number(caps.getValue("v")) == 1)
            val value = caps.getValue("reset_unconfigures").jsonPrimitive
            require(!value.isString && value.content == "true")
            true
        }
        val profile = root["agent"]?.jsonObject?.let { agent ->
            require(agent.keys == setOf("v", "source", "configured", "adapter", "url"))
            require(number(agent.getValue("v")) == 1)
            val source = string(agent.getValue("source")); require(source in setOf("none", "builtin", "custom", "invalid"))
            val configured = agent.getValue("configured").jsonPrimitive.let { require(!it.isString && it.content in setOf("true", "false")); it.boolean }
            val adapter = string(agent.getValue("adapter")); require(adapter == "kim-stream-v1")
            val url = string(agent.getValue("url"))
            require(when (source) {
                "custom" -> configured && validUrl(url)
                "none", "invalid" -> !configured && url.isEmpty()
                else -> url.isEmpty()
            })
            AgentProfile(source, configured, adapter, url)
        }
        return AgentReply(id, state, root["code"]?.let(::number), profile, capability)
    }
    private fun uniqueKeys(text: String) {
        val stack = java.util.ArrayDeque<MutableSet<String>>()
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> stack.addLast(mutableSetOf())
                '}' -> { require(stack.isNotEmpty()); stack.removeLast() }
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    require(i < text.length)
                    var next = i + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val key = Json.parseToJsonElement(text.substring(start, i + 1)).jsonPrimitive.content
                        require(stack.isNotEmpty() && stack.last.add(key)) { "Doppelter Orb-Statusschlüssel." }
                    }
                }
            }
            i++
        }
        require(stack.isEmpty())
    }
}
