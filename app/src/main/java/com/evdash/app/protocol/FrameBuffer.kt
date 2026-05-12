package com.evdash.app.protocol

/**
 * 数据帧缓冲器。用于处理 BLE 字节流分包/粘包问题。
 *
 * 典型场景：一帧完整数据可能被拆成多个 BLE 包发送，
 * 也可能多帧数据粘在一个 BLE 包中。此类负责：
 * 1. 累积字节到缓冲区
 * 2. 根据帧头/长度字段提取完整帧
 * 3. 返回提取出的帧列表，并清理已消费的缓冲区数据
 */
class FrameBuffer(
    private val header: ByteArray,
    private val lengthExtractor: ((ByteArray, Int) -> Int)? = null,
    private val minFrameSize: Int = header.size + 1,
    private val maxFrameSize: Int = 256
) {
    private val buffer = java.nio.ByteBuffer.allocate(4096)

    init {
        buffer.limit(0)
    }

    /** 追加新收到的字节，返回所有提取出的完整帧 */
    fun feed(data: ByteArray): List<ByteArray> {
        val pos = buffer.limit()
        buffer.limit(pos + data.size)
        buffer.position(pos)
        buffer.put(data)
        buffer.position(0)

        val frames = mutableListOf<ByteArray>()
        val arr = ByteArray(buffer.remaining())
        buffer.get(arr)

        var offset = 0
        while (offset <= arr.size - minFrameSize) {
            val headerIdx = findHeader(arr, offset)
            if (headerIdx == -1 || headerIdx > arr.size - minFrameSize) break

            val frameLen = if (lengthExtractor != null) {
                val len = lengthExtractor.invoke(arr, headerIdx)
                if (len < minFrameSize || len > maxFrameSize) {
                    offset = headerIdx + 1
                    continue
                }
                len
            } else {
                val nextHeader = findHeader(arr, headerIdx + 1)
                if (nextHeader == -1) break
                nextHeader - headerIdx
            }

            if (headerIdx + frameLen > arr.size) break

            frames.add(arr.copyOfRange(headerIdx, headerIdx + frameLen))
            offset = headerIdx + frameLen
        }

        val remaining = if (offset < arr.size) arr.copyOfRange(offset, arr.size) else byteArrayOf()
        buffer.clear()
        buffer.limit(remaining.size)
        buffer.put(remaining)
        buffer.position(0)

        return frames
    }

    fun clear() {
        buffer.clear()
        buffer.limit(0)
    }

    private fun findHeader(arr: ByteArray, from: Int): Int {
        for (i in from..arr.size - header.size) {
            if (arr.copyOfRange(i, i + header.size).contentEquals(header)) return i
        }
        return -1
    }
}

/** CRC8 计算（常用多项式 0x31） */
fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size): Byte {
    var crc = 0xFF
    for (i in offset until offset + length) {
        crc = crc xor (data[i].toInt() and 0xFF)
        repeat(8) {
            crc = if ((crc and 0x80) != 0) (crc shl 1) xor 0x31 else crc shl 1
            crc = crc and 0xFF
        }
    }
    return crc.toByte()
}

/** CRC16-CCITT-FALSE */
fun crc16Ccitt(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
    var crc = 0xFFFF
    for (i in offset until offset + length) {
        crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
            crc = crc and 0xFFFF
        }
    }
    return crc
}

/** 求和校验 */
fun checksumSum(data: ByteArray, offset: Int = 0, length: Int = data.size): Byte {
    var sum = 0
    for (i in offset until offset + length) sum += data[i].toInt() and 0xFF
    return sum.toByte()
}

/** 求和校验（取反） */
fun checksumSumInv(data: ByteArray, offset: Int = 0, length: Int = data.size): Byte {
    return (checksumSum(data, offset, length).toInt() and 0xFF).inv().toByte()
}
