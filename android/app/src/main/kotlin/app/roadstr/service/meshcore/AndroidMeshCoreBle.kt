package app.roadstr.service.meshcore

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol as Protocol
import java.util.UUID

/**
 * Bluetooth LE access for the MeshCore support. Platform APIs only (no Play services).
 *
 * NOT validated on hardware: this file has been compiled and reviewed against the Android and
 * MeshCore documentation, but no MeshCore radio was available to run it against. The protocol
 * logic above it is covered by tests with a simulated radio; this adapter is not.
 */
object MeshCoreBlePermissions {
    /** What must be granted at runtime to scan for and talk to a radio, on this Android version. */
    fun required(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Before Android 12 a scan is a location operation. The legacy BLUETOOTH permissions are
        // install-time only.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun granted(context: Context): Boolean = required().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}

/** A radio seen while scanning. */
data class MeshCoreScanResult(val name: String, val address: String, val rssi: Int)

/** Finds radios that advertise the companion service. Results are named, not addressed, in the UI. */
@SuppressLint("MissingPermission") // checked in start(), before any scan call
class AndroidMeshCoreScanner(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private var stopTask: Runnable? = null

    /**
     * Scans for [SCAN_MILLIS], calling [onFound] once per radio and [onEnd] when it is over
     * (null: normally; otherwise why it could not run).
     */
    fun start(onFound: (MeshCoreScanResult) -> Unit, onEnd: (MeshCoreLinkFailure?) -> Unit) {
        stop()
        if (!MeshCoreBlePermissions.granted(context)) return onEnd(MeshCoreLinkFailure.PermissionMissing)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf(BluetoothAdapter::isEnabled)?.bluetoothLeScanner
            ?: return onEnd(MeshCoreLinkFailure.BluetoothOff)

        val seen = HashSet<String>()
        val scan = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address ?: return
                if (!seen.add(address)) return
                val name = result.scanRecord?.deviceName ?: result.device.name ?: address
                onFound(MeshCoreScanResult(name, address, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                finish(onEnd, MeshCoreLinkFailure.ConnectionFailed)
            }
        }
        callback = scan
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Protocol.SERVICE_UUID))).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        scanner.startScan(listOf(filter), settings, scan)
        stopTask = Runnable { finish(onEnd, null) }.also { handler.postDelayed(it, SCAN_MILLIS) }
    }

    fun stop() {
        stopTask?.let(handler::removeCallbacks)
        stopTask = null
        val active = callback ?: return
        callback = null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (MeshCoreBlePermissions.granted(context)) runCatching { adapter?.bluetoothLeScanner?.stopScan(active) }
    }

    private fun finish(onEnd: (MeshCoreLinkFailure?) -> Unit, failure: MeshCoreLinkFailure?) {
        if (callback == null) return
        stop()
        onEnd(failure)
    }

    private companion object {
        const val SCAN_MILLIS = 10_000L
    }
}

/**
 * One GATT connection to one radio: the Nordic UART service of the companion protocol. Writes are
 * strictly one at a time (Android allows a single GATT operation in flight) and every
 * notification is handed up as one frame.
 */
