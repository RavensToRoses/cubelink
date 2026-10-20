package com.cubelink

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Current gamepad state, serialised as HID input report 1 (9 bytes). */
class PadState {
    var buttons = 0                       // bit n = HID button n
    var up = false; var down = false; var left = false; var right = false
    var lx = 128; var ly = 128; var rx = 128; var ry = 128; var lt = 0; var rt = 0

    private fun hat(): Int = when {
        up && right -> 2; up && left -> 8; down && right -> 4; down && left -> 6
        up -> 1; right -> 3; down -> 5; left -> 7; else -> 0
    }
    fun report() = byteArrayOf(
        (buttons and 0xFF).toByte(), ((buttons shr 8) and 0xFF).toByte(), hat().toByte(),
        lx.toByte(), ly.toByte(), rx.toByte(), ry.toByte(), lt.toByte(), rt.toByte()
    )
}

@SuppressLint("MissingPermission")
class HidController(private val ctx: Context, private val switchMode: Boolean, private val onStatus: (String) -> Unit) {
    companion object {
        // Generic gamepad: 16 buttons, hat (0=none, 1..8 = N..NW), X/Y/Z/Rz sticks, Rx/Ry triggers.
        val DESCRIPTOR = intArrayOf(
            0x05,0x01, 0x09,0x05, 0xA1,0x01, 0x85,0x01,
            0x05,0x09, 0x19,0x01, 0x29,0x10, 0x15,0x00, 0x25,0x01, 0x75,0x01, 0x95,0x10, 0x81,0x02,
            0x05,0x01, 0x09,0x39, 0x15,0x01, 0x25,0x08, 0x75,0x04, 0x95,0x01, 0x81,0x42,
            0x75,0x04, 0x95,0x01, 0x81,0x03,
            0x09,0x30, 0x09,0x31, 0x09,0x32, 0x09,0x35, 0x15,0x00, 0x26,0xFF,0x00, 0x75,0x08, 0x95,0x04, 0x81,0x02,
            0x09,0x33, 0x09,0x34, 0x15,0x00, 0x26,0xFF,0x00, 0x75,0x08, 0x95,0x02, 0x81,0x02,
            0xC0
        ).map { it.toByte() }.toByteArray()
    }

    val state = PadState()
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val pro = if (switchMode) ProProtocol(state) else null
    private var ticker: ScheduledExecutorService? = null
    private fun startTicker() {
        ticker?.shutdownNow()
        ticker = Executors.newSingleThreadScheduledExecutor().also { t -> t.scheduleAtFixedRate({
            host?.let { h -> pro?.let { hid?.sendReport(h, 0x30, it.inputReport()) } } }, 0, 15, TimeUnit.MILLISECONDS) }
    }
    val adapter: BluetoothAdapter? get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) =
            onStatus(if (registered) "Ready: pair from the host's Bluetooth menu" else "Not registered")
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (reportId.toInt() == 1) pro?.reply(data)?.let { hid?.sendReport(device, 0x21, it) }
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, st: Int) {
            when (st) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; if (switchMode) startTicker(); onStatus("Connected to ${device.name}") }
                BluetoothProfile.STATE_DISCONNECTED -> if (device == host) { host = null; ticker?.shutdownNow(); onStatus("Disconnected") }
            }
        }
    }

    fun start() {
        val a = adapter ?: return onStatus("No Bluetooth adapter")
        a.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice; hid = h
                val sdp = if (switchMode) BluetoothHidDeviceAppSdpSettings("Pro Controller", "Gamepad", "Nintendo",
                    BluetoothHidDevice.SUBCLASS2_GAMEPAD, ProProtocol.DESCRIPTOR)
                else BluetoothHidDeviceAppSdpSettings("CubeLink", "RG Cube gamepad", "CubeLink",
                    BluetoothHidDevice.SUBCLASS2_GAMEPAD, DESCRIPTOR)
                try { if (!h.registerApp(sdp, null, null, executor, callback)) onStatus("registerApp failed") } catch (e: SecurityException) { onStatus("Bluetooth permission missing") }
            }
            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun connectTo(d: BluetoothDevice) { hid?.connect(d) }
    fun bonded(): List<BluetoothDevice> = try { adapter?.bondedDevices?.toList() ?: emptyList() } catch (e: SecurityException) { emptyList() }
    fun send() { if (!switchMode) host?.let { hid?.sendReport(it, 1, state.report()) } }
    fun stop() { ticker?.shutdownNow(); try { hid?.unregisterApp() } catch (e: SecurityException) { } }
}
