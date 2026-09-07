package com.example.btchat

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var adapter: BluetoothAdapter

    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private lateinit var statusText: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var chatBox: TextView
    private lateinit var messageInput: EditText

    private lateinit var connectButton: Button
    private lateinit var callButton: Button
    private lateinit var waitButton: Button
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button
    private lateinit var endButton: Button
    private lateinit var sendButton: Button

    @Volatile
    private var callActive = false

    private val BT_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = BluetoothAdapter.getDefaultAdapter()

        if (!hasBluetoothPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.RECORD_AUDIO
                ), 100
            )
        }

        createModernUI()
        loadPairedDevices()
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelf Permission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun createModernUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 20, 24, 24)
        root.setBackgroundColor(Color.rgb(245, 247, 251))

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        val header = TextView(this)
        header.text = "🔵  BT Chat"
        header.textSize = 28f
        header.setTypeface(null, Typeface.BOLD)
        header.setTextColor(Color.WHITE)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(24, 10, 10, 10)
        header.setBackgroundColor(Color.rgb(20, 80, 180))

        content.addView(
            header,
            LinearLayout.LayoutParams(-1, 75)
        )

        statusText = TextView(this)
        statusText.text = "●  Bluetooth Status: Ready"
        statusText.textSize = 16f
        statusText.setTypeface(null, Typeface.BOLD)
        statusText.setTextColor(Color.rgb(20, 80, 180))
        statusText.setPadding(18, 22, 18, 22)

        content.addView(statusText)

        val phoneTitle = TextView(this)
        phoneTitle.text = "📱  SELECT PAIRED PHONE"
        phoneTitle.textSize = 14f
        phoneTitle.setTypeface(null, Typeface.BOLD)
        phoneTitle.setTextColor(Color.DKGRAY)

        content.addView(phoneTitle)

        deviceSpinner = Spinner(this)
        content.addView(
            deviceSpinner,
            LinearLayout.LayoutParams(-1, 55)
        )

        connectButton = makeButton("🔗  CONNECT PHONE")
        content.addView(connectButton)

        waitButton = makeButton("📡  WAIT FOR CONNECTION")
        content.addView(waitButton)

        val chatTitle = TextView(this)
        chatTitle.text = "💬  CHAT"
        chatTitle.textSize = 20f
        chatTitle.setTypeface(null, Typeface.BOLD)
        chatTitle.setTextColor(Color.rgb(20, 80, 180))
        chatTitle.setPadding(0, 28, 0, 10)

        content.addView(chatTitle)

        chatBox = TextView(this)
        chatBox.text = "No messages yet...\n"
        chatBox.textSize = 16f
        chatBox.setTextColor(Color.DKGRAY)
        chatBox.setPadding(18, 18, 18, 18)
        chatBox.setBackgroundColor(Color.WHITE)

        val chatParams = LinearLayout.LayoutParams(-1, 230)
        content.addView(chatBox, chatParams)

        val chatRow = LinearLayout(this)
        chatRow.orientation = LinearLayout.HORIZONTAL

        messageInput = EditText(this)
        messageInput.hint = "Type message..."
        messageInput.textSize = 16f
        messageInput.setSingleLine(true)

        sendButton = makeButton("SEND")
        sendButton.textSize = 13f

        chatRow.addView(
            messageInput,
            LinearLayout.LayoutParams(0, 60, 1f)
        )

        chatRow.addView(
            sendButton,
            LinearLayout.LayoutParams(105, 60)
        )

        content.addView(chatRow)

        val callTitle = TextView(this)
        callTitle.text = "📞  VOICE CALL"
        callTitle.textSize = 20f
        callTitle.setTypeface(null, Typeface.BOLD)
        callTitle.setTextColor(Color.rgb(20, 80, 180))
        callTitle.setPadding(0, 28, 0, 10)

        content.addView(callTitle)

        callButton = makeButton("📞  CALL")
        callButton.setTextColor(Color.rgb(0, 120, 50))
        content.addView(callButton)

        waitButton.setOnClickListener {
            waitForConnection()
        }

        acceptButton = makeButton("✅  ACCEPT CALL")
        acceptButton.visibility = View.GONE
        content.addView(acceptButton)

        rejectButton = makeButton("❌  REJECT CALL")
        rejectButton.visibility = View.GONE
        content.addView(rejectButton)

        endButton = makeButton("🔴  END CALL")
        endButton.visibility = View.GONE
        content.addView(endButton)

        connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        callButton.setOnClickListener {
            startVoiceCall()
        }

        acceptButton.setOnClickListener {
            acceptCall()
        }

        rejectButton.setOnClickListener {
            rejectCall()
        }

        endButton.setOnClickListener {
            endCall()
        }

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)
    }

    private fun makeButton(text: String): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 15f
        b.setTypeface(null, Typeface.BOLD)
        b.setTextColor(Color.rgb(30, 30, 30))
        return b
    }

    private fun loadPairedDevices() {

        if (!hasBluetoothPermission()) return

        val devices = adapter.bondedDevices.toList()

        val names = if (devices.isEmpty()) {
            listOf("No paired phone")
        } else {
            devices.map {
                "${it.name ?: "Unknown"}\n${it.address}"
            }
        }

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        deviceSpinner.adapter = spinnerAdapter
    }

    private fun connectToSelectedDevice() {

        if (!hasBluetoothPermission()) {
            statusText.text = "Bluetooth permission required"
            return
        }

        val devices = adapter.bondedDevices.toList()

        if (devices.isEmpty()) {
            statusText.text = "पहले दोनों phones को Bluetooth से pair करें"
            return
        }

        val device = devices[deviceSpinner.selectedItemPosition]

        statusText.text = "Connecting..."

        thread {

            try {
                socket?.close()

                socket = device.createRfcommSocketToServiceRecord(BT_UUID)
                socket!!.connect()

                input = socket!!.inputStream
                output = socket!!.outputStream

                runOnUiThread {
                    statusText.text = "● Connected: ${device.name}"
                }

                listenForMessages()

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text = "Connection failed"
                    Toast.makeText(
                        this,
                        e.message ?: "Connection error",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun waitForConnection() {

        if (!hasBluetoothPermission()) return

        statusText.text = "Waiting for another phone..."

        thread {

            try {

                serverSocket =
                    adapter.listenUsingRfcommWithServiceRecord(
                        "BT Chat",
                        BT_UUID
                    )

                val connected = serverSocket!!.accept()

                socket = connected
                input = connected.inputStream
                output = connected.outputStream

                runOnUiThread {
                    statusText.text = "● Phone Connected"
                }

                listenForMessages()

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text = "Waiting stopped"
                }
            }
        }
    }

    private fun listenForMessages() {

        thread {

            try {

                val buffer = ByteArray(1024)

                while (true) {

                    val count = input?.read(buffer) ?: break

                    if (count > 0) {

                        val message =
                            String(buffer, 0, count)

                        runOnUiThread {

                            if (message.trim() == "CALL_REQUEST") {

                                statusText.text =
                                    "📞 Incoming Voice Call"

                                acceptButton.visibility = View.VISIBLE
                                rejectButton.visibility = View.VISIBLE

                            } else {

                                chatBox.append(
                                    "\n👤 Other: $message"
                                )
                            }
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun sendMessage() {

        val text = messageInput.text.toString().trim()

        if (text.isEmpty()) return

        try {

            output?.write(text.toByteArray())
            output?.flush()

            chatBox.append("\n🧑 You: $text")
            messageInput.text.clear()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Phone connected नहीं है",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startVoiceCall() {

        if (socket == null) {
            Toast.makeText(
                this,
                "पहले phone connect करें",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {

            output?.write("CALL_REQUEST".toByteArray())
            output?.flush()

            statusText.text = "📞 Calling..."

            callButton.visibility = View.GONE
            endButton.visibility = View.VISIBLE

            callActive = true

            startAudio()

        } catch (_: Exception) {
            statusText.text = "Call failed"
        }
    }

    private fun acceptCall() {

        acceptButton.visibility = View.GONE
        rejectButton.visibility = View.GONE

        callButton.visibility = View.GONE
        endButton.visibility = View.VISIBLE

        statusText.text = "📞 Voice Call Connected"

        callActive = true

        startAudio()
    }

    private fun rejectCall() {

        try {
            output?.write("CALL_REJECTED".toByteArray())
            output?.flush()
        } catch (_: Exception) {
        }

        acceptButton.visibility = View.GONE
        rejectButton.visibility = View.GONE

        statusText.text = "Call rejected"
    }

    private fun endCall() {

        callActive = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }

        audioRecord?.release()
        audioTrack?.release()

        audioRecord = null
        audioTrack = null

        callButton.visibility = View.VISIBLE
        endButton.visibility = View.GONE

        statusText.text = "● Connected"
    }

    private fun startAudio() {

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                200
            )
            return
        }

        thread {

            try {

                val sampleRate = 16000

                val minBuffer =
                    AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )

                audioRecord!!.startRecording()

                val buffer = ByteArray(minBuffer)

                while (callActive) {

                    val read =
                        audioRecord!!.read(
                            buffer,
                            0,
                            buffer.size
                        )

                    if (read > 0) {
                        output?.write(buffer, 0, read)
                        output?.flush()
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {

        callActive = false

        try {
            audioRecord?.release()
            audioTrack?.release()
            input?.close()
            output?.close()
            socket?.close()
            serverSocket?.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
