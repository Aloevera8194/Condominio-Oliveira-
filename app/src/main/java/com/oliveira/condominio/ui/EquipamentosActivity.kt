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
import org.json.JSONObject
import java.io.IOException

class EquipamentosActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREDIO_ID = "predio_id"
        const val EXTRA_PREDIO_NOME = "predio_nome"
    }

    private var predioId = 0L
    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
                voltarAoLogin(this@EquipamentosActivity)
            } catch (e: ApiException) {
                erro(e.message ?: "Erro no servidor.")
            } catch (e: IOException) {
                erro("Sem conexão com o servidor.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessaoStorage(this).logado()) { voltarAoLogin(this); return }
        predioId = intent.getLongExtra(EXTRA_PREDIO_ID, 0)
        val nomePredio = intent.getStringExtra(EXTRA_PREDIO_NOME) ?: ""

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))
        raiz.addView(TextView(this).apply { text = "Equipamentos"; textSize = 24f })
        raiz.addView(TextView(this).apply { text = nomePredio })
        raiz.addView(Button(this).apply { text = "Novo equipamento"; setOnClickListener { formulario(null) } })

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
        val eqs = ApiClient(this).equipamentos(predioId)
        lista.removeAllViews()
        if (eqs.isEmpty()) lista.addView(TextView(this).apply { text = "Nenhum equipamento."; setPadding(0, dp(12), 0, 0) })
        eqs.forEach { e ->
            val l = LinearLayout(this)
            l.orientation = LinearLayout.VERTICAL
            l.setPadding(0, dp(12), 0, dp(12))
            l.addView(TextView(this).apply { text = e.nome; textSize = 18f })
            val ip = if (e.ip.isNullOrBlank()) "IP do prédio" else e.ip
            val porta = if (e.porta.isNullOrBlank()) "" else ":${e.porta}"
            l.addView(TextView(this).apply { text = "${e.tipo} · $ip$porta" })
            if (!e.ativo) l.addView(TextView(this).apply { text = "INATIVO"; setTextColor(Color.RED) })
            l.setOnClickListener { opcoes(e) }
            lista.addView(l)
        }
    }

    private fun opcoes(e: EquipamentoApi) {
        AlertDialog.Builder(this)
            .setTitle(e.nome)
            .setItems(arrayOf("Testar conexão", "Editar", "Excluir")) { _, i ->
                when (i) {
                    0 -> rodar {
                        mostrar("Testando...", false)
                        val r = ApiClient(this).testarEquipamento(e.id)
                        if (r.optBoolean("ok", false)) {
                            val modelo = r.optJSONObject("equipamento")?.optString("model") ?: ""
                            mostrar("Conectou. $modelo", false)
                        } else {
                            mostrar(r.optString("erro", "Falha ao conectar."), true)
                        }
                    }
                    1 -> formulario(e)
                    else -> AlertDialog.Builder(this)
                        .setTitle("Excluir ${e.nome}?")
                        .setPositiveButton("Excluir") { _, _ ->
                            rodar {
                                ApiClient(this).excluirEquipamento(e.id)
                                mostrar("Equipamento excluído.", false)
                                carregar()
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .show()
    }

    private fun formulario(e: EquipamentoApi?) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)

        val eNome = EditText(this).apply { hint = "Nome (ex: Portão social)"; setText(e?.nome ?: "") }
        val tipos = listOf("Hikvision", "Sonoff", "Control iD")
        val spTipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@EquipamentosActivity, android.R.layout.simple_spinner_dropdown_item, tipos)
            val tipoSalvo = tipos.indexOf(e?.tipo).coerceAtLeast(0)
            setSelection(tipoSalvo)
        }
        val eIp = EditText(this).apply { hint = "IP (vazio = IP do prédio)"; setText(e?.ip ?: "") }
        val ePorta = EditText(this).apply {
            hint = "Porta (ex: 80)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(e?.porta ?: "")
        }
        val eUser = EditText(this).apply {
            hint = "Usuário (vazio = padrão do prédio)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(e?.usuario ?: "")
        }
        val eSenha = EditText(this).apply {
            hint = if (e?.temSenha == true) "Senha (vazio = manter)" else "Senha (vazio = padrão do prédio)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val cbAtivo = CheckBox(this).apply { text = "Ativo"; isChecked = e?.ativo ?: true }

        listOf(eNome, spTipo, eIp, ePorta, eUser, eSenha, cbAtivo).forEach { form.addView(it) }
        val tErro = TextView(this).apply { setTextColor(android.graphics.Color.parseColor("#C94F49")) }
        form.addView(tErro)

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (e == null) "Novo equipamento" else "Editar equipamento")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()

        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
            val nome = eNome.text.toString().trim()
            if (nome.isEmpty()) { tErro.text = "Informe o nome."; return@setOnClickListener }
            val o = JSONObject()
                .put("predio_id", predioId)
                .put("nome", nome)
                .put("tipo", tipos[spTipo.selectedItemPosition])
                .put("ip", eIp.text.toString().trim())
                .put("usuario", eUser.text.toString().trim())
                .put("ativo", cbAtivo.isChecked)
            val porta = ePorta.text.toString().trim()
            if (porta.isEmpty()) o.put("porta", "") else o.put("porta", porta.toIntOrNull() ?: porta)
            val senha = eSenha.text.toString()
            if (senha.isNotEmpty()) o.put("senha", senha)
            btn.isEnabled = false
            tErro.text = ""
            rodar({ msg -> tErro.text = msg; btn.isEnabled = true }) {
                ApiClient(this).salvarEquipamento(e?.id, o)
                dlg.dismiss()
                mostrar("Equipamento salvo.", false)
                carregar()
            }
        }
    }
}
