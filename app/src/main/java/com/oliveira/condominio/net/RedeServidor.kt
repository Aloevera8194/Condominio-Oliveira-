package com.oliveira.condominio.net

import com.oliveira.condominio.data.SessaoStorage
import java.net.HttpURLConnection
import java.net.URL

// Endereço fixo no app: local (em casa) ou externo com chave (fora de casa)
object RedeServidor {
    @Volatile private var cacheBase = ""
    @Volatile private var cacheEm = 0L

    private fun comHttp(h: String): String {
        var s = h.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s
    }

    // O servidor do condomínio responde em JSON, mesmo para rota inexistente
    private fun ehMeuServidor(base: String): Boolean {
        return try {
            val con = URL("$base/api/index.php?r=ping").openConnection() as HttpURLConnection
            con.connectTimeout = 1500
            con.readTimeout = 1500
            try {
                val codigo = con.responseCode
                val fluxo = if (codigo in 200..299) con.inputStream else con.errorStream
                val texto = fluxo?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: ""
                texto.trimStart().startsWith("{")
            } finally {
                con.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun base(): String {
        val local = SessaoStorage.PADRAO
        val externo = Segredos.EXTERNO.trim()
        if (externo.isEmpty()) return local
        val agora = System.currentTimeMillis()
        if (cacheBase.isNotEmpty() && agora - cacheEm < 20000) return cacheBase
        val chave = Segredos.CHAVE.trim()
        val remoto = comHttp(externo) + (if (chave.isNotEmpty()) "/k/$chave" else "") + "/cond"
        val escolhida = if (ehMeuServidor(local)) local else remoto
        cacheBase = escolhida
        cacheEm = agora
        return escolhida
    }
}
