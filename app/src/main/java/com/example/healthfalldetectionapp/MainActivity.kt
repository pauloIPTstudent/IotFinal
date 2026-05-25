package com.example.healthfalldetectionapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
class MainActivity : AppCompatActivity() , BluetoothScanManager.OnQuedaDetectadaListener {


    // Estrutura para mapear e gerir de forma limpa as views de cada aba
    private lateinit var navItems: Map<Int, NavigationItem>
    private lateinit var bleManager: BluetoothScanManager
    private var countDownTimer: CountDownTimer? = null
    private var viewAlertaQueda: View? = null
    // Cores da Paleta Extraídas Diretamente do Teu Protótipo Visual
    private val colorActive = Color.parseColor("#1D2786")   // Azul Escuro/Índigo (Ativo)
    private val colorInactive = Color.parseColor("#7A7A7A") // Cinza Neutro (Inativo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Configura, vincula e vai buscar todos os componentes visuais por ID
        setupNavigation()

        // 2. Define a "Home" como ecrã inicial se não houver estado anterior guardado
        if (savedInstanceState == null) {
            navigateToTab(R.id.layout_nav_home)
        }
        bleManager = BluetoothScanManager.getInstance(this)

        // Inicializa a referência do layout embutido
        viewAlertaQueda = findViewById(R.id.layoutAlertaQueda)

        // Configura os botões da tela de queda
        viewAlertaQueda?.findViewById<Button>(R.id.btnFalsoPositivo)?.setOnClickListener {
            cancelarAlertaQueda()
        }

        viewAlertaQueda?.findViewById<Button>(R.id.btnAjuda)?.setOnClickListener {
            dispararProtocoloEmergencia()
        }
    }

    override fun onStart() {
        super.onStart()
        // Indica ao Singleton que a MainActivity está ativa e quer ouvir os alertas de queda
        bleManager.setOnQuedaDetectadaListener(this)
    }

    override fun onStop() {
        super.onStop()
        // IMPORTANTE: Remove a referência da atividade do Singleton para evitar Memory Leak
        bleManager.setOnQuedaDetectadaListener(null)
    }

    // IMPLEMENTAÇÃO DA INTERFACE
    override fun onQuedaDetectada() {
        // Como o callback do BLE corre numa thread secundária, forçamos a execução na Main Thread
        runOnUiThread {
            exibirTelaQueda()
        }
    }


    // FUNÇÃO QUE ATIVA A TELA E O CONTADOR
    fun exibirTelaQueda() {
        runOnUiThread {
            if (viewAlertaQueda?.visibility == View.VISIBLE) return@runOnUiThread

            viewAlertaQueda?.visibility = View.VISIBLE
            val txtContador = viewAlertaQueda?.findViewById<TextView>(R.id.txtContador)

            countDownTimer = object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val segundosRestantes = Math.round(millisUntilFinished.toDouble() / 1000).toInt()
                    txtContador?.text = segundosRestantes.toString()
                }

                override fun onFinish() {
                    txtContador?.text = "0"
                    dispararProtocoloEmergencia()
                }
            }.start()
        }
    }

    private fun cancelarAlertaQueda() {
        countDownTimer?.cancel()
        viewAlertaQueda?.visibility = View.GONE
        Log.i("QUEDA", "Alerta cancelado pelo utilizador (Falso positivo).")
    }

    private fun dispararProtocoloEmergencia() {
        countDownTimer?.cancel()
        viewAlertaQueda?.visibility = View.GONE
        Log.e("QUEDA", "🚨 EMERGÊNCIA DISPARADA! O tempo acabou ou o usuário pediu ajuda.")
        // TODO: Enviar SMS, fazer chamada ou disparar API web aqui
    }

    /**
     * Mapeia os IDs das abas, vai buscar todos os componentes por ID (findViewById)
     * e associa-os à nossa estrutura de dados NavigationItem.
     */
    private fun setupNavigation() {
        navItems = mapOf(
            R.id.layout_nav_home to NavigationItem(
                container = findViewById(R.id.layout_nav_home),
                icon = findViewById(R.id.img_nav_home),
                text = findViewById(R.id.txt_nav_home)
            ),
            R.id.layout_nav_devices to NavigationItem(
                container = findViewById(R.id.layout_nav_devices),
                icon = findViewById(R.id.img_nav_devices),
                text = findViewById(R.id.txt_nav_devices)
            ),
            R.id.layout_nav_history to NavigationItem(
                container = findViewById(R.id.layout_nav_history),
                icon = findViewById(R.id.img_nav_history),
                text = findViewById(R.id.txt_nav_history)
            )
        )

        // Define os listeners nos contentores para maior área de clique (amigável para seniores)
        navItems.forEach { (id, item) ->
            item.container.setOnClickListener {
                navigateToTab(id)
            }
        }
    }

    /**
     * Trata da transição de ecrãs através de Fragments no Contentor Principal
     */
    private fun navigateToTab(selectedId: Int) {
        val targetFragment: Fragment = when (selectedId) {
            R.id.layout_nav_home -> RedBottonFragment()
            R.id.layout_nav_devices -> DevicesFragment()
            R.id.layout_nav_history -> MapFragment()
            else -> RedBottonFragment()
        }

        // Substituição limpa do Fragmento atual no contentor
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView3, targetFragment)
            .commit()

        // Atualização sincronizada das cores de Ícones e Textos da Bottom Bar
        updateBottomBarVisuals(selectedId)
    /**/}

    /**
     * Altera visualmente os elementos ativos e inativos da navegação inferior
     */
    private fun updateBottomBarVisuals(selectedId: Int) {
        navItems.forEach { (id, item) ->
            val isActive = (id == selectedId)
            val color = if (isActive) colorActive else colorInactive

            // Aqui mudamos a cor do ImageView (ícone) dinamicamente
            item.icon.imageTintList = ColorStateList.valueOf(color)
            // Aqui mudamos a cor do TextView (texto por baixo do ícone) dinamicamente
            item.text.setTextColor(color)
        }
    }

    /**
     * CLASSE AUXILIAR (Definida aqui para manter o código limpo e num único ficheiro)
     * Agrupa o layout clicável, o ícone e o texto de cada aba.
     */
    data class NavigationItem(
        val container: LinearLayout,
        val icon: ImageView,
        val text: TextView
    )
    override fun onResume() {
        super.onResume()
        // Sempre que o usuário abrir o app (ou voltar para ele),
        // tentamos puxar a conexão em background se houver um dispositivo salvo.
        //bleManager.tryAutoConnect()
    }
}