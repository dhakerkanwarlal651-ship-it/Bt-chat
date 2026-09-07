package com.example.btchat

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

    private lateinit var statusText: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var chatContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var connectButton: TextView
    private lateinit var waitButton: TextView
    private lateinit var callButton: TextView
    private lateinit var endCallButton: TextView

    private var callActive = false

    private val BT_UUID: UUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = BluetoothAdapter.getDefaultAdapter()

        window.statusBarColor = Color.rgb(8, 84, 72)
        window.navigationBarColor = Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        requestBluetoothPermission()

        createUI()
        loadPairedDevices()
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.RECORD_AUDIO
            )

            val missing = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 100)
            }
        } else {
            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    101
                )
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun createUI() {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.rgb(238, 241, 239))

        // ================= HEADER =================

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(10), dp(6), dp(10), dp(6))
        header.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(7, 105, 91),
                Color.rgb(0, 137, 123)
            )
        )

        val appIcon = TextView(this)
        appIcon.text = "ᛒ"
        appIcon.textSize = 30f
        appIcon.gravity = Gravity.CENTER
        appIcon.setTextColor(Color.WHITE)
        appIcon.background = rounded(
            Color.rgb(35, 135, 220),
            50
        )

        header.addView(
            appIcon,
            LinearLayout.LayoutParams(
                dp(55),
                dp(55)
            )
        )

        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL
        titleBox.setPadding(dp(14), 0, 0, 0)

        val title = TextView(this)
        title.text = "BT Chat"
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)

        val subtitle = TextView(this)
        subtitle.text = "Bluetooth • Offline"
        subtitle.textSize = 13f
        subtitle.setTextColor(Color.rgb(210, 245, 238))

        titleBox.addView(title)
        titleBox.addView(subtitle)

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        callButton = TextView(this)
        callButton.text = "☎"
        callButton.textSize = 30f
        callButton.gravity = Gravity.CENTER
        callButton.setTextColor(Color.WHITE)

        header.addView(
            callButton,
            LinearLayout.LayoutParams(
                dp(58),
                dp(58)
            )
        )

        val menu = TextView(this)
        menu.text = "⋮"
        menu.textSize = 30f
        menu.gravity = Gravity.CENTER
        menu.setTextColor(Color.WHITE)

        header.addView(
            menu,
            LinearLayout.LayoutParams(
                dp(45),
                dp(58)
            )
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(
                -1,
                dp(70)
            )
        )

        // ================= CONNECTION BAR =================

        val connectionBar = LinearLayout(this)
        connectionBar.orientation = LinearLayout.HORIZONTAL
        connectionBar.gravity = Gravity.CENTER_VERTICAL
        connectionBar.setPadding(
            dp(16),
            0,
            dp(14),
            0
        )
        connectionBar.setBackgroundColor(
            Color.rgb(0, 105, 92)
        )

        val dot = TextView(this)
        dot.text = "●"
        dot.textSize = 22f
        dot.setTextColor(Color.rgb(0, 255, 70))

        connectionBar.addView(
            dot,
            LinearLayout.LayoutParams(
                dp(30),
                -1
            )
        )

        statusText = TextView(this)
        statusText.text = "Bluetooth ready"
        statusText.textSize = 16f
        statusText.setTypeface(null, Typeface.BOLD)
        statusText.setTextColor(Color.WHITE)

        connectionBar.addView(
            statusText,
            LinearLayout.LayoutParams(
                0,
                -1,
                1f
            )
        )

        val bt = TextView(this)
        bt.text = "ᛒ"
        bt.textSize = 28f
        bt.setTextColor(Color.WHITE)
        bt.gravity = Gravity.CENTER

        connectionBar.addView(
            bt,
            LinearLayout.LayoutParams(
                dp(45),
                -1
            )
        )

        root.addView(
            connectionBar,
            LinearLayout.LayoutParams(
                -1,
                dp(52)
            )
        )

        // ================= DEVICE SELECT =================

        val deviceRow = LinearLayout(this)
        deviceRow.orientation = LinearLayout.HORIZONTAL
        deviceRow.gravity = Gravity.CENTER_VERTICAL
        deviceRow.setPadding(
            dp(12),
            dp(8),
            dp(12),
            dp(8)
        )
        deviceRow.setBackgroundColor(
            Color.rgb(246, 247, 246)
        )

        deviceSpinner = Spinner(this)
        deviceSpinner.background = rounded(
            Color.WHITE,
            12
        )

        deviceRow.addView(
            deviceSpinner,
            LinearLayout.LayoutParams(
                0,
                dp(50),
                1f
            )
        )

        connectButton = actionButton(
            "CONNECT"
        )

        deviceRow.addView(
            connectButton,
            LinearLayout.LayoutParams(
                dp(105),
                dp(50)
            ).apply {
                leftMargin = dp(8)
            }
        )

        root.addView(
            deviceRow,
            LinearLayout.LayoutParams(
                -1,
                dp(66)
            )
        )

        // ================= CHAT AREA =================

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(
            Color.rgb(238, 233, 226)
        )

        chatContainer = LinearLayout(this)
        chatContainer.orientation = LinearLayout.VERTICAL
        chatContainer.setPadding(
            dp(10),
            dp(14),
            dp(10),
            dp(14)
        )

        addSystemMessage(
            "Bluetooth chat ready"
        )

        scroll.addView(chatContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        // ================= BOTTOM COMPOSER =================

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setPadding(
            dp(8),
            dp(7),
            dp(8),
            dp(7)
        )
        bottom.setBackgroundColor(
            Color.rgb(246, 247, 246)
        )

        val inputBox = LinearLayout(this)
        inputBox.orientation = LinearLayout.HORIZONTAL
        inputBox.gravity = Gravity.CENTER_VERTICAL
        inputBox.background = rounded(
            Color.WHITE,
            30
        )

        val emoji = TextView(this)
        emoji.text = "☺"
        emoji.textSize = 25f
        emoji.gravity = Gravity.CENTER
        emoji.setTextColor(Color.DKGRAY)

        inputBox.addView(
            emoji,
            LinearLayout.LayoutParams(
                dp(45),
                dp(55)
            )
        )

        messageInput = EditText(this)
        messageInput.hint = "Type a message..."
        messageInput.textSize = 16f
        messageInput.singleLine = true
        messageInput.setTextColor(Color.BLACK)
        messageInput.setHintTextColor(
            Color.rgb(120, 120, 120)
        )
        messageInput.setBackgroundColor(Color.TRANSPARENT)
        messageInput.setPadding(
            0,
            0,
            0,
            0
        )

        inputBox.addView(
            messageInput,
            LinearLayout.LayoutParams(
                0,
                dp(55),
                1f
            )
        )

        val attach = TextView(this)
        attach.text = "📎"
        attach.textSize = 25f
        attach.gravity = Gravity.CENTER

        inputBox.addView(
            attach,
            LinearLayout.LayoutParams(
                dp(45),
                dp(55)
            )
        )

        bottom.addView(
            inputBox,
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )
        )

        val send = TextView(this)
        send.text = "➤"
        send.textSize = 29f
        send.gravity = Gravity.CENTER
        send.setTextColor(Color.WHITE)
        send.background = rounded(
            Color.rgb(0, 150, 136),
            50
        )

        bottom.addView(
            send,
            LinearLayout.LayoutParams(
                dp(58),
                dp(58)
            ).apply {
                leftMargin = dp(7)
            }
        )

        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                -1,
                dp(72)
            )
        )

        // ================= CALL PANEL =================

        endCallButton = actionButton(
            "END CALL"
        )
        endCallButton.visibility = View.GONE

        root.addView(
            endCallButton,
            LinearLayout.LayoutParams(
                -1,
                dp(52)
            )
        )

        // ================= ACTIONS =================

        connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        send.setOnClickListener {
            sendMessage()
        }

        callButton.setOnClickListener {
            startCall()
        }

        endCallButton.setOnClickListener {
            endCall()
        }

        setContentView(root)
    }

    private fun actionButton(text: String): TextView {

        val b = TextView(this)

        b.text = text
        b.textSize = 13f
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.background = rounded(
            Color.rgb(0, 137, 123),
            25
        )

        return b
    }

    // ================= DEVICES =================

    private fun hasBluetoothPermission(): Boolean {

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadPairedDevices() {

        if (!hasBluetoothPermission()) return

        val devices = adapter.bondedDevices.toList()

        val names = if (devices.isEmpty()) {
            listOf("No paired phone")
        } else {
            devices.map {
                "${it.name ?: "Unknown device"}"
            }
        }

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        deviceSpinner.adapter = spinnerAdapter
    }

    // ================= CONNECT =================

    private fun connectToSelectedDevice() {

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        val devices = adapter.bondedDevices.toList()

        if (devices.isEmpty()) {
            statusText.text = "Pair both phones first"
            return
        }

        val position = deviceSpinner.selectedItemPosition

        if (position < 0 || position >= devices.size) {
            return
        }

        val device = devices[position]

        statusText.text =
            "Connecting to ${device.name}..."

        thread {

            try {

                socket?.close()

                socket =
                    device.createRfcommSocketToServiceRecord(
                        BT_UUID
                    )

                socket!!.connect()

                input = socket!!.inputStream
                output = socket!!.outputStream

                runOnUiThread {

                    statusText.text =
                        "Connected to: ${device.name}"

                    addSystemMessage(
                        "Connected securely"
                    )
                }

                listenForMessages()

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "Connection failed"

                    Toast.makeText(
                        this,
                        "Bluetooth connection failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ================= WAIT =================

    private fun waitForConnection() {

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        statusText.text =
            "Waiting for Bluetooth connection..."

        thread {

            try {

                serverSocket =
                    adapter.listenUsingRfcommWithServiceRecord(
                        "BT Chat",
                        BT_UUID
                    )

                val connected =
                    serverSocket!!.accept()

                socket = connected
                input = connected.inputStream
                output = connected.outputStream

                runOnUiThread {

                    statusText.text =
                        "Phone connected"

                    addSystemMessage(
                        "Bluetooth connection established"
                    )
                }

                listenForMessages()

            } catch (_: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Waiting stopped"
                }
            }
        }
    }

    // ================= RECEIVE =================

    private fun listenForMessages() {

        thread {

            try {

                val buffer = ByteArray(4096)

                while (true) {

                    val count =
                        input?.read(buffer) ?: break

                    if (count <= 0) continue

                    val message =
                        String(buffer, 0, count)

                    runOnUiThread {

                        when {

                            message == "CALL_REQUEST" -> {
                                showIncomingCall()
                            }

                            message == "CALL_END" -> {
                                endCall()
                            }

                            message.startsWith("CALL_ACCEPTED") -> {
                                statusText.text =
                                    "Voice call connected"
                            }

                            else -> {
                                addIncomingMessage(message)
                            }
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    // ================= CHAT =================

    private fun sendMessage() {

        val text =
            messageInput.text.toString().trim()

        if (text.isEmpty()) return

        try {

            output?.write(
                text.toByteArray()
            )

            output?.flush()

            addOutgoingMessage(text)

            messageInput.text.clear()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Phone is not connected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addIncomingMessage(text: String) {

        val row = LinearLayout(this)
        row.gravity = Gravity.START

        val bubble = TextView(this)
        bubble.text = text
        bubble.textSize = 17f
        bubble.setTextColor(Color.rgb(25, 25, 25))
        bubble.setPadding(
            dp(16),
            dp(11),
            dp(16),
            dp(11)
        )
        bubble.background = rounded(
            Color.WHITE,
            18
        )

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                -2,
                -2
            ).apply {
                bottomMargin = dp(8)
            }
        )

        chatContainer.addView(row)
    }

    private fun addOutgoingMessage(text: String) {

        val row = LinearLayout(this)
        row.gravity = Gravity.END

        val bubble = TextView(this)
        bubble.text = "$text  ✓✓"
        bubble.textSize = 17f
        bubble.setTextColor(Color.rgb(20, 70, 50))
        bubble.setPadding(
            dp(16),
            dp(11),
            dp(16),
            dp(11)
        )
        bubble.background = rounded(
            Color.rgb(207, 246, 196),
            18
        )

        row.addView(
            bubble,
            LinearLayo
