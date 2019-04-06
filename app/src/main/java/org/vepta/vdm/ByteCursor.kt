package org.vepta.vdm

import java.nio.ByteBuffer

/**
 * Simple cursor for use on byte arrays
 * @author nulldev
 */
class ByteCursor(val content: ByteArray) {
    var index = -1
        private set
    private var mark = -1
    
    fun mark() {
        mark = index
    }
    
    fun jumpToMark() {
        index = mark
    }
    
    fun jumpToIndex(index: Int) {
        this.index = index
    }
    
    fun next(): Byte {
        return content[++index]
    }
    
    fun next(count: Int): ByteArray {
        val res = content.sliceArray(index + 1 .. index + count)
        skip(count)
        return res
    }
    
    //Used to perform conversions
    private fun byteBuffer(count: Int): ByteBuffer {
        return ByteBuffer.wrap(next(count))
    }
    
    //Epic hack to get an unsigned short properly...
    fun fakeNextShortInt(): Int = ByteBuffer
        .wrap(arrayOf(0x00, 0x00, *next(2).toTypedArray()).toByteArray())
        .getInt(0)
    
    //    fun nextShort(): Short = byteBuffer(2).getShort(0)
    fun nextInt(): Int = byteBuffer(4).getInt(0)
    fun nextLong(): Long = byteBuffer(8).getLong(0)
    fun nextFloat(): Float = byteBuffer(4).getFloat(0)
    fun nextDouble(): Double = byteBuffer(8).getDouble(0)
    
    fun skip(count: Int) {
        index += count
    }
    
    fun expect(vararg bytes: Byte) {
        if(bytes.size > remaining())
            throw IllegalStateException("Unexpected end of content!")
    
        for(i in 0 .. bytes.lastIndex) {
            val expected = bytes[i]
            val actual = content[index + i + 1]
            
            if(expected != actual)
                throw IllegalStateException("Unexpected content (expected: $expected, actual: $actual)!")
        }
        
        index += bytes.size
    }
    
    fun checkEqual(vararg bytes: Byte): Boolean {
        if(bytes.size > remaining())
            return false
            
        for(i in 0 .. bytes.lastIndex) {
            val expected = bytes[i]
            val actual = content[index + i + 1]
        
            if(expected != actual)
                return false
        }
        
        return true
    }
    
    fun atEnd() = index >= content.size - 1
    
    fun remaining() = content.size - index - 1
}