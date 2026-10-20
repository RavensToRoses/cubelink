package com.cubelink

/**
 * Nintendo Pro Controller emulation (Bluetooth). Written from public protocol notes, NOT verified on a real console.
 * Report 0x30 = full input report; 0x21 = subcommand reply to output report 0x01.
 */
class ProProtocol(private val s: PadState) {
    companion object {
        val DESCRIPTOR = ("05 01 15 00 09 04 A1 01 85 30 05 01 05 09 19 01 29 0A 15 00 25 01 75 01 95 0A 55 00 65 00 81 02 " +
            "05 09 19 0B 29 0E 15 00 25 01 75 01 95 04 81 02 75 01 95 02 81 03 0B 01 00 01 00 A1 00 0B 30 00 01 00 " +
            "0B 31 00 01 00 0B 32 00 01 00 0B 35 00 01 00 15 00 27 FF FF 00 00 75 10 95 04 81 02 C0 " +
            "0B 39 00 01 00 15 00 25 07 35 00 46 3B 01 65 14 75 04 95 01 81 02 05 09 19 0F 29 12 15 00 25 01 75 01 95 04 81 02 " +
            "75 08 95 34 81 03 06 00 FF 85 21 09 01 75 08 95 3F 81 03 85 81 09 02 75 08 95 3F 81 03 " +
            "85 01 09 03 75 08 95 3F 91 83 85 10 09 04 75 08 95 3F 91 83 85 80 09 05 75 08 95 3F 91 83 " +
            "85 82 09 06 75 08 95 3F 91 83 C0").split(" ").map { it.toInt(16).toByte() }.toByteArray()
        private val MAC = byteArrayOf(0x7C, 0xBB.toByte(), 0x8A.toByte(), 0x12, 0x34, 0x56)
        // HID button index -> (payload byte, mask). Right=2, Shared=3, Left=4. Positional: south=B, east=A, west=Y, north=X.
        private val BTN = arrayOf(2 to 0x04, 2 to 0x08, 2 to 0x01, 2 to 0x02, 4 to 0x40, 2 to 0x40, 4 to 0x80, 2 to 0x80,
            3 to 0x01, 3 to 0x02, 3 to 0x08, 3 to 0x04, 3 to 0x10, 3 to 0x20)
        private fun pack(x: Int, y: Int) = byteArrayOf(x.toByte(), (((y and 0xF) shl 4) or (x shr 8)).toByte(), (y shr 4).toByte())
        private fun stick(v: Int, invert: Boolean) = (2048 + (if (invert) -1 else 1) * (v - 128) * 1500 / 127).coerceIn(0, 4095)
    }

    private var timer = 0
    private val spi = ByteArray(0x10000) { 0xFF.toByte() }.also { m ->
        val c = pack(2048, 2048); val r = pack(1500, 1500)
        (r + c + r).copyInto(m, 0x603D)                       // left: above, center, below
        (c + r + r).copyInto(m, 0x6046)                       // right: center, below, above
        val params = "0F 30 61 AE 90 D9 D4 14 54 41 15 54 C7 79 9C 33 36 63".split(" ").map { it.toInt(16).toByte() }.toByteArray()
        params.copyInto(m, 0x6086); params.copyInto(m, 0x6098)
    }

    private fun base(): ByteArray {
        val p = ByteArray(48); p[0] = (timer++ and 0xFF).toByte(); p[1] = 0x90.toByte()
        fun on(b: Int, m: Int) { p[b] = (p[b].toInt() or m).toByte() }
        BTN.forEachIndexed { i, (b, m) -> if (((s.buttons shr i) and 1) == 1) on(b, m) }
        if (s.up) on(4, 0x02); if (s.down) on(4, 0x01); if (s.right) on(4, 0x04); if (s.left) on(4, 0x08)
        pack(stick(s.lx, false), stick(s.ly, true)).copyInto(p, 5)
        pack(stick(s.rx, false), stick(s.ry, true)).copyInto(p, 8)
        p[11] = 0x80.toByte(); return p
    }

    fun inputReport(): ByteArray = base()

    /** data = output report 0x01 payload: [0]=counter, [1..8]=rumble, [9]=subcommand, [10..]=args. */
    fun reply(data: ByteArray): ByteArray? {
        if (data.size < 10) return null
        val sub = data[9].toInt() and 0xFF
        var ack = 0x80; var rd = ByteArray(0)
        when (sub) {
            0x02 -> { ack = 0x82; rd = byteArrayOf(0x03, 0x48, 0x03, 0x02) + MAC + byteArrayOf(0x01, 0x01) }
            0x04 -> { ack = 0x83; rd = ByteArray(14) }
            0x10 -> if (data.size >= 15) {
                val a = (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8) or ((data[12].toInt() and 0xFF) shl 16)
                val n = data[14].toInt() and 0xFF
                if (a + n <= spi.size) { ack = 0x90; rd = data.copyOfRange(10, 15) + spi.copyOfRange(a, a + n) }
            }
        }
        val p = base(); p[12] = ack.toByte(); p[13] = sub.toByte()
        rd.copyInto(p, 14, 0, minOf(rd.size, 34)); return p
    }
}
