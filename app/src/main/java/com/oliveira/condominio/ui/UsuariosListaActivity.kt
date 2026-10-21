package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class UsuariosListaActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView
    private var admin = false

    private val todasPermissoes = listOf(
        "ver_pessoas", "cadastrar_pessoa", "editar_pessoa", "excluir_pessoa",
        "criar_usuario", "abrir_porta", "ver_camera", "meu_apartamento"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun mostrar(msg: String, erro: Boolean) {
        textMsg.text = msg
        textMsg.setTextColor(if (erro) android.graphics.Color.parseColor("#C94F49") else android.graphics.Color.parseColor("#54804B"))
        textMsg.visibility = View.VISIBLE
    }

    private fun rodar(bloco: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                bloco()
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@UsuariosListaActivity)
            } catch (e: ApiException) {
                mostrar(e.message ?: "Erro no servidor.", true)
            } catch (e: IOException) {
                mostrar("Sem conexão com o servidor.", true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessao = SessaoStorage(this)
        if (!sessao.logado()) { voltarAoLogin(this); return }
        admin = sessao.perfil == "admin"

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))
        raiz.addView(TextView(this).apply { text = "Usuários"; textSize = 24f })

        raiz.addView(Button(this).apply {
            text = "Novo usuário"
            setOnClickListener { startActivity(android.content.Intent(this@UsuariosListaActivity, UsuariosActivity::class.java)) }
        })

        textMsg = TextView(this)
        textMsg.visibility = View.GONE
        raiz.addView(textMsg)

        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        raiz.addView(lista)

        raiz.addView(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } })

        val scroll = ScrollView(this)
        scroll.addView(raiz)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::lista.isInitialized) carregar()
    }

    private fun carregar() = rodar {
        val us = ApiClient(this).usuarios()
        lista.removeAllViews()
        if (us.isEmpty()) lista.addView(TextView(this).apply { text = "Nenhum usuário."; setPadding(0, dp(12), 0, 0) })
        us.forEach { u ->
            val l = LinearLayout(this)
            l.orientation = LinearLayout.VERTICAL
            l.setPadding(0, dp(12), 0, dp(12))
            l.addView(TextView(this).apply { text = u.nome; textSize = 18f })
            l.addView(TextView(this).apply { text = "${u.login} · ${u.perfil}" })
            if (!u.ativo) l.addView(TextView(this).apply { text = "BLOQUEADO"; setTextColor(Color.RED) })
            if (admin) l.setOnClickListener { opcoes(u) }
            lista.addView(l)
        }
    }

    private fun opcoes(u: UsuarioApi) {
        AlertDialog.Builder(this)
            .setTitle(u.nome)
            .setItems(arrayOf("Editar", "Redefinir senha", if (u.ativo) "Bloquear" else "Desbloquear")) { _, i ->
                when (i) {
                    0 -> editar(u)
                    1 -> redefinirSenha(u)
                    else -> rodar {
                        ApiClient(this).atualizarUsuario(u.id, JSONObject().put("ativo", !u.ativo))
                        mostrar(if (u.ativo) "Usuário bloqueado." else "Usuário desbloqueado.", false)
                        carregar()
                    }
                }
            }
            .show()
    }

    private fun redefinirSenha(u: UsuarioApi) {
        val e = EditText(this).apply {
            hint = "Nova senha (mínimo 8)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Nova senha de ${u.nome}")
            .setView(e)
            .setPositiveButton("Salvar") { _, _ ->
                val s = e.text.toString()
                if (s.length < 8) { mostrar("A senha precisa ter 8 ou mais caracteres.", true); return@setPositiveButton }
                rodar {
                    ApiClient(this).atualizarUsuario(u.id, JSONObject().put("senha", s))
                    mostrar("Senha redefinida. As sessões abertas dele foram encerradas.", false)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editar(u: UsuarioApi) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)
        val eNome = EditText(this).apply { setText(u.nome); hint = "Nome" }
        val perfis = listOf("morador", "portaria", "sindico", "admin")
        val spPerfil = Spinner(this).apply {
            adapter = ArrayAdapter(this@UsuariosListaActivity, android.R.layout.simple_spinner_dropdown_item, perfis)
            setSelection(perfis.indexOf(u.perfil).coerceAtLeast(0))
        }
        form.addView(eNome)
        form.addView(spPerfil)
        form.addView(TextView(this).apply { text = "Permissões:"; setPadding(0, dp(12), 0, 0) })
        val checks = todasPermissoes.map { p ->
            CheckBox(this).apply { text = p; isChecked = p in u.permissoes; form.addView(this) }
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Editar usuário")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Salvar") { _, _ ->
                val perms = JSONArray(todasPermissoes.filterIndexed { i, _ -> checks[i].isChecked })
                rodar {
                    ApiClient(this).atualizarUsuario(
                        u.id,
                        JSONObject().put("nome", eNome.text.toString().trim())
                            .put("perfil", perfis[spPerfil.selectedItemPosition])
                            .put("permissoes", perms)
                    )
                    mostrar("Usuário atualizado.", false)
                    carregar()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
    }
}
