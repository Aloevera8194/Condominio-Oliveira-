package com.oliveira.condominio.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.ApiClient
import com.oliveira.condominio.net.ApiException
import com.oliveira.condominio.net.PredioApi
import com.oliveira.condominio.net.SessaoExpiradaException
import com.oliveira.condominio.net.voltarAoLogin
import kotlinx.coroutines.launch
import java.io.IOException

class UsuariosActivity : AppCompatActivity() {

    private var predios: List<PredioApi> = emptyList()
    private lateinit var perfis: List<String>
    private lateinit var editLogin: EditText
    private lateinit var editSenha: EditText
    private lateinit var editNome: EditText
    private lateinit var editApto: EditText
    private lateinit var spPerfil: Spinner
    private lateinit var spPredio: Spinner
    private lateinit var textPredio: TextView
    private lateinit var btnCriar: Button
    private lateinit var textMsg: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun campo(h: String, tipo: Int): EditText {
        val e = EditText(this)
        e.hint = h
        e.inputType = tipo
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessao = SessaoStorage(this)
        if (!sessao.logado()) {
            voltarAoLogin(this)
            return
        }
        perfis = if (sessao.perfil == "admin")
            listOf("morador", "portaria", "sindico", "admin")
        else listOf("morador", "portaria")

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))

        val titulo = TextView(this)
        titulo.text = "Novo usuário"
        titulo.textSize = 24f
        raiz.addView(titulo)

        editLogin = campo("Login", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        editSenha = campo("Senha (mínimo 8 caracteres)", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        editNome = campo("Nome completo", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        editApto = campo("Apartamento (ex: 101)", android.text.InputType.TYPE_CLASS_TEXT)
        raiz.addView(editLogin)
        raiz.addView(editSenha)
        raiz.addView(editNome)

        val tPerfil = TextView(this)
        tPerfil.text = "Perfil"
        tPerfil.setPadding(0, dp(12), 0, 0)
        raiz.addView(tPerfil)
        spPerfil = Spinner(this)
        spPerfil.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, perfis)
        raiz.addView(spPerfil)

        textPredio = TextView(this)
        textPredio.text = "Prédio"
        textPredio.setPadding(0, dp(12), 0, 0)
        raiz.addView(textPredio)
        spPredio = Spinner(this)
        raiz.addView(spPredio)
        raiz.addView(editApto)

        textMsg = TextView(this)
        textMsg.setPadding(0, dp(12), 0, 0)
        textMsg.visibility = View.GONE
        raiz.addView(textMsg)

        btnCriar = Button(this)
        btnCriar.text = "Criar usuário"
        raiz.addView(btnCriar)

        val btnVoltar = Button(this)
        btnVoltar.text = "Voltar"
        btnVoltar.setOnClickListener { finish() }
        raiz.addView(btnVoltar)

        val scroll = ScrollView(this)
        scroll.addView(raiz)
        setContentView(scroll)

        spPerfil.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = ajustarCampos()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        ajustarCampos()
        btnCriar.setOnClickListener { criar() }
        carregarPredios()
    }

    private fun perfilAtual() = perfis[spPerfil.selectedItemPosition]

    private fun ajustarCampos() {
        val admin = perfilAtual() == "admin"
        val vis = if (admin) View.GONE else View.VISIBLE
        textPredio.visibility = vis
        spPredio.visibility = vis
        editApto.visibility = if (perfilAtual() == "morador") View.VISIBLE else View.GONE
    }

    private fun mostrar(msg: String, erro: Boolean) {
        textMsg.text = msg
        textMsg.setTextColor(if (erro) 0xFFB00020.toInt() else 0xFF2E7D32.toInt())
        textMsg.visibility = View.VISIBLE
    }

    private fun carregarPredios() {
        lifecycleScope.launch {
            try {
                predios = ApiClient(this@UsuariosActivity).predios()
                spPredio.adapter = ArrayAdapter(
                    this@UsuariosActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    predios.map { it.nome }
                )
                if (predios.isEmpty()) mostrar("Nenhum prédio disponível no servidor.", true)
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@UsuariosActivity)
            } catch (e: ApiException) {
                mostrar(e.message ?: "Erro ao carregar prédios.", true)
            } catch (e: IOException) {
                mostrar("Sem conexão com o servidor.", true)
            }
        }
    }

    private fun criar() {
        val login = editLogin.text.toString().trim()
        val senha = editSenha.text.toString()
        val nome = editNome.text.toString().trim()
        val perfil = perfilAtual()
        val apto = editApto.text.toString().trim()
        val predio = predios.getOrNull(spPredio.selectedItemPosition)

        if (login.isEmpty() || nome.isEmpty()) { mostrar("Preencha login e nome.", true); return }
        if (senha.length < 8) { mostrar("A senha precisa ter 8 ou mais caracteres.", true); return }
        if (perfil != "admin" && predio == null) { mostrar("Escolha o prédio.", true); return }
        if (perfil == "morador" && apto.isEmpty()) { mostrar("Informe o apartamento.", true); return }

        btnCriar.isEnabled = false
        textMsg.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = ApiClient(this@UsuariosActivity)
                var unidades = emptyList<Long>()
                var listaPredios = emptyList<Long>()
                if (perfil == "morador" && predio != null) {
                    val uid = api.unidades(predio.id).firstOrNull {
                        it.identificador.trim().equals(apto, ignoreCase = true)
                    }?.id ?: api.criarUnidade(predio.id, apto)
                    unidades = listOf(uid)
                } else if (perfil != "admin" && predio != null) {
                    listaPredios = listOf(predio.id)
                }
                api.criarUsuario(login, senha, nome, perfil, listaPredios, unidades)
                mostrar("Usuário \"$login\" criado.", false)
                editLogin.setText("")
                editSenha.setText("")
                editNome.setText("")
                editApto.setText("")
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@UsuariosActivity)
            } catch (e: ApiException) {
                mostrar(e.message ?: "Erro ao criar usuário.", true)
            } catch (e: IOException) {
                mostrar("Sem conexão com o servidor.", true)
            } finally {
                btnCriar.isEnabled = true
            }
        }
    }
}
