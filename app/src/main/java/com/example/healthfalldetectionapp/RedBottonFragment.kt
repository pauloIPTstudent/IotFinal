package com.example.healthfalldetectionapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.util.Log
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

// Chaves para os parâmetros de inicialização do Fragmento
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * Fragmento que representa o ecrã principal com o Botão Vermelho de Emergência ("PEDIR AJUDA").
 */
class RedBottonFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    private val TAG = "BLE_SCAN_LOG"

    private var bluetoothGatt: BluetoothGatt? = null

    // Objetos de sistema
    private val bluetoothAdapter: BluetoothAdapter? by lazy {

        val manager = getContext()?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla o layout principal do fragmento correspondente ao botão vermelho
        return inflater.inflate(R.layout.fragment_red_botton, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Encontra de forma segura o botão "Conectar" dentro do Card de Status superior
        val btnConectar = view.findViewById<Button>(R.id.btn_connect_device)

        // 2. Garante que o botão existe no layout antes de definir o evento de clique

        btnConectar?.setOnClickListener {
            checkBluetoothPermissionAndStart();
        }
    }

    /**
     * Cria e exibe de forma segura o BottomSheetDialog para busca de dispositivos
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun showDiscoverDevicesDialog() {
        // Verifica se o fragmento está atualmente associado a um contexto ativo
        val context = context ?: return

        // CORREÇÃO: Inicialização limpa e segura do BottomSheetDialog.
        // Isto herda as definições do Material Design configuradas no teu themes.xml,
        // evitando erros ao aceder diretamente a referências internas de pacotes.
        val dialog = BottomSheetDialog(context)
        // Inicia o scan ao abrir o diálogo
        dialog.setOnDismissListener {
            stopBleScan()
        }

        // Inicia o scan após o diálogo estar pronto
        startBleScan()
        // Inflar o layout do diálogo de forma segura
        val inflater = LayoutInflater.from(context)
        val sheetView = inflater.inflate(R.layout.dialog_discover_devices, null)

        dialog.setContentView(sheetView)

        // Encontra os itens da lista de dispositivos dentro do layout inflado do Bottom Sheet
        val itemVital = sheetView.findViewById<View>(R.id.item_device_vital)
        val itemTeste = sheetView.findViewById<View>(R.id.item_device_test)

        // Configura o clique para simular a conexão na primeira pulseira
        itemVital?.setOnClickListener {
            // TODO: Adicionar lógica para iniciar a conexão Bluetooth real (BLE) aqui
            dialog.dismiss() // Fecha o Bottom Sheet
        }

        // Configura o clique no segundo dispositivo
        itemTeste?.setOnClickListener {
            dialog.dismiss()
        }

        // Exibe o Bottom Sheet no ecrã de forma fluida
        dialog.show()
    }
    private fun hasBluetoothPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }
    /* Função para verificar permissões Bluetooth e iniciar o scan */
    private fun checkBluetoothPermissionAndStart() {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allPermissionsGranted = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            showDiscoverDevicesDialog() // Abre o diálogo apenas se tivermos permissão
        } else {
            requestBluetoothPermissionLauncher.launch(bluetoothPermissions)
        }
    }

    /* Função para solicitar permissões Bluetooth */
// No seu ActivityResultContracts (o launcher das permissões)
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@registerForActivityResult
            }else{
                showDiscoverDevicesDialog() // Abre o diálogo após conceder permissão
            }
        } else {
            Toast.makeText(requireContext(), "Permissão negada. Não podemos buscar o sensor.", Toast.LENGTH_SHORT).show()
        }
    }
    /*
     * Função para solicitar permissões Bluetooth
     */
    @SuppressLint( "MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            //val deviceName = device.name ?: "Desconhecido"
            val deviceName = result.scanRecord?.deviceName ?: device.name ?: "Desconhecido"
            val deviceAddress = device.address
            // Aqui enviamos o dispositivo encontrado para o Dialog através do Listener
            Log.d(TAG, "Dispositivo Encontrado: Nome: $deviceName | MAC: $deviceAddress")
        }
        override fun onScanFailed(errorCode: Int) {
            // LOG: Caso ocorra algum erro no scan
            Log.e(TAG, "Erro no Scan: Código $errorCode")
        }
    }
    /* Função para iniciar o scan Bluetooth */
    @SuppressLint("MissingPermission") // Mantenha esta anotação apenas porque nós mesmos validamos com hasBluetoothPermission()
    private fun startBleScan() {
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "Tentativa de scan sem permissão.")
            return
        }

        Log.d(TAG, "Iniciando o Scan Bluetooth...")
        resetBluetoothState()

        view?.postDelayed({
            // Verificação extra antes de iniciar
            if (hasBluetoothPermission()) {
                bleScanner?.startScan(scanCallback)
            }
        }, 100)
    }


    /*
     * Função para resetar o estado do Bluetooth
     */
    @SuppressLint("MissingPermission")
    private fun resetBluetoothState() {
        try {
            // Verificar permissão específica de conexão
            val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Versões antigas não exigem esta permissão específica
            }

            if (hasBluetoothPermission()) {
                bleScanner?.stopScan(scanCallback)
            }

            if (hasConnectPermission) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
            Log.d(TAG, "Estado do Bluetooth resetado.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de segurança ao resetar: ${e.message}")
        }
    }
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (hasBluetoothPermission()) {
            Log.d(TAG, "Parando o Scan Bluetooth.")
            bleScanner?.stopScan(scanCallback)
        }
    }


    companion object {
        /**
         * Método de fábrica para criar uma nova instância deste fragmento
         */
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            RedBottonFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}