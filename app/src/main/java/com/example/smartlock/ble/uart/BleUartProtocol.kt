package com.example.smartlock.ble.uart

/**
 * BLE UART 帧协议：
 *   [0xAA][0x55][CMD][LEN][PAYLOAD...][CRC_H][CRC_L]
 * CRC16-MODBUS，多项式 0x1021，初始值 0xFFFF。
 */
object BleUartProtocol {

    private const val HEADER_1: Byte = 0xAA.toByte()
    private const val HEADER_2: Byte = 0x55.toByte()
    private const val HEADER_SIZE = 2
    private const val CRC_SIZE = 2
    private const val MIN_FRAME_SIZE = HEADER_SIZE + 1 + 1 + CRC_SIZE

    enum class Cmd(val code: Byte) {
        UNLOCK(0x01),
        LOCK(0x02),
        SET_PASSWORD(0x03),
        ACK(0x10),
        NACK(0x11);

        companion object {
            fun from(code: Byte): Cmd? = entries.firstOrNull { it.code == code }
        }
    }

    /** 构造一个完整帧 */
    fun buildFrame(cmd: Cmd, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= 0xFF) { "payload too large" }
        val len = payload.size
        val frame = ByteArray(HEADER_SIZE + 2 + len + CRC_SIZE)
        frame[0] = HEADER_1
        frame[1] = HEADER_2
        frame[2] = cmd.code
        frame[3] = len.toByte()
        System.arraycopy(payload, 0, frame, 4, len)
        val crc = crc16(frame, 0, 4 + len)
        frame[4 + len] = ((crc ushr 8) and 0xFF).toByte()
        frame[5 + len] = (crc and 0xFF).toByte()
        return frame
    }

    /** 构造带 6 位数字密码的指令 */
    fun buildPasswordCommand(cmd: Cmd, password: String): ByteArray =
        buildFrame(cmd, password.toByteArray(Charsets.US_ASCII))

    data class Parsed(val cmd: Cmd, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Parsed) return false
            return cmd == other.cmd && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int =
            31 * cmd.hashCode() + payload.contentHashCode()
    }

    /**
     * 从缓冲区中解析一个完整帧。
     * - 返回 null 表示数据不完整或 CRC 校验失败或未找到帧头。
     * - consumed 表示本次消费的字节数（用于流式解析）。
     */
    data class ParseResult(val frame: Parsed?, val consumed: Int)

    fun tryParse(buffer: ByteArray): ParseResult {
        if (buffer.size < MIN_FRAME_SIZE) return ParseResult(null, 0)

        // 找到帧头
        var start = -1
        for (i in 0..buffer.size - 2) {
            if (buffer[i] == HEADER_1 && buffer[i + 1] == HEADER_2) {
                start = i
                break
            }
        }
        if (start < 0) return ParseResult(null, maxOf(0, buffer.size - 1)) // 丢弃无头垃圾
        if (start > 0) return ParseResult(null, start) // 丢弃帧头前的垃圾，让上层重试

        if (buffer.size < 4) return ParseResult(null, 0)

        val len = buffer[3].toInt() and 0xFF
        val total = HEADER_SIZE + 2 + len + CRC_SIZE
        if (buffer.size < total) return ParseResult(null, 0) // 帧不完整

        val cmd = Cmd.from(buffer[2])
        if (cmd == null) return ParseResult(null, total) // 未知指令，整帧丢弃

        val payload = buffer.copyOfRange(4, 4 + len)
        val crcRecv = ((buffer[4 + len].toInt() and 0xFF) shl 8) or
                (buffer[5 + len].toInt() and 0xFF)
        val crcCalc = crc16(buffer, 0, 4 + len)
        if (crcRecv != crcCalc) return ParseResult(null, total) // CRC 错，整帧丢弃

        return ParseResult(Parsed(cmd, payload), total)
    }

    /** CRC16-MODBUS */
    private fun crc16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}