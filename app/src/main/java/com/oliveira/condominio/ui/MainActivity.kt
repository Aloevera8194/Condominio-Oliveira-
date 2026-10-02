package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.ApiClient
import com.oliveira.condominio.net.voltarAoLogin
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private class Tile(
        val titulo: String,
        val icone: String,
        val cor: String,
        val permissao: String?,
        val grande: Boolean,
        val acao: () -> Unit
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun permissoes(sessao: SessaoStorage): Set<String> {
        val u = sessao.usuario()
        val lista = u?.optJSONArray("permissoes")
        if (lista != null) {
            return (0 until lista.length()).map { lista.getString(it) }.toSet()
        }
        return when (sessao.perfil) {
            "admin", "sindico" -> setOf("ver_pessoas", "criar_usuario", "abrir_porta", "ver_camera")
            "portaria" -> setOf("ver_pessoas", "abrir_porta", "ver_camera")
            "morador" -> setOf("meu_apartamento", "abrir_porta")
            else -> emptySet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessao = SessaoStorage(this)
        if (!sessao.logado()) {
            voltarAoLogin(this)
            return
        }

        val emBreve = { nome: String ->
            Toast.makeText(this, "$nome: em breve", Toast.LENGTH_SHORT).show()
        }

        val todos = listOf(
            Tile("Pessoas", "👥", "#0078D7", "ver_pessoas", true) { startActivity(android.content.Intent(this, PessoasActivity::class.java)) },
            Tile("Meu apartamento", "🏠", "#00A300", "meu_apartamento", true) { startActivity(android.content.Intent(this, PessoasActivity::class.java)) },
            Tile("Usuários", "🔑", "#9B4F96", "criar_usuario", false) { startActivity(android.content.Intent(this, UsuariosListaActivity::class.java)) },
            Tile("Prédios", "🏢", "#008299", "gerenciar_predios", false) { startActivity(android.content.Intent(this, PrediosActivity::class.java)) },
            Tile("Abrir porta", "🚪", "#E3A21A", "abrir_porta", false) { startActivity(android.content.Intent(this, AbrirPortaActivity::class.java)) },
            Tile("Câmeras", "📷", "#B91D47", "ver_camera", false) { emBreve("Câmeras") },
            Tile("Sair", "🔒", "#555555", null, false) { confirmarSaida() }
        )
        val perms = permissoes(sessao) +
            (if (sessao.perfil == "admin") setOf("gerenciar_predios") else emptySet()) +
            (if (sessao.perfil == "morador") setOf("meu_apartamento") else emptySet())
        val visiveis = todos.filter { it.permissao == null || it.permissao in perms }

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.BLACK)
        raiz.setPadding(dp(8), dp(24), dp(8), dp(8))

        val ola = TextView(this)
        ola.text = "Olá, ${sessao.nomeUsuario}"
        ola.setTextColor(Color.WHITE)
        ola.textSize = 26f
        ola.setPadding(dp(8), dp(8), dp(8), 0)
        raiz.addView(ola)

        val perfil = TextView(this)
        perfil.text = sessao.perfil.uppercase()
        perfil.setTextColor(Color.parseColor("#AAAAAA"))
        perfil.textSize = 13f
        perfil.setPadding(dp(8), 0, dp(8), dp(12))
        raiz.addView(perfil)

        var pendente: Tile? = null
        fun linha(a: Tile, b: Tile?) {
            val l = LinearLayout(this)
            l.orientation = LinearLayout.HORIZONTAL
            l.addView(criarTile(a, b == null && !a.grande))
            if (b != null) l.addView(criarTile(b, false))
            raiz.addView(l)
        }
        for (t in visiveis) {
            if (t.grande) {
                pendente?.let { linha(it, null); pendente = null }
                val l = LinearLayout(this)
                l.addView(criarTile(t, true))
                raiz.addView(l)
            } else if (pendente == null) {
                pendente = t
            } else {
                linha(pendente!!, t)
                pendente = null
            }
        }
        pendente?.let { linha(it, null) }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        scroll.addView(raiz)
        setContentView(scroll)
    }

    private fun criarTile(t: Tile, cheio: Boolean): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.BOTTOM
        v.setBackgroundColor(Color.parseColor(t.cor))
        v.setPadding(dp(14), dp(10), dp(14), dp(12))

        val ic = TextView(this)
        ic.text = t.icone
        ic.textSize = 36f
        ic.setTextColor(Color.WHITE)
        ic.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f
        )
        v.addView(ic)

        val nome = TextView(this)
        nome.text = t.titulo
        nome.textSize = 16f
        nome.setTextColor(Color.WHITE)
        v.addView(nome)

        val altura = if (t.grande) dp(120) else dp(140)
        val p = if (t.grande || cheio) {
            LinearLayout.LayoutParams(0, altura, 1f)
        } else {
            LinearLayout.LayoutParams(0, altura, 1f)
        }
        p.setMargins(dp(4), dp(4), dp(4), dp(4))
        v.layoutParams = p
        v.setOnClickListener { t.acao() }
        return v
    }

    private fun confirmarSaida() {
        AlertDialog.Builder(this)
            .setTitle("Sair da conta?")
            .setPositiveButton("Sair") { _, _ ->
                lifecycleScope.launch {
                    ApiClient(this@MainActivity).logout()
                    voltarAoLogin(this@MainActivity)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
