package com.cubelink

import android.view.KeyEvent
import org.json.JSONObject

/** Maps Android key codes to HID button indexes 0..15. */
class Profile(var deadzone: Float, val buttons: MutableMap<Int, Int>) {
    fun toJson(): String = JSONObject()
        .put("name", "Default").put("deadzone", deadzone.toDouble())
        .put("buttons", JSONObject(buttons.mapKeys { it.key.toString() })).toString(2)

    companion object {
        val NAMES = listOf("A","B","X","Y","L1","R1","L2","R2","Select","Start","L3","R3","Home","13","14","15")
        fun default() = Profile(0.08f, mutableMapOf(
            KeyEvent.KEYCODE_BUTTON_A to 0, KeyEvent.KEYCODE_BUTTON_B to 1,
            KeyEvent.KEYCODE_BUTTON_X to 2, KeyEvent.KEYCODE_BUTTON_Y to 3,
            KeyEvent.KEYCODE_BUTTON_L1 to 4, KeyEvent.KEYCODE_BUTTON_R1 to 5,
            KeyEvent.KEYCODE_BUTTON_L2 to 6, KeyEvent.KEYCODE_BUTTON_R2 to 7,
            KeyEvent.KEYCODE_BUTTON_SELECT to 8, KeyEvent.KEYCODE_BUTTON_START to 9,
            KeyEvent.KEYCODE_BUTTON_THUMBL to 10, KeyEvent.KEYCODE_BUTTON_THUMBR to 11,
            KeyEvent.KEYCODE_BUTTON_MODE to 12))
        fun fromJson(s: String): Profile {
            val o = JSONObject(s); val b = o.getJSONObject("buttons")
            return Profile(o.optDouble("deadzone", 0.08).toFloat(),
                b.keys().asSequence().associate { it.toInt() to b.getInt(it) }.toMutableMap())
        }
    }
}
