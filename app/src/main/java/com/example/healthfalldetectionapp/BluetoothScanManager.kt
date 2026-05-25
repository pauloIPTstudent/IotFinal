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
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
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

            // 💥 FILTRO: Só processa se o nome começar exatamente com "ESP32_CONFIG"
            if (name.startsWith("ESP32_CONFIG", ignoreCase = true)) {
                val address = device.address
                val rssi = result.rssi

                val scanned = ScannedDevice(name, address, rssi)

                Log.i("BLE_SCAN", "🎯 ESP32 Alvo encontrado: Nome: $name | MAC: $address | Sinal: $rssi dBm")

                _scannedDevices.update { currentList ->
                    if (currentList.none { it.macAddress == address }) {
                        currentList + scanned
                    } else {
                        currentList.map { if (it.macAddress == address) scanned else it }
                    }
                }
            } else {
                // Log opcional caso queira ver no Logcat o que o app está descartando
                // Log.d("BLE_SCAN_FILTER", "Ignorado: $name")
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
                    gatt?.requestMtu(512)
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
                Log.i("BLE_CONN", "Serviços do ESP32 descobertos! Pronto para trocar dados.")
                // É aqui que futuramente você vai ativar a leitura dos sensores de queda
                gatt?.device?.address?.let { mac ->
                    salvarDispositivoConhecido(mac)
                }

                sendCredentialsToESP32("MEO-E020E0", "7368bc98ad")
                Handler(Looper.getMainLooper()).postDelayed({
                    gatt?.let { ativarNotificacaoQueda(it) }
                }, 500)
            }
        }
        @SuppressLint("MissingPermission")
        fun ativarNotificacaoQueda(gatt: BluetoothGatt) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHARACTERISTIC_STATUS_UUID)

            if (characteristic != null) {
                // 1. Avisa o sistema Android local para aceitar notificações desta característica
                gatt.setCharacteristicNotification(characteristic, true)

                // 2. Escreve no descritor (CCCD) remoto no ESP32 habilitando as notificações por hardware
                val descriptor = characteristic.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.i("BLE_CONN", "Inscrição nas notificações de queda realizada!")
                }
            } else {
                Log.e("BLE_CONN", "Característica de status não encontrada para ativar notificações.")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Subtraímos 3 bytes obrigatoriamente (são os bytes de cabeçalho do protocolo ATT)
                tamanhoBlocoMaximo = mtu - 3
                Log.i("BLE", "MTU atualizado para $mtu. Novo tamanho máximo de bloco de dados: $tamanhoBlocoMaximo")
            }
        }
        //Handle de notificações do esp32 para a aplicação mobile
        //implementar mqtt publisher nas funções (onCharacteristicChanged)
        //Aqui!!!!
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHARACTERISTIC_STATUS_UUID) {
                processarQueda(value)
            }
        }

        // Versão de compatibilidade para celulares antigos
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == CHARACTERISTIC_STATUS_UUID) {
                val value = characteristic.value
                processarQueda(value)
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

    /*@SuppressLint("MissingPermission")
    fun tryAutoConnect() {
        val macSalvo = getDispositivoConhecido()

        // Se não tem nenhum dispositivo salvo, não faz nada (espera o usuário escanear a primeira vez)
        if (macSalvo.isNullOrBlank()) {
            Log.i("AUTO_CONN", "Nenhum dispositivo salvo anteriormente.")
            return
        }

        // Se já estiver conectado ou tentando conectar, ignora para não duplicar
        if (isGattConnected() || _connectionState.value == "Conectando...") {
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || !hasPermissions()) {
            Log.w("AUTO_CONN", "Condições de hardware ou permissões não atendidas para auto-conexão.")
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(macSalvo)
            Log.w("AUTO_CONN", "Dispositivo conhecido encontrado [${device.name ?: "ESP32"}]. Agendando conexão de background...")
            _connectionState.value = "Conectando..."

            // O SEGREDO ESTÁ AQUI: Passar 'true' no parâmetro autoConnect.
            // Isso diz ao Android: "Fique de olho nesse MAC. Assim que ele aparecer, conecte por mim."
            bluetoothGatt = device.connectGatt(appContext, true, gattCallback)

        } catch (e: Exception) {
            Log.e("AUTO_CONN", "Falha ao tentar agendar auto-conexão", e)
        }
    }*/



    /*
    * Percistencia de dados
    * */
    private val sharedPreferences = appContext.getSharedPreferences("HealthPrefs", Context.MODE_PRIVATE)

    // Salva o MAC do ESP32 quando uma conexão bem-sucedida acontecer
    private fun salvarDispositivoConhecido(macAddress: String) {
        sharedPreferences.edit().putString("LAST_CONNECTED_MAC", macAddress).apply()
    }

    // Busca o MAC salvo (retorna nulo se o app acabou de ser instalado)
    fun getDispositivoConhecido(): String? {
        return sharedPreferences.getString("LAST_CONNECTED_MAC", null)
    }

    // Caso o usuário queira desvincular o sensor manualmente
    fun esquecerDispositivoConhecido() {
        sharedPreferences.edit().remove("LAST_CONNECTED_MAC").apply()
        disconnectDevice()
    }




// ----------- GATT Services -----------

    // UUID do Serviço
    val SERVICE_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420000")
    val CHARACTERISTIC_TRIGGER_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420003")

    @SuppressLint("MissingPermission")
    fun sendTriggerToESP32(comando: String) {
        if (bluetoothGatt == null) {
            Log.e("BLE", "Erro: BluetoothGatt é nulo. Certifique-se de estar conectado.")
            return
        }

        // 1. Encontra o Serviço no ESP32
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        if (service == null) {
            Log.e("BLE", "Serviço não encontrado no ESP32.")
            return
        }

        // 2. Encontra a Característica Trigger dentro do serviço
        val characteristic = service.getCharacteristic(CHARACTERISTIC_TRIGGER_UUID)
        if (characteristic == null) {
            Log.e("BLE", "Característica de Trigger não encontrada.")
            return
        }

        // 3. Converte o texto do comando para Array de Bytes
        val bytesDoComando = comando.toByteArray(Charsets.UTF_8)

        // 4. Define o tipo de escrita como WRITE (combina com o BLE_GATT_CHR_F_WRITE do ESP32)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        // 5. Executa a escrita de forma segura dependendo da versão do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Código para Android 13 ou superior
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                bytesDoComando,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            // Código para Android 12 ou inferior (Depreciado nas versões novas, mas obrigatório nas antigas)
            @Suppress("DEPRECATION")
            characteristic.value = bytesDoComando
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }

        Log.i("BLE", "Comando '$comando' enviado para o ESP32!")
    }

    //wifi credencias
    val CHARACTERISTIC_CREDENTIALS_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420001")

    @SuppressLint("MissingPermission")
    fun sendCredentialsToESP32(ssid: String, password: String) {
        if (bluetoothGatt == null) {
            Log.e("BLE", "Erro: BluetoothGatt é nulo. Certifique-se de estar conectado.")
            return
        }

        // 1. Encontra o Serviço no ESP32
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        if (service == null) {
            Log.e("BLE", "Serviço não encontrado no ESP32.")
            return
        }

        // 2. Encontra a Característica de Credenciais (final 0001)
        val characteristic = service.getCharacteristic(CHARACTERISTIC_CREDENTIALS_UUID)
        if (characteristic == null) {
            Log.e("BLE", "Característica de Credenciais não encontrada.")
            return
        }

        // 3. Cria o objeto JSON exatamente como o ESP32 espera
        val jsonCredenciais = JSONObject()
        try {
            jsonCredenciais.put("ssid", ssid)
            jsonCredenciais.put("password", password)
        } catch (e: Exception) {
            Log.e("BLE", "Erro ao criar o JSON: ${e.message}")
            return
        }

        // Converte o JSON para String (Fica no formato: {"ssid":"Nome","password":"Senha"})
        val jsonString = jsonCredenciais.toString()

        // 4. Converte o texto do JSON para Array de Bytes
        val bytesDoComando = jsonString.toByteArray(Charsets.UTF_8)

        // 5. Define o tipo de escrita
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        // 6. Executa a escrita respeitando a versão do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                bytesDoComando,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytesDoComando
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }

        Log.i("BLE", "Credenciais enviadas com sucesso!")
    }

    // status callback
    val CHARACTERISTIC_STATUS_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420002")
    // O UUID do descritor padrão do Bluetooth SIG para notificações é universal:
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    fun processarQueda(value: ByteArray) {
        if (value.isNotEmpty()) {
            val codigoAlerta = value[0].toInt()
            if (codigoAlerta == 1) {
                Log.e("ALERTA_QUEDA", "🚨 ALERTA: Uma queda foi detectada pelo ESP32!")
                // Chame sua interface gráfica ou serviço de alarme aqui
                quedaListener?.onQuedaDetectada()
            }
        }
    }
    var tamanhoBlocoMaximo: Int = 20

    /*    // Definição dos UUIDs do Loader (ajustados para bater com o seu código C anterior)
    val LOADER_SERVICE_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420000")
    val CHARACTERISTIC_DADOS_UUID: UUID = UUID.fromString("1d43338e-de1a-47ec-89af-5544fc420001")

    // Variável para armazenar o tamanho máximo do bloco que o Android descobriu no MTU
// O padrão inicial seguro do BLE é 20 bytes (23 MTU - 3 bytes de cabeçalho)

    private var otaLatch: CountDownLatch? = null

    @SuppressLint("MissingPermission")
    fun sendFileToESP32(fileBytes: ByteArray, onProgresso: (porcentagem: Int) -> Unit) {
        if (bluetoothGatt == null) {
            Log.e("BLE_OTA", "Erro: BluetoothGatt é nulo. Certifique-se de estar conectado.")
            return
        }

        val service = bluetoothGatt?.getService(LOADER_SERVICE_UUID)
        if (service == null) {
            Log.e("BLE_OTA", "Serviço do Loader não encontrado no ESP32.")
            return
        }

        val characteristic = service.getCharacteristic(CHARACTERISTIC_DADOS_UUID)
        if (characteristic == null) {
            Log.e("BLE_OTA", "Característica de dados não encontrada.")
            return
        }

        // Configura para escrita síncrona com confirmação de entrega física (ACK)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        Thread {
            try {
                val totalBytes = fileBytes.size
                var bytesEnviados = 0

                Log.i("BLE_OTA", "Iniciando envio ASSINCRONO CONTROLADO de $totalBytes bytes em blocos de $tamanhoBlocoMaximo bytes...")

                while (bytesEnviados < totalBytes) {
                    val tamanhoAtual = Math.min(tamanhoBlocoMaximo, totalBytes - bytesEnviados)
                    val bloco = ByteArray(tamanhoAtual)

                    System.arraycopy(fileBytes, bytesEnviados, bloco, 0, tamanhoAtual)

                    // 1. Inicializa o Latch com o contador em 1 (uma tranca)
                    otaLatch = CountDownLatch(1)

                    // 2. Dispara a escrita física
                    val sucessoEscrita = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val codigoRetorno = bluetoothGatt?.writeCharacteristic(
                            characteristic,
                            bloco,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                        codigoRetorno == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = bloco
                        @Suppress("DEPRECATION")
                        bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                    }

                    if (!sucessoEscrita) {
                        Log.e("BLE_OTA", "Falha crítica: A pilha do Android rejeitou o envio do bloco!")
                        return@Thread
                    }

                    // 3. BLOQUEIO INTELIGENTE: A Thread dorme aqui até o onCharacteristicWrite dar o sinal.
                    // Coloquei um Timeout seguro de 1 segundo. Se o ESP32 sumir, a app não fica travada para sempre.
                    val recebidoPeloEsp32 = otaLatch?.await(5000, TimeUnit.MILLISECONDS) ?: false

                    if (!recebidoPeloEsp32) {
                        Log.e("BLE_OTA", "Timeout! O ESP32 ou o rádio falharam em responder o pacote nos 1052 bytes. Abortando.")
                        return@Thread
                    }

                    // 4. Se chegou aqui, o pacote foi ENTREGUE de verdade! Atualizamos o contador.
                    bytesEnviados += tamanhoAtual

                    val progresso = ((bytesEnviados.toFloat() / totalBytes.toFloat()) * 100).toInt()
                    onProgresso(progresso)

                    // Pequena folga milimétrica de segurança entre loops para o escalonador do sistema
                    Thread.sleep(10)
                }

                Log.i("BLE_OTA", "Arquivo TOTALMENTE enviado e processado pelo hardware! A aguardar gravação final...")
                Thread.sleep(2000)

                Log.i("BLE_OTA", "A desconectar de forma limpa...")
                bluetoothGatt?.disconnect()

            } catch (e: Exception) {
                Log.e("BLE_OTA", "Erro durante a transmissão do arquivo: ${e.message}")
            } finally {
                otaLatch = null
            }
        }.start()
    }
*/
    // warning dialog
    // Interface para comunicação externa
    interface OnQuedaDetectadaListener {
        fun onQuedaDetectada()
    }

    // Variável volátil para guardar quem está a ouvir o alerta atualmente
    private var quedaListener: OnQuedaDetectadaListener? = null

    // MÉTODO NOVO: Permite que a Atividade se registe para ouvir os alertas
    fun setOnQuedaDetectadaListener(listener: OnQuedaDetectadaListener?) {
        this.quedaListener = listener
    }


    companion object {
        @Volatile
        private var INSTANCE: BluetoothScanManager? = null

        fun getInstance(context: Context): BluetoothScanManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothScanManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
