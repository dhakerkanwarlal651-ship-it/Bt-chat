package com.example.btchat

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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
import android.view.inputmethod.InputMethodManager
import android.content.Context
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

    private lateinit var chatContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var statusText: TextView

    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var waitButton: Button

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
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                100
            )
        }

        createWhatsAppUI()
        loadPairedDevices()
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------
    // WHATSAPP STYLE UI
    // ---------------------------------------------------------

    private fun createWhatsAppUI() {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setOnApplyWindowInsetsListener { view, insets ->
    val top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
    val bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom

    view.setPadding(
        view.paddingLeft,
        top,
        view.paddingRight,
        bottom + 12
    )

    insets
        }
        root.setBackgroundColor(Color.rgb(236, 229, 221))

        // ================= HEADER =================

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(8, 0, 8, 0)
        header.setBackgroundColor(Color.rgb(0, 128, 105))

        val backButton = TextView(this)
        backButton.text = "‹"
        backButton.textSize = 42f
        backButton.setTextColor(Color.WHITE)
        backButton.gravity = Gravity.CENTER
        backButton.setPadding(5, 0, 5, 0)

        header.addView(
            backButton,
            LinearLayout.LayoutParams(48, 70)
        )

        val logo = TextView(this)
        logo.text = "♢"
        logo.textSize = 32f
        logo.gravity = Gravity.CENTER
        logo.setTextColor(Color.WHITE)

        val logoBg = GradientDrawable()
        logoBg.shape = GradientDrawable.OVAL
        logoBg.setColor(Color.rgb(37, 150, 243))
        logo.background = logoBg

        header.addView(
            logo,
            LinearLayout.LayoutParams(55, 55)
        )

        val titleLayout = LinearLayout(this)
        titleLayout.orientation = LinearLayout.VERTICAL
        titleLayout.setPadding(10, 0, 0, 0)

        val title = TextView(this)
        title.text = "BT Chat"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)

        val subtitle = TextView(this)
        subtitle.text = "Bluetooth Chat"
        subtitle.textSize = 14f
        subtitle.setTextColor(Color.rgb(220, 240, 235))

        titleLayout.addView(title)
        titleLayout.addView(subtitle)

        header.addView(
            titleLayout,
            LinearLayout.LayoutParams(0, 70, 1f)
        )

        val callButton = TextView(this)
        callButton.text = "☎"
        callButton.textSize = 28f
        callButton.gravity = Gravity.CENTER
        callButton.setTextColor(Color.WHITE)

        header.addView(
            callButton,
            LinearLayout.LayoutParams(55, 70)
        )

        val menuButton = TextView(this)
        menuButton.text = "⋮"
        menuButton.textSize = 30f
        menuButton.gravity = Gravity.CENTER
        menuButton.setTextColor(Color.WHITE)

        header.addView(
            menuButton,
            LinearLayout.LayoutParams(40, 70)
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(-1, 70)
        )

        // ================= CONNECTION BAR =================

        val connectionBar = LinearLayout(this)
        connectionBar.orientation = LinearLayout.HORIZONTAL
        connectionBar.gravity = Gravity.CENTER_VERTICAL
        connectionBar.setPadding(15, 0, 15, 0)
        connectionBar.setBackgroundColor(Color.rgb(0, 105, 88))

        val greenDot = TextView(this)
        greenDot.text = "●"
        greenDot.textSize = 18f
        greenDot.setTextColor(Color.GREEN)

        connectionBar.addView(
            greenDot,
            LinearLayout.LayoutParams(35, 45)
        )

        statusText = TextView(this)
        statusText.text = "Connecting Bluetooth..."
        statusText.textSize = 15f
        statusText.setTypeface(null, Typeface.BOLD)
        statusText.setTextColor(Color.CYAN)

        connectionBar.addView(
            statusText,
            LinearLayout.LayoutParams(0, 45, 1f)
        )

        val btIcon = TextView(this)
        btIcon.text = "ᛒ"
        btIcon.textSize = 28f
        btIcon.setTextColor(Color.WHITE)
        btIcon.gravity = Gravity.CENTER

        connectionBar.addView(
            btIcon,
            LinearLayout.LayoutParams(45, 45)
        )

        root.addView(
            connectionBar,
            LinearLayout.LayoutParams(-1, 45)
        )

        // ================= DEVICE AREA =================

        val deviceArea = LinearLayout(this)
        deviceArea.orientation = LinearLayout.VERTICAL
        deviceArea.setPadding(10, 5, 10, 5)
        deviceArea.setBackgroundColor(Color.rgb(230, 224, 218))

        deviceSpinner = Spinner(this)

        deviceArea.addView(
            deviceSpinner,
            LinearLayout.LayoutParams(-1, 45)
        )

        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL

        connectButton = Button(this)
        connectButton.text = "CONNECT"
        connectButton.textSize = 12f

        waitButton = Button(this)
        waitButton.text = "WAIT"
        waitButton.textSize = 12f

        buttonRow.addView(
            connectButton,
            LinearLayout.LayoutParams(0, 45, 1f)
        )

        buttonRow.addView(
            waitButton,
            LinearLayout.LayoutParams(0, 45, 1f)
        )

        deviceArea.addView(buttonRow)

        root.addView(
            deviceArea,
            LinearLayout.LayoutParams(-1, 95)
        )

        // ================= CHAT AREA =================

        val scrollView = ScrollView(this)
        scrollView.isFillViewport = true

        chatContainer = LinearLayout(this)
        chatContainer.orientation = LinearLayout.VERTICAL
        chatContainer.setPadding(12, 15, 12, 15)

        val chatBackground = GradientDrawable()
        chatBackground.setColor(Color.rgb(236, 229, 221))
        chatContainer.background = chatBackground

        scrollView.addView(chatContainer)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        // ================= MESSAGE BAR =================

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setPadding(7, 6, 7, 6)
        bottom.setBackgroundColor(Color.rgb(236, 229, 221))

        val inputBox = LinearLayout(this)
        inputBox.orientation = LinearLayout.HORIZONTAL
        inputBox.gravity = Gravity.CENTER_VERTICAL
        inputBox.setPadding(8, 0, 5, 0)

        val inputBg = GradientDrawable()
        inputBg.shape = GradientDrawable.RECTANGLE
        inputBg.cornerRadius = 45f
        inputBg.setColor(Color.WHITE)
        inputBox.background = inputBg

        val emojiButton = TextView(this)
        emojiButton.text = "☺"
        emojiButton.textSize = 27f
        emojiButton.setTextColor(Color.DKGRAY)
        emojiButton.gravity = Gravity.CENTER

        inputBox.addView(
            emojiButton,
            LinearLayout.LayoutParams(45, 55)
        )

        messageInput = EditText(this)
        messageInput.hint = "Type a message..."
        messageInput.textSize = 16f
        messageInput.setSingleLine(true)
        messageInput.setBackgroundColor(Color.TRANSPARENT)
        messageInput.setPadding(5, 0, 5, 0)

        inputBox.addView(
            messageInput,
            LinearLayout.LayoutParams(0, 55, 1f)
        )

        val attachButton = TextView(this)
        attachButton.text = "📎"
        attachButton.textSize = 24f
        attachButton.gravity = Gravity.CENTER

        inputBox.addView(
            attachButton,
            LinearLayout.LayoutParams(45, 55)
        )

        val cameraButton = TextView(this)
        cameraButton.text = "▣"
        cameraButton.textSize = 24f
        cameraButton.gravity = Gravity.CENTER

        inputBox.addView(
            cameraButton,
            LinearLayout.LayoutParams(45, 55)
        )

        bottom.addView(
            inputBox,
            LinearLayout.LayoutParams(0, 58, 1f)
        )

        val sendButton = TextView(this)
        sendButton.text = "➤"
        sendButton.textSize = 30f
        sendButton.gravity = Gravity.CENTER
        sendButton.setTextColor(Color.WHITE)

        val sendBg = GradientDrawable()
        sendBg.shape = GradientDrawable.OVAL
        sendBg.setColor(Color.rgb(0, 168, 132))
        sendButton.background = sendBg

        val sendParams = LinearLayout.LayoutParams(58, 58)
        sendParams.setMargins(6, 0, 0, 0)

        bottom.addView(
            sendButton,
            sendParams
        )

        val bottomParams = LinearLayout.LayoutParams(-1, 70)
