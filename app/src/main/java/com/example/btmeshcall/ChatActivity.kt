package com.example.btmeshcall

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btmeshcall.ble.ChatMessage
import com.example.btmeshcall.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter

    private val listener: (ChatMessage) -> Unit = { msg ->
        runOnUiThread {
            adapter.updateList(MeshHolder.history.toList())
            binding.messageList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MessageAdapter(MeshHolder.history.toList())
        binding.messageList.layoutManager = LinearLayoutManager(this)
        binding.messageList.adapter = adapter

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                MeshHolder.mesh?.send(ChatMessage.BROADCAST, text)
                binding.messageInput.text.clear()
            }
        }

        MeshHolder.addMessageListener(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        MeshHolder.removeMessageListener(listener)
    }
}

class MessageAdapter(private var items: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val senderText: android.widget.TextView = view.findViewById(R.id.senderText)
        val bodyText: android.widget.TextView = view.findViewById(R.id.bodyText)
    }

    fun updateList(newItems: List<ChatMessage>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.senderText.text = msg.senderId
        holder.bodyText.text = msg.body
    }

    override fun getItemCount(): Int = items.size
    }
