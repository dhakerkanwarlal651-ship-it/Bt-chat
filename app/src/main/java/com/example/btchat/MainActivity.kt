package com.example.btchat

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var statusText: TextView
    private lateinit var chatText: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var callButton: Button
    private lateinit var answerButton: Button
    private lateinit var rejectButton: Button
    private lateinit var endCallButton: Button

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var callActive = false

    private val appUuid =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        private const val REQUEST_PERMISSION = 100
        private const val REQUEST_ENABLE_BT = 101
        private const val SAMPLE_RATE = 16000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        createUI()

        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth उपलब्ध नहीं है"
            return
        }

        requestPermissionsIfNeeded()

        findViewById<Button>(1001).setOnClickListener {
            enableBluetooth()
        }

        findViewById<Button>(1002).setOnClickListener {
            showPairedDevices()
        }

        findViewById<Button>(1003).setOnClickListener {
            startWaitingForCall()
        }

        callButton.setOnClickListener {
            connectAndCall()
        }

        answerButton.setOnClickListener {
            acceptCall()
        }

        rejectButton.setOnClickListener {
            rejectCall()
        }

        endCallButton.setOnClickListener {
            endCall()
        }
    }

    private fun createUI() {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(25, 25, 25, 25)

        statusText = TextView(this)
        statusText.text = "BT Chat - Voice Call"
        statusText.textSize = 20f

        chatText = TextView(this)
        chatText.text = "Status: तैयार"
        chatText.textSize = 16f

        val enableBtButton = Button(this)
        enableBtButton.id = 1001
        enableBtButton.text = "Bluetooth ON करें"

        val refreshButton = Button(this)
        refreshButton.id = 1002
        refreshButton.text = "Paired Phone चुनें"

        val waitButton = Button(this)
        waitButton.id = 1003
        waitButton.text = "Incoming Call का इंतजार"

        callButton = Button(this)
        callButton.text = "📞 CALL"

        answerButton = Button(this)
        answerButton.text = "✅ ACCEPT CALL"

        rejectButton = Button(this)
        rejectButton.text = "❌ REJECT CALL"

        endCallButton = Button(this)
        endCallButton.text = "🔴 END CALL"

        deviceSpinner = Spinner(this)

        layout.addView(statusText)
        layout.addView(chatText)
        layout.addView(enableBtButton)
        layout.addView(refreshButton)
        layout.addView(deviceSpinner)
        layout.addView(callButton)
        layout.addView(waitButton)
        layout.addView(answerButton)
        layout.addView(rejectButton)
        layout.addView(endCallButton)

        setContentView(layout)
    }

    private fun requestPermissionsIfNeeded() {

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissions.add(Manifest.permission.RECORD_AUDIO)

        val needed = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissions(
                needed.toTypedArray(),
                REQUEST_PERMISSION
            )
        }
    }

    private fun hasBluetoothPermission(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasAudioPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableBluetooth() {

        if (!hasBluetoothPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val intent =
                android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        } else {
            statusText.text = "Bluetooth पहले से ON है"
        }
    }

    private fun showPairedDevices() {

        if (!hasBluetoothPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(
                this,
                "पहले Bluetooth ON करें",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val devices = bluetoothAdapter.bondedDevices.toList()

        if (devices.isEmpty()) {
            statusText.text = "कोई paired phone नहीं मिला"
            return
        }

        val names = devices.map {
            "${it.name ?: "Unknown"}\n${it.address}"
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        deviceSpinner.adapter = adapter

        deviceSpinner.tag = devices

        statusText.text = "${devices.size} paired device मिले"
    }

    private fun getSelectedDevice(): BluetoothDevice? {

        @Suppress("UNCHECKED_CAST")
        val devices =
            deviceSpinner.tag as? List<BluetoothDevice>
                ?: return null

        val position = deviceSpinner.selectedItemPosition

        if (position < 0 || position >= devices.size) {
            return null
        }

        return devices[position]
    }

    private fun connectAndCall() {

        if (!hasBluetoothPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        if (!hasAudioPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        val device = getSelectedDevice()

        if (device == null) {
            Toast.makeText(
                this,
                "पहले Paired Phone चुनें",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        statusText.text = "📞 Phone से connection हो रहा है..."

        thread {

            try {

                bluetoothAdapter.cancelDiscovery()

                val newSocket =
                    device.createRfcommSocketToServiceRecord(appUuid)

                newSocket.connect()

                socket = newSocket
                inputStream = newSocket.inputStream
                outputStream = newSocket.outputStream

                runOnUiThread {
                    statusText.text = "📞 CALL CONNECTED"
                    chatText.text = "दूसरे फोन से आवाज़ जुड़ गई है"
                }

                outputStream?.write("CALL".toByteArray())

                startVoiceCall()

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Connection failed: ${e.message}"
                }

                closeConnection()
            }
        }
    }

    private fun startWaitingForCall() {

        if (!hasBluetoothPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        statusText.text = "📲 Incoming Call का इंतजार..."

        thread {

            var serverSocket: BluetoothServerSocket? = null

            try {

                serverSocket =
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        "BT Chat Voice",
                        appUuid
                    )

                val newSocket = serverSocket.accept()

                socket = newSocket
                inputStream = newSocket.inputStream
                outputStream = newSocket.outputStream

                runOnUiThread {
                    showIncomingCall()
                }

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Waiting stopped: ${e.message}"
                }

            } finally {
                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun showIncomingCall() {

        statusText.text = "📲 INCOMING CALL"

        AlertDialog.Builder(this)
            .setTitle("📞 Incoming Call")
            .setMessage("दूसरे फोन से Voice Call आ रही है")
            .setPositiveButton("ACCEPT") { _, _ ->
                acceptCall()
            }
            .setNegativeButton("REJECT") { _, _ ->
                rejectCall()
            }
            .setCancelable(false)
            .show()
    }

    private fun acceptCall() {

        if (!hasAudioPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        try {
            outputStream?.write("ACCEPT".toByteArray())
        } catch (_: Exception) {
        }

        statusText.text = "📞 CALL CONNECTED"
        chatText.text = "Voice Call चालू है"

        startVoiceCall()
    }

    private fun rejectCall() {

        try {
            outputStream?.write("REJECT".toByteArray())
        } catch (_: Exception) {
        }

        statusText.text = "Call rejected"
        closeConnection()
    }

    private fun startVoiceCall() {

        if (callActive) return

        if (!hasAudioPermission()) {
            requestPermissionsIfNeeded()
            return
        }

        callActive = true

        thread {
            recordAndSendAudio()
        }

        thread {
            receiveAndPlayAudio()
        }
    }

    private fun recordAndSendAudio() {

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBuffer * 2, 4096)

        try {

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord = recorder

            recorder.startRecording()

            val buffer = ByteArray(2048)

            while (callActive) {

                val read = recorder.read(
                    buffer,
                    0,
                    buffer.size
                )

                if (read > 0) {

                    try {
                        outputStream?.write(
                            buffer,
                            0,
                            read
                        )
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            recorder.stop()
            recorder.release()

        } catch (e: Exception) {

            runOnUiThread {
                statusText.text =
                    "Microphone error: ${e.message}"
            }
        }
    }

    private fun receiveAndPlayAudio() {

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBuffer * 2, 4096)

        try {

            val track = AudioTrack(
                android.media.AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack = track

            track.play()

            val buffer = ByteArray(2048)

            while (callActive) {

                val read =
                    inputStream?.read(buffer)
                        ?: break

                if (read > 0) {
                    track.write(
                        buffer,
                        0,
                        read
                    )
                }
            }

            track.stop()
            track.release()

        } catch (e: Exception) {

            runOnUiThread {
                statusText.text =
                    "Speaker error: ${e.message}"
            }
        }
    }

    private fun endCall() {

        callActive = false

        try {
            outputStream?.write("END".toByteArray())
        } catch (_: Exception) {
        }

        audioRecord?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }

            try {
                it.release()
            } catch (_: Exception) {
            }
        }

        audioRecord = null

        audioTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }

            try {
                it.release()
            } catch (_: Exception) {
            }
        }

        audioTrack = null

        statusText.text = "🔴 Call समाप्त"
        chatText.text = "Call ended"

        closeConnection()
    }

    private fun closeConnection() {

        callActive = false

        try {
            inputStream?.close()
        } catch (_: Exception) {
        }

        try {
            outputStream?.close()
        } catch (_: Exception) {
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        inputStream = null
        outputStream = null
        socket = null
    }

    override fun onDestroy() {
        callActive = false
        closeConnection()
        super.onDestroy()
    }
}
