package com.example.healthfalldetectionapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
class MapFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    // Elementos da Interface (UI)
    private lateinit var txtStreet: TextView
    private lateinit var txtCity: TextView
    private lateinit var btnShare: Button

    // Guardas das coordenadas atuais para partilha rápida
    private var lastKnownLatitude: Double = 0.0
    private var lastKnownLongitude: Double = 0.0
    private var currentFullAddress: String = "Localização desconhecida"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val ctx = requireContext().applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        val view = inflater.inflate(R.layout.fragment_map, container, false)

        // 1. Vincula as Views do XML
        map = view.findViewById(R.id.map)
        txtStreet = view.findViewById(R.id.txtStreet)
        txtCity = view.findViewById(R.id.txtCity)
        btnShare = view.findViewById(R.id.btnShare)

        map.setMultiTouchControls(true)

        // 2. Configura a ação do botão Partilhar
        btnShare.setOnClickListener {
            partilharLocalizacao()
        }

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
        // Usamos um provedor customizado para interceptar quando a posição muda
        val provider = GpsMyLocationProvider(requireContext())

        locationOverlay = object : MyLocationNewOverlay(provider, map) {
            override fun onLocationChanged(location: android.location.Location?, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)

                location?.let {
                    lastKnownLatitude = it.latitude
                    lastKnownLongitude = it.longitude

                    // Atualiza os Textviews com base na lat/long capturada
                    atualizarEnderecoNoEcra(it.latitude, it.longitude)
                }
            }
        }

        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()

        map.overlays.add(locationOverlay)

        val mapController = map.controller
        mapController.setZoom(17.0)
    }

    // TRANSFORMA LAT/LONG EM NOME DE RUA E CIDADE
    private fun atualizarEnderecoNoEcra(latitude: Double, longitude: Double) {
        // Força a execução segura na Main Thread porque o GPS roda em paralelo
        activity?.runOnUiThread {
            try {
                // Instancia o Geocoder com o idioma local do telemóvel
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]

                    // Nome da rua + número se houver (Ex: Rua das Flores, 123)
                    val rua = address.thoroughfare ?: "Sem nome de rua"
                    val numero = address.subThoroughfare ?: ""
                    val nomeRuaCompleto = if (numero.isNotEmpty()) "$rua, $numero" else rua

                    // Cidade + País (Ex: Tomar, Portugal ou São Paulo, Brasil)
                    val cidade = address.locality ?: address.subAdminArea ?: "Cidade desconhecida"
                    val pais = address.countryName ?: ""
                    val cidadePaisCompleto = if (pais.isNotEmpty()) "$cidade, $pais" else cidade

                    // Atualiza a UI dinamicamente
                    txtStreet.text = nomeRuaCompleto
                    txtCity.text = cidadePaisCompleto

                    // Guarda a string limpa para a partilha posterior
                    currentFullAddress = "$nomeRuaCompleto - $cidadePaisCompleto"
                }
            } catch (e: Exception) {
                Log.e("MAP_GEO", "Erro ao obter endereço: ${e.message}")
            }
        }
    }

    // DISPARA O MENU DE PARTILHA NATIVO DO ANDROID
    private fun partilharLocalizacao() {
        if (lastKnownLatitude == 0.0 && lastKnownLongitude == 0.0) {
            Toast.makeText(context, "Ainda a obter sinal de GPS...", Toast.LENGTH_SHORT).show()
            return
        }

        // Criamos um link direto para abrir no Google Maps de qualquer telemóvel
        val linkGoogleMaps = "https://www.google.com/maps/search/?api=1&query=$lastKnownLatitude,$lastKnownLongitude"

        // Monta a mensagem de texto estruturada
        val mensagemPartilha = """
            📍 Minha Localização Atual:
            $currentFullAddress
            
            Veja no mapa:
            $linkGoogleMaps
        """.trimIndent()

        // Configura a Intent nativa de envio
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, mensagemPartilha)
            type = "text/plain"
        }

        // Abre a folha de partilha nativa do sistema (Share Sheet)
        val shareIntent = Intent.createChooser(sendIntent, "Partilhar localização via:")
        startActivity(shareIntent)
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
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}