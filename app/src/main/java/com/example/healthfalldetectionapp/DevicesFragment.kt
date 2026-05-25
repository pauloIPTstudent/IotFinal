package com.example.healthfalldetectionapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import android.content.Context
import android.util.Log
import java.io.IOException

class DevicesFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // Função para ler o arquivo .bin da pasta assets
    fun lerArquivoBinarioDosAssets(context: Context, nomeDoArquivo: String): ByteArray? {
        return try {
            // Abre o arquivo e lê todos os bytes de uma vez
            context.assets.open(nomeDoArquivo).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null // Retorna null se houver erro (ex: arquivo não encontrado)
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val scanManager = BluetoothScanManager.getInstance(requireContext())
        val popupConnect = view.findViewById<ConstraintLayout>(R.id.popup_concect)
        val update_card = view.findViewById<ConstraintLayout>(R.id.update_card)
        val btn_update_now = view.findViewById<Button>(R.id.btn_update_now)

        // 1. Encontra de forma segura o botão "Conectar" dentro do Card de Status superior
        if(scanManager.isGattConnected()){
            popupConnect.visibility = View.GONE
            update_card.visibility = View.VISIBLE
        }
        else{
            popupConnect.visibility = View.GONE
            update_card.visibility = View.VISIBLE
        }

        btn_update_now.setOnClickListener {
            val meusBytesDoFicheiro: ByteArray? = lerArquivoBinarioDosAssets(requireContext(), "firmware.bin")

            if (meusBytesDoFicheiro != null) {
                // ESSE LOG AQUI É A CHAVE: Quantos bytes aparecem no seu Logcat?
                Log.d("FIRMWARE_UPDATE", "TAMANHO REAL NO ANDROID: ${meusBytesDoFicheiro.size} bytes.")

                /*scanManager.sendFileToESP32(meusBytesDoFicheiro) { porcentagem ->
                    Log.d("FIRMWARE_UPDATE", "Progresso: $porcentagem%")
                }*/
            } else {
                Log.e("FIRMWARE_UPDATE", "Erro ao ler arquivo.")
            }
        }


    }
}