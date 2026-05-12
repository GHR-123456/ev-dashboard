package com.evdash.app.data

enum class PacketDirection { TX, RX }

data class RawPacket(
    val timestampMs: Long,
    val direction: PacketDirection,
    val data: ByteArray
) {
    fun hexString(): String = data.joinToString(" ") { "%02X".format(it) }

    fun asciiString(): String = data.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawPacket
        return timestampMs == other.timestampMs &&
                direction == other.direction &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
