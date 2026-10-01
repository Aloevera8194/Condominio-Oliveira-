package com.oliveira.condominio.net

import org.json.JSONObject

internal fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

internal fun JSONObject.bool(k: String): Boolean {
    val v = opt(k)
    return v == true || v == 1 || v == "1" || v == "true"
}

data class PredioApi(val id: Long, val nome: String, val endereco: String?) {
    companion object {
        fun de(o: JSONObject) = PredioApi(o.getLong("id"), o.optString("nome"), o.str("endereco"))
    }
}

data class UnidadeApi(val id: Long, val predioId: Long, val identificador: String, val tipo: String) {
    companion object {
        fun de(o: JSONObject) = UnidadeApi(
            o.getLong("id"), o.optLong("predio_id"),
            o.optString("identificador"), o.optString("tipo")
        )
    }
}

data class PessoaApi(
    val id: Long = 0,
    val unidadeId: Long,
    val unidades: List<Long> = emptyList(),
    val nome: String,
    val telefone: String? = null,
    val cpf: String? = null,
    val tipo: String,
    val inicioValidade: String? = null,
    val fimValidade: String? = null,
    val expirado: Boolean = false,
    val temFoto: Boolean = false
) {
    fun paraJson(): JSONObject {
        fun v(s: String?): Any = if (s.isNullOrBlank()) JSONObject.NULL else s
        return JSONObject()
            .put("unidade_id", unidadeId)
            .put("unidades", org.json.JSONArray(if (unidades.isEmpty()) listOf(unidadeId) else unidades))
            .put("nome", nome)
            .put("telefone", v(telefone))
            .put("cpf", v(cpf))
            .put("tipo", tipo)
            .put("inicio_validade", v(inicioValidade))
            .put("fim_validade", v(fimValidade))
    }

    companion object {
        fun de(o: JSONObject) = PessoaApi(
            id = o.getLong("id"),
            unidadeId = o.optLong("unidade_id"),
            unidades = o.optJSONArray("unidades")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
            nome = o.optString("nome"),
            telefone = o.str("telefone"),
            cpf = o.str("cpf"),
            tipo = o.optString("tipo"),
            inicioValidade = o.str("inicio_validade"),
            fimValidade = o.str("fim_validade"),
            expirado = o.bool("expirado"),
            temFoto = o.bool("tem_foto")
        )
    }
}
