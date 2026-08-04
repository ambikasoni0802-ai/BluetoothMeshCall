package com.example.btmeshcall.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.btmeshcall.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEER_ADDRESS = "peer_address"
    }

    private lateinit var binding: ActivityCallBinding
    private lateinit var callManager: BluetoothAudioCallManager

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val address = intent.getStringExtra(EXTRA_PEER_ADDRESS)
        val btManager = getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter = btManager.adapter

        callManager = BluetoothAudioCallManager(adapter)

        startService(Intent(this, AudioCallService::class.java))

        val listener = object : BluetoothAudioCallManager.Listener {
            override fun onCallConnected() {
                runOnUiThread { binding.callStatusText.text = "Call connected" }
            }
            override fun onCallEnded(reason: String) {
                runOnUiThread { binding.callStatusText.text = "Call ended: $reason" }
            }
        }

        if (address != null) {
            binding.callStatusText.text = "Calling $address ..."
            val device = adapter.getRemoteDevice(address)
            callManager.callDevice(device, listener)
        } else {
            binding.callStatusText.text = "Waiting for incoming call..."
            callManager.listenForIncomingCall(listener)
        }

        binding.endCallButton.setOnClickListener {
            callManager.endCall()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager.endCall()
        stopService(Intent(this, AudioCallService::class.java))
    }
}
