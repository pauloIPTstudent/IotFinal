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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragmento que representa o ecrã principal com o Botão Vermelho de Emergência ("PEDIR AJUDA").
 */
class RedBottonFragment : Fragment() {
    private val TAG = "BLE_SCAN_LOG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val scanManager = BluetoothScanManager.getInstance(requireContext())
        // 2. Garante que o botão existe no layout antes de definir o evento de clique

        btnConectar?.setOnClickListener {
            showDiscoverDevicesDialog(scanManager);
        }
    }

    data class StaticDevice(val id: String, val name: String)
    /**
     * Cria e exibe de forma segura o BottomSheetDialog para busca de dispositivos
     */
    private fun showDiscoverDevicesDialog(scan:BluetoothScanManager) {
        // Verifica se o fragmento está atualmente associado a um contexto ativo
        val context = context ?: return
        scan.startScan()
        // CORREÇÃO: Inicialização limpa e segura do BottomSheetDialog.
        // Isto herda as definições do Material Design configuradas no teu themes.xml,
        // evitando erros ao aceder diretamente a referências internas de pacotes.
        val dialog = BottomSheetDialog(context)
        // Inicia o scan ao abrir o diálogo
        dialog.setOnDismissListener {
            scan.stopScan()
        }

        // Inicia o scan após o diálogo estar pronto
        // Inflar o layout do diálogo de forma segura
        val inflater = LayoutInflater.from(context)
        val sheetView = inflater.inflate(R.layout.dialog_discover_devices, null)

        dialog.setContentView(sheetView)
        // 2. Encontra o contentor onde vamos colocar os itens
        val layoutDeviceList = sheetView.findViewById<LinearLayout>(R.id.layout_device_list)
        val scopeJob = viewLifecycleOwner.lifecycleScope.launch {
            // collectLatest garante que se chegar uma lista nova enquanto processa a antiga,
            // ele cancela a antiga e foca-se na mais recente.
            scan.scannedDevices.collectLatest { devices ->

                // 1. Limpa os itens antigos do ecrã para não duplicar
                layoutDeviceList?.removeAllViews()

                // 2. Se a lista estiver vazia, podes opcionalmente mostrar um aviso de "A procurar..."
                if (devices.isEmpty()) {
                    // Exemplo: mostrar um TextView de "Nenhum dispositivo encontrado"
                    return@collectLatest
                }

                // 3. Adiciona cada dispositivo descoberto ao ecrã
                devices.forEach { device ->
                    val itemView = inflater.inflate(R.layout.item_device, layoutDeviceList, false)

                    val txtName = itemView.findViewById<TextView>(R.id.txt_device_name)
                    // Se o dispositivo não tiver nome, mostra um fallback (comum em BLE)
                    txtName.text = device.name.ifBlank { "Dispositivo Desconhecido" }

                    // Configura o clique real com base no ID/MAC do dispositivo
                    itemView.setOnClickListener {
                        // Podes passar o 'device' completo para a tua função de conexão
                        scan.connectToDevice(device.macAddress)
                        scan.stopScan()
                        dialog.dismiss() // Fecha o Bottom Sheet após o clique
                    }

                    // Injeta o elemento visual no contentor
                    layoutDeviceList?.addView(itemView)
                }
            }
        }

        // Cancelar a escuta do Flow se o diálogo for fechado pelo utilizador
        dialog.setOnDismissListener {
            scopeJob.cancel()
            scan.stopScan()
        }
        dialog.show()
    }
}