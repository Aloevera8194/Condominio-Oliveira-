package com.oliveira.condominio.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.*
import java.io.IOException

class CameraViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "camera_id"
        const val EXTRA_NOME = "camera_nome"
    }

    private var camId = 0L
    private lateinit var img: ImageView
    private lateinit var msg: TextView
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessaoStorage(this).logado()) { voltarAoLogin(this); return }
        camId = intent.getLongExtra(EXTRA_ID, 0)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(Color.BLACK)
        raiz.setPadding(16, 32, 16, 16)

        raiz.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_NOME) ?: "Câmera"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        img = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        raiz.addView(img)
        msg = TextView(this).apply { setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }
        raiz.addView(msg)
        raiz.addView(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } })
        setContentView(raiz)
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
                    if (bmp != null) { img.setImageBitmap(bmp); msg.text = "" }
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
