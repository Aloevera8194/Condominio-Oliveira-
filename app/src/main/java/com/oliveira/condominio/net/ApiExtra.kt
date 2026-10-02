package com.oliveira.condominio.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun corpo(o: JSONObject) = o.toString().toByteArray(Charsets.UTF_8)
private fun obj(b: ByteArray) = JSONObject(String(b, Charsets.UTF_8))
private fun arr(b: ByteArray) = JSONArray(String(b, Charsets.UTF_8))
private fun longs(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getLong(i) } } ?: emptyList()
private fun strs(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()

data class UsuarioApi(
    val id: Long, val login: String, val nome: String, val perfil: String,
    val predios: String, val unidades: List<Long>, val permissoes: List<String>, val ativo: Boolean
) {
    companion object {
        fun de(o: JSONObject) = UsuarioApi(
            o.getLong("id"), o.optString("login"), o.optString("nome"), o.optString("perfil"),
            o.opt("predios")?.toString() ?: "",
            longs(o.optJSONArray("unidades")), strs(o.optJSONArray("permissoes")),
            !o.has("ativo") || o.bool("ativo")
        )
    }
}

data class EquipamentoApi(
    val id: Long, val predioId: Long, val nome: String, val tipo: String,
    val ip: String?, val porta: String?, val usuario: String?, val temSenha: Boolean, val ativo: Boolean
) {
    companion object {
        fun de(o: JSONObject) = EquipamentoApi(
            o.getLong("id"), o.optLong("predio_id"), o.optString("nome"), o.optString("tipo"),
            o.str("ip"), o.str("porta"), o.str("usuario"), o.bool("tem_senha"),
            !o.has("ativo") || o.bool("ativo")
        )
    }
}

data class CameraApi(val id: Long, val predioId: Long, val nome: String, val json: JSONObject) {
    companion object {
        fun de(o: JSONObject) = CameraApi(o.getLong("id"), o.optLong("predio_id"), o.optString("nome"), o)
    }
}

// ---------- Usuários ----------
suspend fun ApiClient.usuarios(): List<UsuarioApi> = withContext(Dispatchers.IO) {
    val a = arr(chamar("GET", "usuarios"))
    (0 until a.length()).map { UsuarioApi.de(a.getJSONObject(it)) }
}

suspend fun ApiClient.atualizarUsuario(id: Long, dados: JSONObject) = withContext(Dispatchers.IO) {
    chamar("PUT", "usuarios&id=$id", corpo(dados)); Unit
}

// ---------- Prédios e unidades ----------
suspend fun ApiClient.atualizarPredio(id: Long, nome: String, endereco: String?) = withContext(Dispatchers.IO) {
    val o = JSONObject().put("nome", nome).put("endereco", endereco ?: JSONObject.NULL)
    chamar("PUT", "predios&id=$id", corpo(o)); Unit
}

suspend fun ApiClient.excluirPredio(id: Long) = withContext(Dispatchers.IO) {
    chamar("DELETE", "predios&id=$id"); Unit
}

suspend fun ApiClient.atualizarUnidade(id: Long, identificador: String) = withContext(Dispatchers.IO) {
    chamar("PUT", "unidades&id=$id", corpo(JSONObject().put("identificador", identificador))); Unit
}

suspend fun ApiClient.excluirUnidade(id: Long) = withContext(Dispatchers.IO) {
    chamar("DELETE", "unidades&id=$id"); Unit
}

// ---------- Equipamentos ----------
suspend fun ApiClient.equipamentos(predioId: Long): List<EquipamentoApi> = withContext(Dispatchers.IO) {
    val a = arr(chamar("GET", "equipamentos&predio_id=$predioId"))
    (0 until a.length()).map { EquipamentoApi.de(a.getJSONObject(it)) }
}

suspend fun ApiClient.salvarEquipamento(id: Long?, dados: JSONObject) = withContext(Dispatchers.IO) {
    if (id == null) chamar("POST", "equipamentos", corpo(dados))
    else chamar("PUT", "equipamentos&id=$id", corpo(dados))
    Unit
}

suspend fun ApiClient.excluirEquipamento(id: Long) = withContext(Dispatchers.IO) {
    chamar("DELETE", "equipamentos&id=$id"); Unit
}

suspend fun ApiClient.testarEquipamento(id: Long): JSONObject = withContext(Dispatchers.IO) {
    obj(chamar("POST", "equipamentos_teste&id=$id", ByteArray(0)))
}

suspend fun ApiClient.abrirPorta(equipamentoId: Long, porta: Int = 1) = withContext(Dispatchers.IO) {
    chamar("POST", "abrir_porta&equipamento_id=$equipamentoId&porta_numero=$porta", ByteArray(0)); Unit
}

suspend fun ApiClient.enviarAosEquipamentos(pessoaId: Long, ids: List<Long>? = null): JSONObject =
    withContext(Dispatchers.IO) {
        val o = JSONObject().put("pessoa_id", pessoaId)
        if (ids != null) o.put("equipamentos", JSONArray(ids))
        obj(chamar("POST", "enviar_equipamentos", corpo(o)))
    }

// ---------- Câmeras ----------
suspend fun ApiClient.cameras(predioId: Long): List<CameraApi> = withContext(Dispatchers.IO) {
    val a = arr(chamar("GET", "cameras&predio_id=$predioId"))
    (0 until a.length()).map { CameraApi.de(a.getJSONObject(it)) }
}

suspend fun ApiClient.salvarCamera(id: Long?, dados: JSONObject) = withContext(Dispatchers.IO) {
    if (id == null) chamar("POST", "cameras", corpo(dados))
    else chamar("PUT", "cameras&id=$id", corpo(dados))
    Unit
}

suspend fun ApiClient.excluirCamera(id: Long) = withContext(Dispatchers.IO) {
    chamar("DELETE", "cameras&id=$id"); Unit
}

suspend fun ApiClient.fotoCamera(id: Long): ByteArray = withContext(Dispatchers.IO) {
    chamar("GET", "camera_foto&id=$id")
}
