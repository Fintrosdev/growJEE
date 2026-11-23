package com.growjee.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/** Same Supabase project as the website and the desktop app. */
object Api {
    private const val BASE = "https://filbyqskhaivvimxudjr.supabase.co"
    private const val KEY = "sb_publishable_U8rPRHcKY3usIzTKNW47LQ_1e1uwqoS"
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    /** Returns a JSONObject, JSONArray or null. */
    suspend fun request(path: String, method: String, body: JSONObject? = null, token: String? = null, prefer: String? = null): Any? =
        withContext(Dispatchers.IO) {
            val rb = body?.toString()?.toRequestBody(json) ?: if (method == "GET") null else ByteArray(0).toRequestBody()
            val req = Request.Builder().url(BASE + path).header("apikey", KEY)
                .apply {
                    token?.let { header("Authorization", "Bearer $it") }
                    prefer?.let { header("Prefer", it) }
                }
                .method(method, rb).build()
            http.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiException(errorOf(raw, res.code))
                if (raw.isBlank()) null else JSONTokener(raw).nextValue()
            }
        }

    private fun errorOf(raw: String, code: Int): String = try {
        val e = JSONObject(raw)
        listOf("message", "msg", "error_description").firstNotNullOfOrNull { k -> e.optString(k).takeIf { it.isNotBlank() } }
            ?: "Request failed ($code)"
    } catch (_: Exception) {
        "Request failed ($code)"
    }

    private fun storagePath(path: String) = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    suspend fun uploadPdf(path: String, bytes: ByteArray, token: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BASE/storage/v1/object/jee-mock-pdfs/${storagePath(path)}")
            .header("apikey", KEY).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(bytes.toRequestBody("application/pdf".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.body?.string().orEmpty().ifBlank { "Upload failed (${res.code})" })
        }
    }

    suspend fun downloadPdf(path: String, token: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BASE/storage/v1/object/authenticated/jee-mock-pdfs/${storagePath(path)}")
            .header("apikey", KEY).header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException("Download failed (${res.code})")
            res.body!!.bytes()
        }
    }
}
