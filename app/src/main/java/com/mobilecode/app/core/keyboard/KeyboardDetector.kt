package com.mobilecode.app.core.keyboard

import android.content.res.Configuration

object KeyboardDetector {
    /**
     * Returns true if a physical hardware keyboard is currently attached and active.
     */
    fun hasPhysicalKeyboard(configuration: Configuration): Boolean {
        return configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
                configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    }
}

