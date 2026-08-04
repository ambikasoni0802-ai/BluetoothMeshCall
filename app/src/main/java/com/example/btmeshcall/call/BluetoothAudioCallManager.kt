package com.example.btmeshcall.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class BluetoothAudioCallManager(private val adapter: BluetoothAdapter) {

    companion object {
        val CALL_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val SAMPLE_RATE = 16000
        private const val TAG = "BtAudioCall"
    }

    interface Listener {
        fun onCallConnected()
        fun onCallEnded(reason: String)
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private val running = AtomicBoolean(false)

    private var recordThread: Thread? = null
    private var playbackThread: Thread? = null
    private var acceptThread: Thread? = null

    private var fallbackServerSocket: BluetoothServerSocket? = null
    private val alreadyConnected = AtomicBoolean(false)

    fun listenForIncomingCall(listener: Listener) {
        alreadyConnected.set(false)

        acceptThread = Thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BtMeshCall", CALL_UUID)
                val socket = serverSocket?.accept()
                if (socket != null && alreadyConnected.compareAndSet(false, true)) {
                    fallbackServerSocket?.close()
                    activeSocket = socket
                    beginStreaming(socket, listener)
                } else {
                    socket?.close()
                }
            } catch (e: IOException) {
                Log.e(TAG, "standard listen ended: ${e.message}")
            }
        }
        acceptThread?.start()

        Thread {
            try {
                val method = adapter.javaClass.getMethod("listenUsingRfcommOn", Int::class.javaPrimitiveType)
                fallbackServerSocket = method.invoke(adapter, 1) as BluetoothServerSocket
                val socket = fallbackServerSocket?.accept()
                if (socket != null && alreadyConnected.compareAndSet(false, true)) {
                    serverSocket?.close()
                    activeSocket = socket
                    beginStreaming(socket, listener)
                } else {
                    socket?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fallback listen ended: ${e.message}")
            }
        }.start()
    }

    fun callDevice(device: BluetoothDevice, listener: Listener) {
        Thread {
            adapter.cancelDiscovery()
            try {
                val socket = device.createRfcommSocketToServiceRecord(CALL_UUID)
                socket.connect()
                activeSocket = socket
                beginStreaming(socket, listener)
                return@Thread
            } catch (e: IOException) {
                Log.e(TAG, "standard connect failed, trying fallback", e)
            }

            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val fallbackSocket = method.invoke(device, 1) as BluetoothSocket
                fallbackSocket.connect()
                activeSocket = fallbackSocket
                beginStreaming(fallbackSocket, listener)
            } catch (e: Exception) {
                Log.e(TAG, "fallback connect also failed", e)
                listener.onCallEnded("Connect failed: ${e.message}")
            }
        }.start()
    }

    fun endCall() {
        running.set(false)
        try { activeSocket?.close() } catch (_: IOException) {}
        try { serverSocket?.close() } catch (_: IOException) {}
        try { fallbackServerSocket?.close() } catch (_: IOException) {}
        recordThread?.interrupt()
        playbackThread?.interrupt()
    }

    private fun beginStreaming(socket: BluetoothSocket, listener: Listener) {
        running.set(true)
        listener.onCallConnected()
        val input = socket.inputStream
        val output = socket.outputStream
        startMicUpload(output)
        startEarPlayback(input)
    }

    @SuppressLint("MissingPermission")
    private fun startMicUpload(output: OutputStream) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        )
        recordThread = Thread {
            val buffer = ByteArray(bufSize)
            try {
                recorder.startRecording()
                while (running.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) output.write(buffer, 0, read)
                }
            } catch (e: IOException) {
                Log.e(TAG, "mic upload stopped", e)
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
        recordThread?.start()
    }

    private fun startEarPlayback(input: InputStream) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 2048)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .build()

        playbackThread = Thread {
            val buffer = ByteArray(bufSize)
            try {
                track.play()
                while (running.get()) {
                    val read = input.read(buffer)
                    if (read > 0) track.write(buffer, 0, read)
                }
            } catch (e: IOException) {
                Log.e(TAG, "playback stopped", e)
            } finally {
                track.stop()
                track.release()
            }
        }
        playbackThread?.start()
    }
}
