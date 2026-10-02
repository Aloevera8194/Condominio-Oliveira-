package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.*
import java.io.IOException

class CameraViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "camera_id"
        const val EXTRA_NOME = "camera_nome"
        const val EXTRA_PREDIO_ID = "predio_id"
    }

    private var camId = 0L
    private var cheio = false
    private lateinit var img: ImageView
    private lateinit var msg: TextView
    private lateinit var titulo: TextView
    private lateinit var voltar: Button
    private lateinit var portas: LinearLayout
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessaoStorage(this).logado()) { voltarAoLogin(this); return }
        camId = intent.getLongExtra(EXTRA_ID, 0)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.BLACK)
        raiz.setPadding(16, 32, 16, 16)

        titulo = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_NOME) ?: "Câmera"
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        raiz.addView(titulo)

        img = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setOnClickListener { alternarTela() }
        }
        raiz.addView(img)

        msg = TextView(this).apply { setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }
        raiz.addView(msg)

        portas = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(portas)

        voltar = Button(this).apply { text = "Voltar"; setOnClickListener { finish() } }
        raiz.addView(voltar)
        setContentView(raiz)
        carregarPortas()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (cheio) alternarTela() else finish()
            }
        })
        if (savedInstanceState?.getBoolean("cheio") == true) alternarTela()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("cheio", cheio)
    }

    private fun alternarTela() {
        cheio = !cheio
        val vis = if (cheio) View.GONE else View.VISIBLE
        titulo.visibility = vis
        voltar.visibility = vis
        portas.visibility = vis
        requestedOrientation = if (cheio) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                               else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (cheio) c.hide(WindowInsetsCompat.Type.systemBars())
        else c.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun carregarPortas() {
        val predioId = intent.getLongExtra(EXTRA_PREDIO_ID, 0)
        if (predioId == 0L) return
        lifecycleScope.launch {
            try {
                val eqs = ApiClient(this@CameraViewActivity).equipamentos(predioId).filter { it.ativo }
                eqs.forEach { e ->
                    portas.addView(Button(this@CameraViewActivity).apply {
                        text = "🚪  Abrir ${e.nome}"
                        setOnClickListener { confirmarAbrir(e) }
                    })
                }
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@CameraViewActivity)
            } catch (e: Exception) {
                // sem permissão de abrir porta ou sem equipamentos: não mostra botões
            }
        }
    }

    private fun confirmarAbrir(e: EquipamentoApi) {
        AlertDialog.Builder(this)
            .setTitle("Abrir ${e.nome}?")
            .setPositiveButton("Abrir") { _, _ ->
                lifecycleScope.launch {
                    try {
                        msg.text = "Abrindo ${e.nome}..."
                        ApiClient(this@CameraViewActivity).abrirPorta(e.id)
                        msg.text = "${e.nome}: comando enviado."
                    } catch (x: SessaoExpiradaException) {
                        voltarAoLogin(this@CameraViewActivity)
                    } catch (x: ApiException) {
                        msg.text = x.message ?: "Erro ao abrir."
                    } catch (x: IOException) {
                        msg.text = "Sem conexão com o servidor."
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!::img.isInitialized) return
        job = lifecycleScope.launch {
            val api = ApiClient(this@CameraViewActivity)
            while (isActive) {
                var espera = 1500L
                try {
                    val b = api.fotoCamera(camId)
                    val bmp = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(b, 0, b.size)
                    }
                    if (bmp != null) { img.setImageBitmap(bmp) }
                    else { msg.text = "Imagem inválida."; espera = 3000 }
                } catch (e: SessaoExpiradaException) {
                    voltarAoLogin(this@CameraViewActivity); return@launch
                } catch (e: ApiException) {
                    msg.text = e.message ?: "Erro na câmera."; espera = 3000
                } catch (e: IOException) {
                    msg.text = "Sem conexão com o servidor."; espera = 3000
                }
                delay(espera)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        job?.cancel()
    }
}
