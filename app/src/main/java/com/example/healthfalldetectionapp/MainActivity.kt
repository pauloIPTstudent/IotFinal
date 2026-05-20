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

class MainActivity : AppCompatActivity() {
    // Estrutura para mapear e gerir de forma limpa as views de cada aba
    private lateinit var navItems: Map<Int, NavigationItem>

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
            //R.id.layout_nav_history -> HistoryFragment()
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
}