bottomParams.setMargins(0, 0, 0, 12)

root.addView(
    bottom,
    bottomParams
)

        // ================= BUTTON ACTIONS =================

        connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        waitButton.setOnClickListener {
            waitForConnection()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        callButton.setOnClickListener {
            Toast.makeText(
                this,
                "Voice call feature",
                Toast.LENGTH_SHORT
            ).show()
        }

        backButton.setOnClickListener {
            finish()
        }

        setContentView(root)

        addReceivedMessage(
            "Bluetooth chat शुरू करें 👋",
            "09:12"
        )
    }

    // ---------------------------------------------------------
    // MESSAGE BUBBLE
    // ---------------------------------------------------------

    private fun addSentMessage(message: String) {

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END

        val bubble = TextView(this)

        bubble.text = "$message    ${getTime()}  ✓✓"
        bubble.textSize = 16f
        bubble.setTextColor(Color.rgb(20, 20, 20))
        bubble.setPadding(15, 10, 10, 8)

        val bg = GradientDrawable()
        bg.setColor(Color.rgb(220, 248, 198))
        bg.cornerRadius = 18f

        bubble.background = bg

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(50, 4, 0, 4)

        row.addView(bubble, params)

        chatContainer.addView(row)

        scrollToBottom()
    }

    private fun addReceivedMessage(message: String, time: String = getTime()) {

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.START

        val bubble = TextView(this)

        bubble.text = "$message    $time"
        bubble.textSize = 16f
        bubble.setTextColor(Color.rgb(20, 20, 20))
        bubble.setPadding(15, 10, 10, 8)

        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = 18f

        bubble.background = bg

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, 4, 50, 4)

        row.addView(bubble, params)

        chatContainer.addView(row)

        scrollToBottom()
    }

    private fun scrollToBottom() {
        chatContainer.post {
            val parent = chatContainer.parent
            if (parent is ScrollView) {
                parent.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun getTime(): String {
        val calendar = java.util.Calendar.getInstance()

        val hour = calendar.get(
            java.util.Calendar.HOUR
        )

        val minute = calendar.get(
            java.util.Calendar.MINUTE
        )

        val amPm = if (
            calendar.get(java.util.Calendar.AM_PM)
            == java.util.Calendar.AM
        ) {
            "AM"
        } else {
            "PM"
        }

        val h = if (hour == 0) 12 else hour

        return String.format(
            "%02d:%02d %s",
            h,
            minute,
            amPm
        )
    }

    // ---------------------------------------------------------
    // BLUETOOTH DEVICES
    // ---------------------------------------------------------

    private fun loadPairedDevices() {

        if (!hasBluetoothPermission()) return

        val devices = adapter.bondedDevices.toList()

        val names = if (devices.isEmpty()) {
            listOf("No paired phone")
        } else {
            devices.map {
                it.name ?: "Unknown device"
            }
        }

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        deviceSpinner.adapter = spinnerAdapter
    }

    // ---------------------------------------------------------
    // CONNECT
    // ---------------------------------------------------------

    private fun connectToSelectedDevice() {

        if (!hasBluetoothPermission()) {
            Toast.makeText(
                this,
                "Bluetooth permission required",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val devices = adapter.bondedDevices.toList()

        if (devices.isEmpty()) {
            Toast.makeText(
                this,
                "Pehle dono phones ko Bluetooth se pair karein",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val position = deviceSpinner.selectedItemPosition

        if (position < 0 || position >= devices.size) return

        val device = devices[position]

        statusText.text = "Connecting..."

        thread {

            try {

                socket?.close()

                socket =
                    device.createRfcommSocketToServiceRecord(BT_UUID)

                socket!!.connect()

                input = socket!!.inputStream
                output = socket!!.outputStream

                runOnUiThread {

                    statusText.text =
                        "Connected to: ${device.name}"

                    Toast.makeText(
                        this,
                        "Bluetooth connected",
                        Toast.LENGTH_SHORT
                    ).show()
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

    // ---------------------------------------------------------
    // WAIT FOR CONNECTION
    // ---------------------------------------------------------

    private fun waitForConnection() {

        if (!hasBluetoothPermission()) return

        statusText.text = "Waiting for connection..."

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
                        "Connected to Bluetooth phone"

                    Toast.makeText(
                        this,
                        "Phone connected",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                listenForMessages()

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Waiting stopped"
                }
            }
        }
    }

    // ---------------------------------------------------------
    // RECEIVE MESSAGE
    // ---------------------------------------------------------

    private fun listenForMessages() {

        thread {

            try {

                val buffer = ByteArray(4096)

                while (true) {

                    val count =
                        input?.read(buffer) ?: break

                    if (count > 0) {

                        val message =
                            String(
                                buffer,
                                0,
                                count
                            ).trim()

                        if (message.isNotEmpty()) {

                            runOnUiThread {

                                if (
                                    message != "CALL_REQUEST" &&
                                    message != "CALL_REJECTED"
                                ) {

                                    addReceivedMessage(
                                        message
                                    )
                                }
                            }
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------
    // SEND MESSAGE
    // ---------------------------------------------------------

    private fun sendMessage() {

        val text =
            messageInput.text
                .toString()
                .trim()

        if (text.isEmpty()) return

        try {

            output?.write(
                text.toByteArray()
            )

            output?.flush()

            addSentMessage(text)

            messageInput.text.clear()

            val imm =
                getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Phone connected नहीं है",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
