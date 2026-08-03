package com.example.btmeshcall.ble

import java.util.UUID

/**
 * A single mesh chat message.
 *
 * Wire format (pipe separated, kept simple so it fits in BLE MTU-sized chunks):
 *   msgId|ttl|senderId|targetId|body
 *
 * targetId == "*" means "broadcast to everyone in range of the mesh".
 * ttl is decremented at every hop; when it hits 0 the message is not
 * relayed any further (stops infinite flooding).
 */
data class ChatMessage(
    val msgId: String,
    var ttl: Int,
    val senderId: String,
    val targetId: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toWire(): String = "$msgId|$ttl|$senderId|$targetId|$body"

    companion object {
        const val BROADCAST = "*"
        const val DEFAULT_TTL = 6 // max hops before message is dropped

        fun newOutgoing(senderId: String, targetId: String, body: String): ChatMessage =
            ChatMessage(
                msgId = UUID.randomUUID().toString(),
                ttl = DEFAULT_TTL,
                senderId = senderId,
                targetId = targetId,
                body = body
            )

        fun fromWire(wire: String): ChatMessage? {
            val parts = wire.split("|", limit = 5)
            if (parts.size != 5) return null
            val ttl = parts[1].toIntOrNull() ?: return null
            return ChatMessage(
                msgId = parts[0],
                ttl = ttl,
                senderId = parts[2],
                targetId = parts[3],
                body = parts[4]
            )
        }
    }
}