@SuppressLint("MissingPermission") // checked in open(); a permission revoked later ends the link
class AndroidMeshCoreBleLink(
    private val context: Context,
    private val address: () -> String?,
) : MeshCoreLink {
    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var listener: MeshCoreLink.Listener? = null
    private val pending = ArrayDeque<ByteArray>()
    private var writing = false
    private var generation = 0

    override fun open(listener: MeshCoreLink.Listener) {
        close()
        val attempt: Int
        synchronized(lock) {
            attempt = ++generation
            this.listener = listener
        }
        if (!MeshCoreBlePermissions.granted(context)) return listener.onDisconnected(MeshCoreLinkFailure.PermissionMissing)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return listener.onDisconnected(MeshCoreLinkFailure.BluetoothOff)
        val device: BluetoothDevice = address()?.let { runCatching { adapter.getRemoteDevice(it) }.getOrNull() }
            ?: return listener.onDisconnected(MeshCoreLinkFailure.DeviceNotFound)
        val connection = device.connectGatt(context, false, callback(attempt), BluetoothDevice.TRANSPORT_LE)
        synchronized(lock) { if (attempt == generation) gatt = connection else connection?.close() }
    }

    override fun write(frame: ByteArray): Boolean {
        synchronized(lock) {
            if (gatt == null || rx == null || pending.size >= MAX_PENDING_WRITES) return false
            pending.addLast(frame.copyOf())
            if (!writing) writeNextLocked()
            return true
        }
    }

    override fun close() {
        val old: BluetoothGatt?
        synchronized(lock) {
            generation++
            old = gatt
            gatt = null
            rx = null
            pending.clear()
            writing = false
            listener = null
        }
        runCatching {
            old?.disconnect()
            old?.close()
        }
    }

    private fun writeNextLocked() {
        val connection = gatt ?: return
        val characteristic = rx ?: return
        val frame = pending.removeFirstOrNull() ?: return
        writing = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connection.writeCharacteristic(characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = frame
                connection.writeCharacteristic(characteristic)
            }
        }
        if (!started) {
            writing = false
            pending.clear()
            val failed = listener
            fail(failed, MeshCoreLinkFailure.ConnectionFailed)
        }
    }

    private fun fail(target: MeshCoreLink.Listener?, failure: MeshCoreLinkFailure) {
        Handler(Looper.getMainLooper()).post { target?.onDisconnected(failure) }
    }

    private fun callback(attempt: Int) = object : BluetoothGattCallback() {
        private fun current(): MeshCoreLink.Listener? = synchronized(lock) { if (attempt == generation) listener else null }

        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            val target = current() ?: return
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                    // Larger frames than the 20-byte default payload: ask for the biggest MTU.
                    if (!connection.requestMtu(DESIRED_MTU)) connection.discoverServices()
                else -> {
                    synchronized(lock) { if (attempt == generation) generation++ }
                    runCatching { connection.close() }
                    target.onDisconnected(MeshCoreLinkFailure.ConnectionFailed)
                }
            }
        }

        override fun onMtuChanged(connection: BluetoothGatt, mtu: Int, status: Int) {
            if (current() != null) connection.discoverServices()
        }

        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            val target = current() ?: return
            val service = connection.getService(UUID.fromString(Protocol.SERVICE_UUID))
            val tx = service?.getCharacteristic(UUID.fromString(Protocol.TX_CHARACTERISTIC_UUID))
            val receive = service?.getCharacteristic(UUID.fromString(Protocol.RX_CHARACTERISTIC_UUID))
            if (status != BluetoothGatt.GATT_SUCCESS || tx == null || receive == null) {
                return target.onDisconnected(MeshCoreLinkFailure.ServiceMissing)
            }
            synchronized(lock) { rx = receive }
            enableNotifications(connection, tx, target)
        }

        override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val target = current() ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) target.onConnected() else target.onDisconnected(MeshCoreLinkFailure.ConnectionFailed)
        }

        override fun onCharacteristicWrite(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val target = current() ?: return
            synchronized(lock) {
                writing = false
                if (status == BluetoothGatt.GATT_SUCCESS) writeNextLocked() else pending.clear()
            }
            if (status != BluetoothGatt.GATT_SUCCESS) target.onDisconnected(MeshCoreLinkFailure.ConnectionFailed)
        }

        // API 33+ delivers the value directly; earlier versions read it from the characteristic.
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            current()?.onFrame(value.copyOf())
        }

        @Deprecated("Used below API 33")
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { current()?.onFrame(it.copyOf()) }
        }
    }

    private fun enableNotifications(connection: BluetoothGatt, tx: BluetoothGattCharacteristic, target: MeshCoreLink.Listener) {
        val descriptor = tx.getDescriptor(CLIENT_CONFIG_UUID)
        if (descriptor == null || !connection.setCharacteristicNotification(tx, true)) {
            return target.onDisconnected(MeshCoreLinkFailure.ServiceMissing)
        }
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connection.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                connection.writeDescriptor(descriptor)
            }
        }
        if (!started) target.onDisconnected(MeshCoreLinkFailure.ConnectionFailed)
    }

    private companion object {
        const val DESIRED_MTU = 247
        const val MAX_PENDING_WRITES = 16
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
