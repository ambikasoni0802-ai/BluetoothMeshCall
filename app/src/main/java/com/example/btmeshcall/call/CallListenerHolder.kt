package com.example.btmeshcall.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.widget.Toast

@SuppressLint("MissingPermission")
object CallListenerHolder {
    private var started = false

    fun start(adapter: BluetoothAdapter, context: Context) {
        if (started) return
        started = true
        loopListen(adapter, context.applicationContext)
    }

    private fun loopListen(adapter: BluetoothAdapter, context: Context) {
        val mgr = BluetoothAudioCallManager(adapter)
        mgr.listenForIncomingCall(object : BluetoothAudioCallManager.Listener {
            override fun onCallConnected() {
                context.startService(Intent(context, AudioCallService::class.java))
                Toast.makeText(context, "Incoming call connected", Toast.LENGTH_SHORT).show()
            }

            override fun onCallEnded(reason: String) {
                context.stopService(Intent(context, AudioCallService::class.java))
                loopListen(adapter, context)
            }
        })
    }
}
