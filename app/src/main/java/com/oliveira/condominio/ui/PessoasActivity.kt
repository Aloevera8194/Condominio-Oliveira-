package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

class PessoasActivity : AppCompatActivity() {

    private var predios: List<PredioApi> = emptyList()
    private var unidades: List<UnidadeApi> = emptyList()
    private lateinit var spPredio: Spinner
    private lateinit var spUnidade: Spinner
    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView
    private var aoEscolherFoto: ((Uri) -> Unit)? = null

    private val escolherFoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) aoEscolherFoto?.invoke(uri)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun unidadeAtual() = unidades.getOrNull(spUnidade.selectedItemPosition)
    private fun fmt(s: String) = s.take(10).split("-").reversed().joinToString("/")

    private fun aoSelecionar(f: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = f()
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }

    private fun mostrar(msg: String, erro: Boolean) {
        textMsg.text = msg
        textMsg.setTextColor(if (erro) 0xFFB00020.toInt() else 0xFF2E7D32.toInt())
        textMsg.visibility = View.VISIBLE
    }

    private fun rodar(erro: (String) -> Unit = { mostrar(it, true) }, bloco: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                bloco()
            } catch (e: SessaoExpiradaException) {
                voltarAoLogin(this@PessoasActivity)
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

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(20), dp(24), dp(20), dp(24))

        val titulo = TextView(this)
        titulo.text = "Pessoas"
        titulo.textSize = 24f
        raiz.addView(titulo)

        spPredio = Spinner(this)
        spUnidade = Spinner(this)
        raiz.addView(TextView(this).apply { text = "Prédio" })
        raiz.addView(spPredio)
        raiz.addView(TextView(this).apply { text = "Unidade" })
        raiz.addView(spUnidade)

        if (sessao.perfil != "portaria") {
            val b = Button(this)
            b.text = "Nova pessoa"
            b.setOnClickListener { novaPessoa() }
            raiz.addView(b)
        }

        textMsg = TextView(this)
        textMsg.visibility = View.GONE
        raiz.addView(textMsg)

        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        raiz.addView(lista)

        val voltar = Button(this)
        voltar.text = "Voltar"
        voltar.setOnClickListener { finish() }
        raiz.addView(voltar)

        val scroll = ScrollView(this)
        scroll.addView(raiz)
        setContentView(scroll)

        spPredio.onItemSelectedListener = aoSelecionar { carregarUnidades() }
        spUnidade.onItemSelectedListener = aoSelecionar { carregarPessoas() }
        carregarPredios()
    }

    private fun carregarPredios() = rodar {
        predios = ApiClient(this).predios()
        spPredio.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, predios.map { it.nome })
        if (predios.isEmpty()) mostrar("Nenhum prédio disponível.", true)
    }

    private fun carregarUnidades() {
        val p = predios.getOrNull(spPredio.selectedItemPosition) ?: return
        rodar {
            unidades = ApiClient(this).unidades(p.id)
            spUnidade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unidades.map { it.identificador })
            if (unidades.isEmpty()) lista.removeAllViews()
        }
    }

    private fun carregarPessoas() {
        val u = unidadeAtual() ?: return
        textMsg.visibility = View.GONE
        rodar {
            val pessoas = ApiClient(this).pessoas(u.id)
            lista.removeAllViews()
            if (pessoas.isEmpty()) {
                lista.addView(TextView(this).apply { text = "Nenhuma pessoa cadastrada."; setPadding(0, dp(12), 0, 0) })
            }
            pessoas.forEach { lista.addView(linhaPessoa(it)) }
        }
    }

    private fun linhaPessoa(p: PessoaApi): View {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(0, dp(12), 0, dp(12))
        val n = TextView(this)
        n.text = p.nome
        n.textSize = 18f
        l.addView(n)
        val validade = p.fimValidade?.let { "até ${fmt(it)}" } ?: "sem validade"
        val d = TextView(this)
        d.text = "${p.tipo} · $validade" + (if (p.temFoto) "" else " · sem foto")
        l.addView(d)
        if (p.expirado) {
            l.addView(TextView(this).apply { text = "EXPIRADO"; setTextColor(Color.RED) })
        }
        l.setOnClickListener { opcoes(p) }
        return l
    }

    private fun opcoes(p: PessoaApi) {
        AlertDialog.Builder(this)
            .setTitle(p.nome)
            .setItems(arrayOf("Trocar foto", "Excluir")) { _, i ->
                if (i == 0) {
                    aoEscolherFoto = { uri ->
                        rodar { enviarFoto(p.id, uri, p.temFoto); carregarPessoas() }
                    }
                    escolherFoto.launch("image/*")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Excluir ${p.nome}?")
                        .setPositiveButton("Excluir") { _, _ ->
                            rodar { ApiClient(this).excluirPessoa(p.id); carregarPessoas() }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .show()
    }

    private suspend fun enviarFoto(id: Long, uri: Uri, atualizar: Boolean) {
        val bytes = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw ApiException(0, "Não consegui ler a foto")
        ApiClient(this).enviarFoto(id, bytes, atualizar, contentResolver.getType(uri) ?: "image/jpeg")
    }

    private fun escolherData(ok: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, a, m, d -> ok("%04d-%02d-%02d".format(a, m + 1, d)) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun novaPessoa() {
        val u = unidadeAtual() ?: run { mostrar("Escolha uma unidade.", true); return }
        val tipos = if (u.tipo.equals("condominio", true))
            listOf("funcionario_predio", "prestador")
        else listOf("morador", "familiar", "locatario", "visitante", "funcionario_apto")

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)

        val eNome = EditText(this).apply {
            hint = "Nome completo"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val spTipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@PessoasActivity, android.R.layout.simple_spinner_dropdown_item, tipos)
        }
        val eTel = EditText(this).apply { hint = "Telefone com DDD"; inputType = InputType.TYPE_CLASS_PHONE }
        val eCpf = EditText(this).apply { hint = "CPF (só números)"; inputType = InputType.TYPE_CLASS_NUMBER }

        var inicio = "%04d-%02d-%02d".format(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        var fim = ""
        var fotoUri: Uri? = null

        val bIni = Button(this).apply { text = "Início: ${fmt(inicio)}" }
        val bFim = Button(this).apply { text = "Fim: escolher (obrigatório p/ locatário, visitante, prestador)" }
        val bFoto = Button(this).apply { text = "Escolher foto" }
        bIni.setOnClickListener { escolherData { inicio = it; bIni.text = "Início: ${fmt(it)}" } }
        bFim.setOnClickListener { escolherData { fim = it; bFim.text = "Fim: ${fmt(it)}" } }
        bFoto.setOnClickListener {
            aoEscolherFoto = { uri -> fotoUri = uri; bFoto.text = "Foto escolhida ✓" }
            escolherFoto.launch("image/*")
        }

        form.addView(eNome)
        form.addView(spTipo)
        form.addView(eTel)
        form.addView(eCpf)
        form.addView(bIni)
        form.addView(bFim)
        form.addView(bFoto)

        val checks = mutableListOf<CheckBox>()
        val outros = predios.filter { it.id != u.predioId }
        if (outros.isNotEmpty()) {
            form.addView(TextView(this).apply { text = "Também liberar nos condomínios:"; setPadding(0, dp(12), 0, 0) })
            outros.forEach { p ->
                val cb = CheckBox(this)
                cb.text = p.nome
                cb.tag = p.id
                checks.add(cb)
                form.addView(cb)
            }
        }

        val tErro = TextView(this).apply { setTextColor(0xFFB00020.toInt()) }
        form.addView(tErro)

        val dlg = AlertDialog.Builder(this)
            .setTitle("Nova pessoa")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()

        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
            val nome = eNome.text.toString().trim()
            val tipo = tipos[spTipo.selectedItemPosition]
            if (nome.isEmpty()) { tErro.text = "Informe o nome."; return@setOnClickListener }
            if (tipo in listOf("locatario", "visitante", "prestador") && fim.isEmpty()) {
                tErro.text = "Informe o fim da validade."; return@setOnClickListener
            }
            val marcados = checks.filter { it.isChecked }.map { it.tag as Long }
            btn.isEnabled = false
            tErro.text = ""
            rodar({ msg -> tErro.text = msg; btn.isEnabled = true }) {
                val api = ApiClient(this)
                val ids = mutableListOf(u.id)
                for (pid in marcados) {
                    api.unidades(pid).firstOrNull { it.tipo.equals("condominio", true) }?.let { ids.add(it.id) }
                }
                val id = api.criarPessoa(
                    PessoaApi(
                        unidadeId = u.id,
                        unidades = ids,
                        nome = nome,
                        telefone = eTel.text.toString().trim().ifEmpty { null },
                        cpf = eCpf.text.toString().trim().ifEmpty { null },
                        tipo = tipo,
                        inicioValidade = inicio,
                        fimValidade = fim.ifEmpty { null }
                    )
                )
                var aviso = ""
                fotoUri?.let {
                    try {
                        enviarFoto(id, it, false)
                    } catch (e: SessaoExpiradaException) {
                        throw e
                    } catch (e: Exception) {
                        aviso = " (a foto falhou: ${e.message})"
                    }
                }
                dlg.dismiss()
                mostrar("Pessoa salva$aviso", aviso.isNotEmpty())
                carregarPessoas()
            }
        }
    }
}
