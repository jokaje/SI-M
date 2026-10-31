package com.example.orb

/** ESP_ERR_NOT_SUPPORTED is the only firmware response proving this optional op absent. */
internal fun languageFailureUnsupported(code: Int): Boolean = code == 0x106
