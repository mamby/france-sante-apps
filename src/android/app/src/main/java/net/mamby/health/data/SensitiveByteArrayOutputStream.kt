package net.mamby.health.data

import java.io.ByteArrayOutputStream

internal class SensitiveByteArrayOutputStream(initialSize: Int = DEFAULT_INITIAL_SIZE) :
    ByteArrayOutputStream(initialSize) {
    fun takeBytes(): ByteArray = try {
        toByteArray()
    } finally {
        clear()
    }

    override fun close() {
        clear()
        super.close()
    }

    private fun clear() {
        buf.fill(0)
        reset()
    }

    private companion object {
        const val DEFAULT_INITIAL_SIZE = 32
    }
}
