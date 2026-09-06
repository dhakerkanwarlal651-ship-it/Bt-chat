package com.example.btchat
import android.app.AlertDialog
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var statusText: TextView
    private lateinit var chatText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val appUuid =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        const val REQUEST_PERMISSION = 100
        const val REQUEST_ENABLE_BT = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        createInterface()

        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth is not supported"
            return
        }

        requestBluetoothPermissions()
    }

    private fun createInterface() {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(25, 25, 25, 25)

        statusText = TextView(this)
        statusText.text = "BT Chat - Starting..."
        statusText.textSize = 20f

        val enableButton = Button(this)
        enableButton.text = "Turn Bluetooth ON"

        val discoverButton = Button(this)
        discoverButton.text = "Make Device Discoverable"

        val hostButton = Button(this)
        hostButton.text = "Wait for Connection"

        val connectButton = Button(this)
        connectButton.text = "Connect to Paired Device"

        chatText = TextView(this)
        chatText.text = "Chat:\n"
        chatText.textSize = 18f

        messageInput = EditText(this)
        messageInput.hint = "Type message..."

        sendButton = Button(this)
        sendButton.text = "SEND"

        layout.addView(statusText)
        layout.addView(enableButton)
        layout.addView(discoverButton)
        layout.addView(hostButton)
        layout.addView(connectButton)
        layout.addView(chatText)
        layout.addView(messageInput)
        layout.addView(sendButton)

        setContentView(layout)

        enableButton.setOnClickListener {
            enableBluetooth()
        }

        discoverButton.setOnClickListener {
            makeDiscoverable()
        }

        hostButton.setOnClickListener {
            startServer()
        }

        connectButton.setOnClickListener {
            showPairedDevices()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun requestBluetoothPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ),
                REQUEST_PERMISSION
            )
        }
    }

    private fun enableBluetooth() {

        if (!hasBluetoothPermission()) return

        if (!bluetoothAdapter.isEnabled) {

            val intent =
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            startActivityForResult(
                intent,
                REQUEST_ENABLE_BT
            )

        } else {

            statusText.text = "Bluetooth is ON"
        }
    }

    private fun makeDiscoverable() {

        if (!hasBluetoothPermission()) return

        val intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)

        intent.putExtra(
            BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
            300
        )

        startActivity(intent)

        statusText.text =
            "Device is discoverable for 5 minutes"
    }

    private fun startServer() {

        if (!hasBluetoothPermission()) return

        Thread {

            try {

                val serverSocket: BluetoothServerSocket =
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        "BT Chat",
                        appUuid
                    )

                runOnUiThread {
                    statusText.text =
                        "Waiting for another phone..."
                }

                val connectedSocket =
                    serverSocket.accept()

                socket = connectedSocket
                outputStream = connectedSocket.outputStream

                serverSocket.close()

                runOnUiThread {
                    statusText.text = "Connected!"
                }

                listenForMessages(connectedSocket)

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Connection error: ${e.message}"
                }
            }

        }.start()
    }

    private fun showPairedDevices() {

        if (!hasBluetoothPermission()) return

        val devices =
            bluetoothAdapter.bondedDevices.toList()

        if (devices.isEmpty()) {

            Toast.makeText(
                this,
                "First pair another phone from Bluetooth settings",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val names =
            devices.map {
                "${it.name ?: "Unknown"}\n${it.address}"
            }

        AlertDialog.Builder(this)
            .setTitle("Select Device")
            .setItems(names.toTypedArray()) { _, which ->

                connectToDevice(devices[which])
            }
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {

        Thread {

            try {

                runOnUiThread {
                    statusText.text =
                        "Connecting to ${device.name}..."
                }

                val newSocket =
                    device.createRfcommSocketToServiceRecord(appUuid)

                bluetoothAdapter.cancelDiscovery()

                newSocket.connect()

                socket = newSocket
                outputStream = newSocket.outputStream

                runOnUiThread {
                    statusText.text = "Connected!"
                }

                listenForMessages(newSocket)

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text =
                        "Connection failed: ${e.message}"
                }
            }

        }.start()
    }

    private fun listenForMessages(
        bluetoothSocket: BluetoothSocket
    ) {

        Thread {

            try {

                val inputStream: InputStream =
                    bluetoothSocket.inputStream

                val buffer = ByteArray(1024)

                while (true) {

                    val count =
                        inputStream.read(buffer)

                    if (count > 0) {

                        val message =
                            String(buffer, 0, count)

                        runOnUiThread {

                            chatText.append(
                                "\nFriend: $message"
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    statusText.text = "Disconnected"
                }
            }

        }.start()
    }

    private fun sendMessage() {

        val message =
            messageInput.text.toString().trim()

        if (message.isEmpty()) return

        try {

            outputStream?.write(
                message.toByteArray()
            )

            chatText.append(
                "\nYou: $message"
            )

            messageInput.text.clear()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Not connected",
                Toast.LENGTH_SHORT
            ).show()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_PERMISSION) {

            if (grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {

                statusText.text =
                    "Bluetooth permissions granted"

            } else {

                statusText.text =
                    "Bluetooth permissions required"
            }
        }
    }
}
