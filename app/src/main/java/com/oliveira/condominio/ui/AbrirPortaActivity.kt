package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.launch
import java.io.IOException

class AbrirPortaActivity : AppCompatActivity() {

    private var predios: List<PredioApi> = emptyList()
    private lateinit var spPredio: Spinner
    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView

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
                voltarAoLogin(this@AbrirPortaActivity)
            } catch (e: ApiException) {
                mostrar(e.message ?: "Erro no servidor.", true)
            } catch (e: IOException) {
                mostrar("Sem conexão com o servidor.", true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessaoStorage(this).logado()) { voltarAoLogin(this); return }

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))
        raiz.addView(TextView(this).apply { text = "Abrir porta"; textSize = 24f })
        raiz.addView(TextView(this).apply { text = "Prédio" })
        spPredio = Spinner(this)
        raiz.addView(spPredio)

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

        spPredio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = carregarEquipamentos()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        rodar {
            predios = ApiClient(this).predios()
            spPredio.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, predios.map { it.nome })
            if (predios.isEmpty()) mostrar("Nenhum prédio disponível.", true)
        }
    }

    private fun carregarEquipamentos() {
        val p = predios.getOrNull(spPredio.selectedItemPosition) ?: return
        textMsg.visibility = View.GONE
        rodar {
            val eqs = ApiClient(this).equipamentos(p.id).filter { it.ativo }
            lista.removeAllViews()
            if (eqs.isEmpty()) lista.addView(TextView(this).apply { text = "Nenhuma porta disponível."; setPadding(0, dp(12), 0, 0) })
            eqs.forEach { e ->
                lista.addView(Button(this).apply {
                    text = "🚪  ${e.nome}"
                    textSize = 18f
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                    setOnClickListener { confirmar(e) }
                })
            }
        }
    }

    private fun confirmar(e: EquipamentoApi) {
        AlertDialog.Builder(this)
            .setTitle("Abrir ${e.nome}?")
            .setPositiveButton("Abrir") { _, _ ->
                rodar {
                    mostrar("Abrindo ${e.nome}...", false)
                    ApiClient(this).abrirPorta(e.id)
                    mostrar("${e.nome}: comando enviado.", false)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
