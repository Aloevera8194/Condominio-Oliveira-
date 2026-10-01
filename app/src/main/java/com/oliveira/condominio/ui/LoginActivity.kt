package com.oliveira.condominio.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.R
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.ApiClient
import com.oliveira.condominio.net.ApiException
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var btnEntrar: Button
    private lateinit var progress: ProgressBar
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessao = SessaoStorage(this)
        if (sessao.logado()) {
            abrirMain()
            return
        }
        setContentView(R.layout.activity_login)

        val editServidor = findViewById<EditText>(R.id.editServidor)
        val editLogin = findViewById<EditText>(R.id.editLogin)
        val editSenha = findViewById<EditText>(R.id.editSenha)
        btnEntrar = findViewById(R.id.btnEntrar)
        progress = findViewById(R.id.progress)
        textErro = findViewById(R.id.textErro)

        editServidor.setText(sessao.servidor)

        btnEntrar.setOnClickListener {
            val login = editLogin.text.toString().trim()
            val senha = editSenha.text.toString()
            if (login.isEmpty() || senha.isEmpty()) {
                mostrarErro("Informe login e senha.")
                return@setOnClickListener
            }
            sessao.servidor = editServidor.text.toString()
            setCarregando(true)
            lifecycleScope.launch {
                try {
                    ApiClient(this@LoginActivity).login(login, senha)
                    abrirMain()
                } catch (e: ApiException) {
                    mostrarErro(e.message ?: "Erro ao entrar.")
                } catch (e: IOException) {
                    mostrarErro("Não consegui conectar em ${sessao.servidor}. Confira o endereço e se o celular está na mesma rede.")
                } catch (e: Exception) {
                    mostrarErro("Erro: ${e.message}")
                } finally {
                    setCarregando(false)
                }
            }
        }
    }

    private fun setCarregando(on: Boolean) {
        btnEntrar.isEnabled = !on
        progress.visibility = if (on) View.VISIBLE else View.GONE
        if (on) textErro.visibility = View.GONE
    }

    private fun mostrarErro(msg: String) {
        textErro.text = msg
        textErro.visibility = View.VISIBLE
    }

    private fun abrirMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
