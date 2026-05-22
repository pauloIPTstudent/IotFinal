package com.example.healthfalldetectionapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Importante para o funcionamento do Osmdroid (carregar configurações de cache)
        val ctx = requireContext().applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        val view = inflater.inflate(R.layout.fragment_map, container, false)

        // Inicializa o mapa open-source
        map = view.findViewById(R.id.map)
        map.setMultiTouchControls(true) // Permite pinça para zoom

        // Verifica permissões para ativar a localização
        checkLocationPermission()

        return view
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupLocationOverlay()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1002
            )
        }
    }

    private fun setupLocationOverlay() {
        // Cria a camada que desenha a sua posição atual no mapa (Bolinha azul com bússola)
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
        locationOverlay.enableMyLocation() // Ativa o ponto de localização
        locationOverlay.enableFollowLocation() // Faz a câmera seguir você automaticamente

        // Adiciona a camada ao mapa
        map.overlays.add(locationOverlay)

        // Define um zoom inicial padrão
        val mapController = map.controller
        mapController.setZoom(17.0)

        // Caso queira focar em uma coordenada padrão inicial:
        // val startPoint = GeoPoint(-23.550520, -46.633309)
        // mapController.setCenter(startPoint)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupLocationOverlay()
        } else {
            Toast.makeText(context, "Permissão de localização necessária.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume() // Necessário para o ciclo de vida do Osmdroid
    }

    override fun onPause() {
        super.onPause()
        map.onPause() // Necessário para o ciclo de vida do Osmdroid
    }
}