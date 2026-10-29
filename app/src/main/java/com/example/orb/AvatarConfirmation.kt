package com.example.orb

/** Atomic publication gate after protected mutation, two independent device reads and epoch check. */
object AvatarConfirmation {
    fun accept(expectedDna: String?, avatar: SetupReply?, character: SetupReply?, currentWindow: Boolean,
        priorSelection: String? = null): Boolean {
        if (!currentWindow || avatar?.state != "done" || character?.state != "done") return false
        val expectedSelection = if (expectedDna != null) "custom" else if (priorSelection == "custom") "flame" else priorSelection
        return avatar.avatarPresent == (expectedDna != null) && avatar.avatarDna == (expectedDna ?: "") &&
            character.selected == expectedSelection
    }
}
