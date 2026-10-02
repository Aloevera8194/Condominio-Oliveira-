package com.oliveira.condominio.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.net.*
import kotlinx.coroutines.launch
import java.io.IOException

class PrediosActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var textMsg: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
                voltarAoLogin(this@PrediosActivity)
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

        raiz.addView(TextView(this).apply { text = "Prédios"; textSize = 24f })

        if (sessao.perfil == "admin") {
            raiz.addView(Button(this).apply {
                text = "Novo prédio"
                setOnClickListener { novoPredio() }
            })
        }

        textMsg = TextView(this)
        textMsg.visibility = View.GONE
        raiz.addView(textMsg)

        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        raiz.addView(lista)

        raiz.addView(Button(this).apply {
            text = "Voltar"
            setOnClickListener { finish() }
        })

        val scroll = ScrollView(this)
        scroll.addView(raiz)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::lista.isInitialized) carregar()
    }

    private fun carregar() = rodar {
        val predios = ApiClient(this).predios()
        lista.removeAllViews()
        if (predios.isEmpty()) {
            lista.addView(TextView(this).apply { text = "Nenhum prédio disponível."; setPadding(0, dp(12), 0, 0) })
        }
        predios.forEach { p ->
            val l = LinearLayout(this)
            l.orientation = LinearLayout.VERTICAL
            l.setPadding(0, dp(12), 0, dp(12))
            l.addView(TextView(this).apply { text = p.nome; textSize = 18f })
            l.addView(TextView(this).apply { text = p.endereco ?: "sem endereço" })
            l.setOnClickListener { opcoes(p) }
            lista.addView(l)
        }
    }

    private fun opcoes(p: PredioApi) {
        val perfil = SessaoStorage(this).perfil
        val admin = perfil == "admin"
        val itens = mutableListOf("Ver unidades", "Nova unidade")
        if (admin || perfil == "sindico") itens.add("Equipamentos")
        if (admin) { itens.add("Editar prédio"); itens.add("Excluir prédio") }
        AlertDialog.Builder(this)
            .setTitle(p.nome)
            .setItems(itens.toTypedArray()) { _, i ->
                when (itens[i]) {
                    "Ver unidades" -> verUnidades(p)
                    "Nova unidade" -> novaUnidade(p)
                    "Equipamentos" -> {
                        val it = android.content.Intent(this, EquipamentosActivity::class.java)
                        it.putExtra(EquipamentosActivity.EXTRA_PREDIO_ID, p.id)
                        it.putExtra(EquipamentosActivity.EXTRA_PREDIO_NOME, p.nome)
                        startActivity(it)
                    }
                    "Editar prédio" -> editarPredio(p)
                    else -> excluirPredio(p)
                }
            }
            .show()
    }

    private fun editarPredio(p: PredioApi) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)
        val eNome = EditText(this).apply { setText(p.nome); hint = "Nome do prédio" }
        val eEnd = EditText(this).apply { setText(p.endereco ?: ""); hint = "Endereço" }
        form.addView(eNome)
        form.addView(eEnd)
        AlertDialog.Builder(this)
            .setTitle("Editar prédio")
            .setView(form)
            .setPositiveButton("Salvar") { _, _ ->
                val nome = eNome.text.toString().trim()
                if (nome.isEmpty()) { mostrar("Informe o nome.", true); return@setPositiveButton }
                rodar {
                    ApiClient(this).atualizarPredio(p.id, nome, eEnd.text.toString().trim().ifEmpty { null })
                    mostrar("Prédio atualizado.", false)
                    carregar()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirPredio(p: PredioApi) {
        AlertDialog.Builder(this)
            .setTitle("Excluir ${p.nome}?")
            .setMessage("O servidor recusa se houver pessoas, equipamentos, câmeras ou usuários ligados.")
            .setPositiveButton("Excluir") { _, _ ->
                rodar {
                    ApiClient(this).excluirPredio(p.id)
                    mostrar("Prédio excluído.", false)
                    carregar()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun verUnidades(p: PredioApi) = rodar {
        val un = ApiClient(this).unidades(p.id)
        if (un.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Unidades de ${p.nome}")
                .setMessage("Nenhuma unidade.").setPositiveButton("Fechar", null).show()
            return@rodar
        }
        val nomes = un.map { "${it.identificador}  (${it.tipo})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Unidades de ${p.nome}")
            .setItems(nomes) { _, i -> opcoesUnidade(p, un[i]) }
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun opcoesUnidade(p: PredioApi, u: UnidadeApi) {
        if (u.tipo.equals("condominio", true)) {
            mostrar("A unidade CONDOMINIO não pode ser renomeada nem excluída.", true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unidade ${u.identificador}")
            .setItems(arrayOf("Renomear", "Excluir")) { _, i ->
                if (i == 0) {
                    val e = EditText(this).apply { setText(u.identificador) }
                    AlertDialog.Builder(this)
                        .setTitle("Renomear unidade")
                        .setView(e)
                        .setPositiveButton("Salvar") { _, _ ->
                            val nv = e.text.toString().trim()
                            if (nv.isNotEmpty()) rodar {
                                ApiClient(this).atualizarUnidade(u.id, nv)
                                mostrar("Unidade renomeada para \"$nv\".", false)
                                verUnidades(p)
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Excluir unidade ${u.identificador}?")
                        .setMessage("O servidor recusa se houver pessoas, usuários ou pets ligados.")
                        .setPositiveButton("Excluir") { _, _ ->
                            rodar {
                                ApiClient(this).excluirUnidade(u.id)
                                mostrar("Unidade excluída.", false)
                                verUnidades(p)
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .show()
    }

    private fun novaUnidade(p: PredioApi) {
        val e = EditText(this).apply { hint = "Identificador (ex: 201)" }
        AlertDialog.Builder(this)
            .setTitle("Nova unidade em ${p.nome}")
            .setView(e)
            .setPositiveButton("Criar") { _, _ ->
                val id = e.text.toString().trim()
                if (id.isNotEmpty()) rodar {
                    ApiClient(this).criarUnidade(p.id, id)
                    mostrar("Unidade \"$id\" criada.", false)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun novoPredio() {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(12), dp(20), 0)
        val eNome = EditText(this).apply {
            hint = "Nome do prédio"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val eEnd = EditText(this).apply {
            hint = "Endereço (opcional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        form.addView(eNome)
        form.addView(eEnd)
        AlertDialog.Builder(this)
            .setTitle("Novo prédio")
            .setView(form)
            .setPositiveButton("Criar") { _, _ ->
                val nome = eNome.text.toString().trim()
                if (nome.isEmpty()) { mostrar("Informe o nome.", true); return@setPositiveButton }
                rodar {
                    ApiClient(this).criarPredio(nome, eEnd.text.toString().trim().ifEmpty { null })
                    mostrar("Prédio \"$nome\" criado.", false)
                    carregar()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
