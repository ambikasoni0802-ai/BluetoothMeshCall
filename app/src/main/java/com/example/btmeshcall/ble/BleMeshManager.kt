package com.example.btmeshcall.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleMeshManager(
    private val context: Context,
    val myDeviceId: String,
    private val listener: Listener
) {
    interface Listener {
        fun onMessageReceived(message: ChatMessage)
        fun onPeerConnected(address: String)
        fun onPeerDisconnected(address: String)
        fun onLog(line: String)
    }

    companion object {
        private const val TAG = "BleMeshManager"
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHAT_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val connectedCentrals = ConcurrentHashMap<String, BluetoothDevice>()
    private val connectedPeripherals = ConcurrentHashMap<String, BluetoothGatt>()

    private val seenMessageIds = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>()
    )

    fun start() {
        if (adapter == null || !adapter.isEnabled) {
            listener.onLog("Bluetooth is off or not available")
            return
        }
        startGattServer()
        startAdvertising()
        startScanning()
    }

    fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        connectedPeripherals.values.forEach { it.close() }
        connectedPeripherals.clear()
        gattServer?.close()
    }

    fun send
