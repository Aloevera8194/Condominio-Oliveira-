package com.oliveira.condominio.net

import android.content.Context
import android.content.Intent
import com.oliveira.condominio.data.SessaoStorage
import com.oliveira.condominio.ui.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val codigo: Int, mensagem: String) : Exception(mensagem)
class SessaoExpiradaException : Exception("Sessão expirada. Faça login novamente.")

/** Limpa a tela e abre o login. Use ao receber SessaoExpiradaException. */
fun voltarAoLogin(context: Context) {
    val i = Intent(context, LoginActivity::class.java)
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(i)
}

class ApiClient(context: Context) {
    private val sessao = SessaoStorage(context.applicationContext)

    internal fun chamar(
        metodo: String,
        rota: String,
        corpo: ByteArray? = null,
        tipo: String = "application/json",
        autenticar: Boolean = true
    ): ByteArray {
        val base = RedeServidor.base()
        val con = URL("$base/api/index.php?r=$rota").openConnection() as HttpURLConnection
        try {
            con.requestMethod = metodo
            con.connectTimeout = 10000
            con.readTimeout = 30000
            con.setRequestProperty("Accept", "application/json")
            val t = sessao.token
            if (autenticar && !t.isNullOrEmpty()) {
                con.setRequestProperty("Authorization", "Bearer $t")
            }
            if (corpo != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", tipo)
                con.setFixedLengthStreamingMode(corpo.size)
                con.outputStream.use { it.write(corpo) }
            }
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code in 200..299) return bytes
            if (code == 401 && autenticar) {
                sessao.limpar()
                throw SessaoExpiradaException()
            }
            val msg = try {
                JSONObject(String(bytes, Charsets.UTF_8)).optString("erro", "Erro $code")
            } catch (e: Exception) {
                "Erro $code"
            }
            throw ApiException(code, msg)
        } finally {
            con.disconnect()
        }
    }

    private fun json(o: JSONObject) = o.toString().toByteArray(Charsets.UTF_8)
    private fun texto(b: ByteArray) = String(b, Charsets.UTF_8)

    // ---------- Sessão ----------
    suspend fun login(login: String, senha: String): JSONObject = withContext(Dispatchers.IO) {
        val corpo = json(JSONObject().put("login", login).put("senha", senha))
        val r = JSONObject(texto(chamar("POST", "login", corpo, autenticar = false)))
        val usuario = r.getJSONObject("usuario")
        sessao.salvarLogin(r.getString("token"), usuario)
        usuario
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            if (!sessao.token.isNullOrEmpty()) chamar("POST", "logout", ByteArray(0))
        } catch (e: Exception) {
            // ignora: sai do aparelho de qualquer jeito
        } finally {
            sessao.limpar()
        }
    }

    suspend fun me(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(texto(chamar("GET", "me")))
    }

    // ---------- Prédios e unidades ----------
    suspend fun predios(): List<PredioApi> = withContext(Dispatchers.IO) {
        val a = JSONArray(texto(chamar("GET", "predios")))
        (0 until a.length()).map { PredioApi.de(a.getJSONObject(it)) }
    }

    suspend fun criarPredio(nome: String, endereco: String?): Long = withContext(Dispatchers.IO) {
        val o = JSONObject().put("nome", nome).put("endereco", endereco ?: JSONObject.NULL)
        JSONObject(texto(chamar("POST", "predios", json(o)))).getLong("id")
    }

    suspend fun unidades(predioId: Long): List<UnidadeApi> = withContext(Dispatchers.IO) {
        val a = JSONArray(texto(chamar("GET", "unidades&predio_id=$predioId")))
        (0 until a.length()).map { UnidadeApi.de(a.getJSONObject(it)) }
    }

    suspend fun criarUnidade(predioId: Long, identificador: String): Long = withContext(Dispatchers.IO) {
        val o = JSONObject().put("predio_id", predioId).put("identificador", identificador)
        JSONObject(texto(chamar("POST", "unidades", json(o)))).getLong("id")
    }

    // ---------- Pessoas ----------
    suspend fun pessoas(unidadeId: Long): List<PessoaApi> = withContext(Dispatchers.IO) {
        val a = JSONArray(texto(chamar("GET", "pessoas&unidade_id=$unidadeId")))
        (0 until a.length()).map { PessoaApi.de(a.getJSONObject(it)) }
    }

    suspend fun criarPessoa(p: PessoaApi): Long = withContext(Dispatchers.IO) {
        JSONObject(texto(chamar("POST", "pessoas", json(p.paraJson())))).getLong("id")
    }

    suspend fun atualizarPessoa(p: PessoaApi) = withContext(Dispatchers.IO) {
        chamar("PUT", "pessoas&id=${p.id}", json(p.paraJson()))
        Unit
    }

    suspend fun excluirPessoa(id: Long) = withContext(Dispatchers.IO) {
        chamar("DELETE", "pessoas&id=$id")
        Unit
    }

    // ---------- Fotos ----------
    suspend fun enviarFoto(
        pessoaId: Long,
        imagem: ByteArray,
        atualizar: Boolean = false,
        mime: String = "image/jpeg"
    ) = withContext(Dispatchers.IO) {
        chamar(if (atualizar) "PUT" else "POST", "fotos&pessoa_id=$pessoaId", imagem, mime)
        Unit
    }

    suspend fun baixarFoto(pessoaId: Long): ByteArray = withContext(Dispatchers.IO) {
        chamar("GET", "fotos&pessoa_id=$pessoaId")
    }

    suspend fun removerFoto(pessoaId: Long) = withContext(Dispatchers.IO) {
        chamar("DELETE", "fotos&pessoa_id=$pessoaId")
        Unit
    }

    // ---------- Usuários (admin) ----------
    suspend fun criarUsuario(
        login: String, senha: String, nome: String, perfil: String,
        predios: List<Long>, unidades: List<Long>
    ): Long = withContext(Dispatchers.IO) {
        val o = JSONObject()
            .put("login", login).put("senha", senha).put("nome", nome).put("perfil", perfil)
            .put("predios", JSONArray(predios)).put("unidades", JSONArray(unidades))
        JSONObject(texto(chamar("POST", "usuarios", json(o)))).optLong("id")
    }
}
