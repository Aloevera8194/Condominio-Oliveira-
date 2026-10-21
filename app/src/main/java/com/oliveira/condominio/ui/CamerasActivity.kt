package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.content.Intent
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
import org.json.JSONObject
import java.io.IOException

class CamerasActivity : AppCompatActivity() {

    private var predios: List<PredioApi> = emptyList()
    private var config = false
    private lateinit var spPredio: Spinner
    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun predioAtual() = predios.getOrNull(spPredio.selectedItemPosition)

    private fun mostrar(msg: String, erro: Boolean) {
        textMsg.text = msg
        textMsg.setTextColor(if (erro) android.graphics.Color.parseColor("#C94F49") else android.graphics.Color.parseColor("#54804B"))
        textMsg.visibility = View.VISIBLE
    }

    private fun rodar(erro: (String) -> Unit = { mostrar(it, true) }, bloco: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                bloco()
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@CamerasActivity)
            } catch (e: ApiException) {
                erro(e.message ?: "Erro no servidor.")
            } catch (e: IOException) {
                erro("Sem conexão com o servidor.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessao = SessaoStorage(this)
        if (!sessao.logado()) { voltarAoLogin(this); return }
        config = sessao.perfil == "admin" || sessao.perfil == "sindico"

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))
        raiz.addView(TextView(this).apply { text = "Câmeras"; textSize = 24f })
        raiz.addView(TextView(this).apply { text = "Prédio" })
        spPredio = Spinner(this)
        raiz.addView(spPredio)
        if (config) raiz.addView(Button(this).apply {
            text = "Nova câmera"
            setOnClickListener { formulario(null) }
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

        spPredio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = carregar()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        rodar {
            predios = ApiClient(this).predios()
            spPredio.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, predios.map { it.nome })
            if (predios.isEmpty()) mostrar("Nenhum prédio disponível.", true)
        }
    }

    private fun carregar() {
        val p = predioAtual() ?: return
        rodar {
            val cams = ApiClient(this).cameras(p.id)
            lista.removeAllViews()
            if (cams.isEmpty()) lista.addView(TextView(this).apply { text = "Nenhuma câmera."; setPadding(0, dp(12), 0, 0) })
            cams.forEach { c ->
                val l = LinearLayout(this)
                l.orientation = LinearLayout.VERTICAL
                l.setPadding(0, dp(12), 0, dp(12))
                l.addView(TextView(this).apply { text = "📷  ${c.nome}"; textSize = 18f })
                if (config) {
                    val ip = c.json.optString("ip", "")
                    l.addView(TextView(this).apply { text = if (ip.isBlank()) "IP do prédio" else ip })
                    if (!c.json.optBoolean("ativo", true)) {
                        l.addView(TextView(this).apply { text = "INATIVA"; setTextColor(Color.RED) })
                    }
                }
                l.setOnClickListener { if (config) opcoes(c) else ver(c) }
                lista.addView(l)
            }
        }
    }

    private fun ver(c: CameraApi) {
        val i = Intent(this, CameraViewActivity::class.java)
        i.putExtra(CameraViewActivity.EXTRA_ID, c.id)
        i.putExtra(CameraViewActivity.EXTRA_NOME, c.nome)
        i.putExtra(CameraViewActivity.EXTRA_PREDIO_ID, c.predioId)
        startActivity(i)
    }

    private fun opcoes(c: CameraApi) {
        AlertDialog.Builder(this)
            .setTitle(c.nome)
            .setItems(arrayOf("Ver imagem", "Editar", "Excluir")) { _, i ->
                when (i) {
                    0 -> ver(c)
                    1 -> formulario(c)
                    else -> AlertDialog.Builder(this)
                        .setTitle("Excluir ${c.nome}?")
                        .setPositiveButton("Excluir") { _, _ ->
                            rodar {
                                ApiClient(this).excluirCamera(c.id)
                                mostrar("Câmera excluída.", false)
                                carregar()
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .show()
    }

    private fun formulario(c: CameraApi?) {
        val p = predioAtual() ?: return
        val j = c?.json
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)

        fun campo(h: String, v: String?, tipo: Int = InputType.TYPE_CLASS_TEXT) =
            EditText(this).apply { hint = h; setText(v ?: ""); inputType = tipo }
        val num = InputType.TYPE_CLASS_NUMBER
        val eNome = campo("Nome (ex: Garagem)", c?.nome)
        val eIp = campo("IP (vazio = IP do prédio)", j?.optString("ip"))
        val ePorta = campo("Porta RTSP (ex: 554)", j?.optString("porta"), num)
        val ePortaHttp = campo("Porta HTTP (ex: 80)", j?.optString("porta_http"), num)
        val eUser = campo("Usuário", j?.optString("usuario"))
        val eSenha = campo(
            if (j?.optBoolean("tem_senha") == true) "Senha (vazio = manter)" else "Senha",
            "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val eStream = campo("Caminho do stream (ex: /Streaming/Channels/101)", j?.optString("caminho_stream"))
        val eSnap = campo("Caminho do snapshot (ex: /ISAPI/Streaming/channels/101/picture)", j?.optString("caminho_snapshot"))
        val cbAtivo = CheckBox(this).apply { text = "Ativa"; isChecked = j?.optBoolean("ativo", true) ?: true }
        val tErro = TextView(this).apply { setTextColor(android.graphics.Color.parseColor("#C94F49")) }
        listOf(eNome, eIp, ePorta, ePortaHttp, eUser, eSenha, eStream, eSnap, cbAtivo, tErro)
            .forEach { form.addView(it) }

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (c == null) "Nova câmera" else "Editar câmera")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()

        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
            val nome = eNome.text.toString().trim()
            if (nome.isEmpty()) { tErro.text = "Informe o nome."; return@setOnClickListener }
            fun n(e: EditText): Any = e.text.toString().trim().let { it.toIntOrNull() ?: it }
            val o = JSONObject()
                .put("predio_id", p.id)
                .put("nome", nome)
                .put("ip", eIp.text.toString().trim())
                .put("porta", n(ePorta))
                .put("porta_http", n(ePortaHttp))
                .put("usuario", eUser.text.toString().trim())
                .put("caminho_stream", eStream.text.toString().trim())
                .put("caminho_snapshot", eSnap.text.toString().trim())
                .put("ativo", cbAtivo.isChecked)
            val senha = eSenha.text.toString()
            if (senha.isNotEmpty()) o.put("senha", senha)
            btn.isEnabled = false
            tErro.text = ""
            rodar({ msg -> tErro.text = msg; btn.isEnabled = true }) {
                ApiClient(this).salvarCamera(c?.id, o)
                dlg.dismiss()
                mostrar("Câmera salva.", false)
                carregar()
            }
        }
    }
}
