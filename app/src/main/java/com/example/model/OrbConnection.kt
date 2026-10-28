package com.example.model

enum class InternetConnectionType {
    HOTSPOT,
    MANUAL_WIFI
}

enum class AgentType {
    EXISTING_AGENT,  // e.g. Hermes, OpenClaw (address & bearer token)
    SELF_CONFIGURED   // User picks name, speech style & personality generated in background, LLM API key
}

enum class SoundProfile(val label: String) {
    STANDARD("Standard (Klar)"),
    SPEECH_BOOST("Sprach-Fokus"),
    SUBTLE("Dezent (Sanft)"),
    SILENT("Stummgeschaltet")
}

enum class ScreenTimeout(val label: String, val seconds: Int) {
    SEC_30("30 Sekunden", 30),
    MIN_1("1 Minute", 60),
    MIN_2("2 Minuten", 120),
    MIN_5("5 Minuten", 300),
    ALWAYS_ON("Immer an", 0)
}

data class OrbDevice(
    val id: String = "orb-si-m-4092",
    val name: String = "SI-M Orb #4092",
    val macAddress: String = "E4:5F:01:8A:2B:9C",
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val batteryPercent: Int = 88,
    val isCharging: Boolean = false,
    val firmwareVersion: String = "v2.4.1-Q12",
    val temperatureC: Float = 34.4f,
    val latencyMs: Int = 18,
    val signalStrengthDbm: Int = -52
)

data class InternetConfig(
    val connectionType: InternetConnectionType = InternetConnectionType.HOTSPOT,
    val ssid: String = "SIM-Hotspot-Net",
    val isHotspotActive: Boolean = true,
    val wifiSecurity: String = "WPA3 Personal",
    val ipAddress: String = "192.168.4.15",
    val isConnected: Boolean = true
)

data class AgentConfig(
    val agentType: AgentType = AgentType.SELF_CONFIGURED,
    val agentName: String = "Nova",
    val serverAddress: String = "https://agent.hermes-ai.internal:8443",
    val bearerToken: String = "",
    val llmProvider: String = "Gemini 2.5 Flash (Empfohlen)",
    val apiKey: String = "",
    val isConnected: Boolean = true
)

data class OrbSettings(
    val volume: Float = 0.75f,
    val soundProfile: SoundProfile = SoundProfile.STANDARD,
    val brightness: Float = 0.85f,
    val adaptiveBrightness: Boolean = true,
    val screenTimeout: ScreenTimeout = ScreenTimeout.MIN_2,
    val wifiEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val pixelGridHardware: Boolean = true,
    val cameraEyeActive: Boolean = false
)
