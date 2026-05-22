package com.example.healthfalldetectionapp


import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
data class ScannedDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int
)

class BluetoothScanManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasHardwareBluetooth = MutableStateFlow(bluetoothAdapter != null)
    val hasHardwareBluetooth: StateFlow<Boolean> = _hasHardwareBluetooth.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Dispositivo Desconhecido"
            val address = device.address
            val rssi = result.rssi

            val scanned = ScannedDevice(name, address, rssi)

            Log.i("BLE_SCAN", "REAL encontrado: Nome: $name | MAC: $address | Sinal: $rssi dBm")
            _scannedDevices.update { currentList ->
                if (currentList.none { it.macAddress == address }) {
                    currentList + scanned
                } else {
                    currentList.map { if (it.macAddress == address) scanned else it }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            startSimulatedScan()
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            startSimulatedScan()
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true
        try {
            scanner.startScan(scanCallback)
        } catch (e: Exception) {
            startSimulatedScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        if (hasPermissions() && bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        stopSimulatedScan()
    }

    // Simulated scan flow for emulator tests
    private var isSimulating = false
    private var simThread: Thread? = null

    private fun startSimulatedScan() {
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        isSimulating = true
        simThread = Thread {
            val mockDevices = listOf(
                ScannedDevice("Pulseira Ativa SOS Tracker v4", "D8:A0:1D:9B:F4:55", -52),
                ScannedDevice("Sensor Corporal de Queda Pro", "C1:42:3B:56:88:99", -64),
                ScannedDevice("Smartwatch Sénior Care", "F3:A1:AC:4E:99:11", -78),
                ScannedDevice("Medidor de Frequência Cardíaca", "E4:88:12:F3:1D:20", -80)
            )
            try {
                var index = 0
                while (isSimulating && index < mockDevices.size) {
                    Thread.sleep(800)
                    val dev = mockDevices[index]
                    Log.i("BLE_SCAN", "SIMULADO encontrado: Nome: ${dev.name} | MAC: ${dev.macAddress}")
                    _scannedDevices.update { it + dev }
                    index++
                }
            } catch (e: InterruptedException) {
                // Interrupted
            }
        }
        simThread?.start()
    }

    private fun stopSimulatedScan() {
        isSimulating = false
        simThread?.interrupt()
        simThread = null
    }
    /**
     * Conectar com esp32 e consumir gatt ble api's
     * */

    private var bluetoothGatt: android.bluetooth.BluetoothGatt? = null

    private val _connectionState = MutableStateFlow("Desconectado")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: android.bluetooth.BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            val deviceAddress = gatt?.device?.address ?: ""
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BLE_CONN", "Conectado com sucesso ao dispositivo: $deviceAddress")
                    _connectionState.value = "Conectado"

                    // OBRIGATÓRIO: Descobrir os serviços do ESP32 para conseguir ler/receber dados depois
                    gatt?.discoverServices()

                } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BLE_CONN", "Dispositivo desconectado: $deviceAddress")
                    _connectionState.value = "Desconectado"
                    gatt?.close()
                    bluetoothGatt = null
                }
            } else {
                Log.e("BLE_CONN", "Erro na conexão GATT. Status erro: $status. Fechando conexão.")
                _connectionState.value = "Erro na Conexão"
                gatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt?, status: Int) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                Log.w("BLE_CONN", "Serviços do ESP32 descobertos! Pronto para trocar dados.")
                // É aqui que futuramente você vai ativar a leitura dos sensores de queda
            }
        }
    }

    fun isGattConnected(): Boolean {
        return bluetoothGatt != null
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(macAddress: String) {
        // 1. Se já estiver tentando escanear, o ideal é parar para economizar bateria e chip de rádio
        stopScan()

        // 2. Validações de segurança antes de tentar a conexão
        if (!hasPermissions()) {
            Log.e("BLE_CONN", "Impossível conectar: Falta permissões de Bluetooth")
            _connectionState.value = "Erro: Falta Permissão"
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BLE_CONN", "Impossível conectar: Adaptador Bluetooth desligado")
            _connectionState.value = "Bluetooth Desligado"
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            Log.w("BLE_CONN", "Tentando conectar a: ${device.name ?: "Dispositivo"} [$macAddress]...")
            _connectionState.value = "Conectando..."

            // 3. Efetua a conexão gatt
            // 'false' significa conexão direta imediata (não espera o dispositivo aparecer se ele sumir)
            bluetoothGatt = device.connectGatt(appContext, false, gattCallback)

        } catch (e: IllegalArgumentException) {
            Log.e("BLE_CONN", "Endereço MAC inválido: $macAddress")
            _connectionState.value = "Erro: MAC Inválido"
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        if (bluetoothGatt != null) {
            Log.w("BLE_CONN", "Forçando desconexão manual...")
            bluetoothGatt?.disconnect()
        }
    }
    companion object {
        @Volatile
        private var INSTANCE: BluetoothScanManager? = null

        /**
         * Método principal para pegar a instância única do gerenciador de Bluetooth.
         */
        fun getInstance(context: Context): BluetoothScanManager {
            // Se já existir uma instância, retorna ela direto (performance rápida)
            return INSTANCE ?: synchronized(this) {
                // Se não existir, entra no bloco sincronizado e cria de forma segura
                INSTANCE ?: BluetoothScanManager(context).also { INSTANCE = it }
            }
        }
    }
}
