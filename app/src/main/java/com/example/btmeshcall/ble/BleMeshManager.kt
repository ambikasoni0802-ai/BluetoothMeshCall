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

    fun send(targetId: String, body: String) {
        val msg = ChatMessage.newOutgoing(myDeviceId, targetId, body)
        seenMessageIds.add(msg.msgId)
        listener.onMessageReceived(msg)
        relay(msg)
    }

    private fun relay(msg: ChatMessage) {
        if (msg.ttl <= 0) return
        val wire = msg.copy(ttl = msg.ttl - 1).toWire().toByteArray(Charsets.UTF_8)

        connectedCentrals.values.forEach { device ->
            notifyCentral(device, wire)
        }
        connectedPeripherals.values.forEach { gatt ->
            writeToPeripheral(gatt, wire)
        }
    }

    private fun handleIncomingWire(wire: String) {
        val msg = ChatMessage.fromWire(wire) ?: return
        if (!seenMessageIds.add(msg.msgId)) return

        if (msg.targetId == ChatMessage.BROADCAST || msg.targetId == myDeviceId) {
            listener.onMessageReceived(msg)
        }
        relay(msg)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedCentrals[device.address] = device
                listener.onPeerConnected(device.address)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedCentrals.remove(device.address)
                listener.onPeerDisconnected(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == CHAT_CHARACTERISTIC_UUID) {
                handleIncomingWire(String(value, Charsets.UTF_8))
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    private fun startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val chatChar = BluetoothGattCharacteristic(
            CHAT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        chatChar.addDescriptor(cccd)
        service.addCharacteristic(chatChar)
        gattServer?.addService(service)
    }

    private fun notifyCentral(device: BluetoothDevice, wire: ByteArray) {
        val service = gattServer?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHAT_CHARACTERISTIC_UUID) ?: return
        characteristic.value = wire
        gattServer?.notifyCharacteristicChanged(device, characteristic, false)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            listener.onLog("Advertise failed: $errorCode")
        }
    }

    private fun startAdvertising() {
        advertiser = adapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (connectedPeripherals.containsKey(device.address)) return
            if (connectedCentrals.containsKey(device.address)) return
            device.connectGatt(context, false, gattClientCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onLog("Scan failed: $errorCode")
        }
    }

    private fun startScanning() {
        scanner = adapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedPeripherals.remove(gatt.device.address)
                listener.onPeerDisconnected(gatt.device.address)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            val characteristic = service.getCharacteristic(CHAT_CHARACTERISTIC_UUID) ?: return

            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(CCCD_UUID)?.let { descriptor ->
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            connectedPeripherals[gatt.device.address] = gatt
            listener.onPeerConnected(gatt.device.address)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAT_CHARACTERISTIC_UUID) {
                handleIncomingWire(String(characteristic.value, Charsets.UTF_8))
            }
        }
    }

    private fun writeToPeripheral(gatt: BluetoothGatt, wire: ByteArray) {
        val service = gatt.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CHAT_CHARACTERISTIC_UUID) ?: return
        characteristic.value = wire
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(characteristic)
    }
}
