package com.example.btmeshcall

import android.content.Context
import com.example.btmeshcall.ble.BleMeshManager
import com.example.btmeshcall.ble.ChatMessage
import java.util.Collections

/**
 * Simple process-wide singleton holding the one BleMeshManager instance and the
 * running chat history, so ChatActivity and MainActivity share the same mesh
 * connection instead of each opening their own.
 */
object MeshHolder {
    var mesh: BleMeshManager? = null
        private set

    val history: MutableList<ChatMessage> = Collections.synchronizedList(mutableListOf())
    private val messageListeners = mutableListOf<(ChatMessage) -> Unit>()

    fun init(context: Context, myDeviceId: String, listener: BleMeshManager.Listener) {
        if (mesh != null) return
        mesh = BleMeshManager(context, myDeviceId, object : BleMeshManager.Listener {
            override fun onMessageReceived(message: ChatMessage) {
                history.add(message)
                messageListeners.forEach { it(message) }
                listener.onMessageReceived(message)
            }
            override fun onPeerConnected(address: String) = listener.onPeerConnected(address)
            override fun onPeerDisconnected(address: String) = listener.onPeerDisconnected(address)
            override fun onLog(line: String) = listener.onLog(line)
        })
    }

    fun addMessageListener(l: (ChatMessage) -> Unit) {
        messageListeners.add(l)
    }

    fun removeMessageListener(l: (ChatMessage) -> Unit) {
        messageListeners.remove(l)
    }
}
