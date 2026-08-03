package com.example.btmeshcall

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btmeshcall.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val peerAddresses = linkedSetOf<String>()
    private lateinit var peerAdapter: PeerAdapter

    // This device's short mesh id - generated once and reused (in a real shipped app,
    // persist this in SharedPreferences instead of regenerating each launch).
    val myDeviceId: String by lazy {
        UUID.randomUUID().toString().substring(0, 8)
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
        }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initBluetoothAndMesh()
        } else {
            Toast.makeText(this, "Bluetooth/mic permissions are required for this app to work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerAdapter = PeerAdapter(peerAddresses.toList()) { address ->
            val intent = Intent(this, com.example.btmeshcall.call.CallActivity::class.java)
            intent.putExtra(com.example.btmeshcall.call.CallActivity.EXTRA_PEER_ADDRESS, address)
            startActivity(intent)
        }
        binding.peerList.layoutManager = LinearLayoutManager(this)
        binding.peerList.adapter = peerAdapter

        binding.openBroadcastChatButton.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        if (hasAllPermissions()) {
            initBluetoothAndMesh()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun initBluetoothAndMesh() {
        val btManager = getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = btManager.adapter
        if (adapter == null) {
            binding.statusText.text = "Bluetooth: not supported on this device"
            return
        }
        if (!adapter.isEnabled) {
            binding.statusText.text = "Bluetooth: please turn Bluetooth on, then reopen the app"
            return
        }
        binding.statusText.text = "Bluetooth: on — my mesh id is ${myDeviceId} — scanning for nearby phones..."

        MeshHolder.init(applicationContext, myDeviceId, object : com.example.btmeshcall.ble.BleMeshManager.Listener {
            override fun onMessageReceived(message: com.example.btmeshcall.ble.ChatMessage) {
                // ChatActivity listens to MeshHolder directly while open
            }
            override fun onPeerConnected(address: String) {
                runOnUiThread {
                    if (peerAddresses.add(address)) {
                        peerAdapter.updateList(peerAddresses.toList())
                    }
                }
            }
            override fun onPeerDisconnected(address: String) {
                runOnUiThread {
                    peerAddresses.remove(address)
                    peerAdapter.updateList(peerAddresses.toList())
                }
            }
            override fun onLog(line: String) {
                runOnUiThread { Toast.makeText(this@MainActivity, line, Toast.LENGTH_SHORT).show() }
            }
        })
        MeshHolder.mesh?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        MeshHolder.mesh?.stop()
    }
}

class PeerAdapter(
    private var items: List<String>,
    private val onCallClick: (String) -> Unit
) : RecyclerView.Adapter<PeerAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val addressText: android.widget.TextView = view.findViewById(R.id.deviceAddressText)
        val callButton: android.widget.Button = view.findViewById(R.id.callButton)
    }

    fun updateList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val address = items[position]
        holder.addressText.text = address
        holder.callButton.setOnClickListener { onCallClick(address) }
    }

    override fun getItemCount(): Int = items.size
}
