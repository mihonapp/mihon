package mihon.extension.ipc

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object IpcFrameCodec {

    const val MAX_FRAME_BYTES = 16 * 1024 * 1024 // 16 MiB

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun writeMessage(output: OutputStream, message: IpcMessage) {
        val payload = json.encodeToString(message).encodeToByteArray()
        if (payload.size > MAX_FRAME_BYTES) {
            throw IllegalArgumentException("Message size (${payload.size}) exceeds MAX_FRAME_BYTES ($MAX_FRAME_BYTES)")
        }
        val dos = if (output is DataOutputStream) output else DataOutputStream(output)
        dos.writeInt(payload.size)
        dos.write(payload)
        dos.flush()
    }

    fun readMessage(input: InputStream): IpcMessage? {
        val dis = if (input is DataInputStream) input else DataInputStream(input)
        val length = try {
            dis.readInt()
        } catch (_: EOFException) {
            return null
        }
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw IllegalArgumentException("Invalid frame length: $length (max: $MAX_FRAME_BYTES)")
        }
        val buffer = ByteArray(length)
        dis.readFully(buffer)
        val text = buffer.decodeToString()
        return json.decodeFromString<IpcMessage>(text)
    }
}
