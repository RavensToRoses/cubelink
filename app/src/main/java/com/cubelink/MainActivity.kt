package com.cubelink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("Starting...")
    private var remapTarget by mutableStateOf<Int?>(null)
    private lateinit var hid: HidController
    private lateinit var profile: Profile
    private var mapVersion by mutableStateOf(0)
    private val file get() = File(filesDir, "profile.json")

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) { hid.start(); mapVersion++ } else status = "Bluetooth permission is needed" }
    private var lastCrash: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashFile = File(filesDir, "crash.txt")
        lastCrash = runCatching { crashFile.readText() }.getOrNull(); crashFile.delete()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            runCatching { crashFile.writeText(e.stackTraceToString()) }; android.os.Process.killProcess(android.os.Process.myPid()) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        profile = runCatching { Profile.fromJson(file.readText()) }.getOrElse { Profile.default() }
        hid = HidController(this, getSharedPreferences("c", 0).getBoolean("switch", false)) { status = it }
        if (Build.VERSION.SDK_INT >= 31)
            perms.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
        else hid.start()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    @Composable fun Screen() {
        var devices by remember { mutableStateOf(listOf<String>()) }
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(status, style = MaterialTheme.typography.titleMedium) }
            lastCrash?.let { c -> item { Text("Last crash:\n" + c.take(1500)) } }
            item { val sw = getSharedPreferences("c", 0).getBoolean("switch", false)
                Button(onClick = { getSharedPreferences("c", 0).edit().putBoolean("switch", !sw).apply(); hid.stop(); recreate() }) {
                    Text(if (sw) "Mode: Switch Pro Controller (tap for generic)" else "Mode: Generic gamepad (tap for Switch)") } }
            item { Button(onClick = {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
            }) { Text("Make discoverable (2 min)") } }
            item { Text("Paired hosts (tap to connect):") }
            items(mapVersion.let { hid.bonded() }) { d -> Text(d.name ?: d.address, Modifier.clickable { hid.connectTo(d) }.padding(8.dp)) }
            item { Text("Remap: tap a button, then press the physical key", style = MaterialTheme.typography.titleSmall) }
            items(Profile.NAMES.indices.toList()) { i ->
                val key = profile.buttons.entries.firstOrNull { it.value == i }?.key
                val label = if (remapTarget == i) "press a key..." else key?.let { KeyEvent.keyCodeToString(it) } ?: "unassigned"
                mapVersion.let { Text("${Profile.NAMES[i]}  ->  $label", Modifier.clickable { remapTarget = i }.padding(8.dp)) }
            }
            item { Button(onClick = { file.writeText(profile.toJson()) }) { Text("Save profile") } }
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) == 0 || e.keyCode == KeyEvent.KEYCODE_BACK)
            return super.dispatchKeyEvent(e)
        val down = e.action == KeyEvent.ACTION_DOWN
        remapTarget?.let { t ->
            if (down) { profile.buttons.entries.removeAll { it.key == e.keyCode || it.value == t }
                profile.buttons[e.keyCode] = t; remapTarget = null; mapVersion++ }
            return true
        }
        val s = hid.state
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> s.up = down; KeyEvent.KEYCODE_DPAD_DOWN -> s.down = down
            KeyEvent.KEYCODE_DPAD_LEFT -> s.left = down; KeyEvent.KEYCODE_DPAD_RIGHT -> s.right = down
            else -> profile.buttons[e.keyCode]?.let { b ->
                s.buttons = if (down) s.buttons or (1 shl b) else s.buttons and (1 shl b).inv()
                if (e.keyCode == KeyEvent.KEYCODE_BUTTON_L2) s.lt = if (down) 255 else 0
                if (e.keyCode == KeyEvent.KEYCODE_BUTTON_R2) s.rt = if (down) 255 else 0
            } ?: return super.dispatchKeyEvent(e)
        }
        hid.send(); return true
    }

    private fun axis(v: Float) = (((if (abs(v) < profile.deadzone) 0f else v) + 1f) * 127.5f).toInt().coerceIn(0, 255)

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.source and InputDevice.SOURCE_JOYSTICK == 0 || e.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(e)
        val s = hid.state
        s.lx = axis(e.getAxisValue(MotionEvent.AXIS_X)); s.ly = axis(e.getAxisValue(MotionEvent.AXIS_Y))
        s.rx = axis(e.getAxisValue(MotionEvent.AXIS_Z)); s.ry = axis(e.getAxisValue(MotionEvent.AXIS_RZ))
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hx != 0f || hy != 0f || s.up || s.down || s.left || s.right) { s.left = hx < 0; s.right = hx > 0; s.up = hy < 0; s.down = hy > 0 }
        s.lt = (e.getAxisValue(MotionEvent.AXIS_LTRIGGER) * 255).toInt().coerceIn(0, 255).takeIf { it > 0 } ?: s.lt
        s.rt = (e.getAxisValue(MotionEvent.AXIS_RTRIGGER) * 255).toInt().coerceIn(0, 255).takeIf { it > 0 } ?: s.rt
        hid.send(); return true
    }

    override fun onDestroy() { hid.stop(); super.onDestroy() }
}
