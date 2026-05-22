package com.example.healthfalldetectionapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout

class DevicesFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // 1. Encontra de forma segura o botão "Conectar" dentro do Card de Status superior
        if(scanManager.isGattConnected()){
            popupConnect.visibility = View.GONE
            update_card.visibility = View.VISIBLE
        }
        else{
            popupConnect.visibility = View.VISIBLE
            update_card.visibility = View.GONE
        }
    }
}