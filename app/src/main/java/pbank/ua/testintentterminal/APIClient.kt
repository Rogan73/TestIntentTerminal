package pbank.ua.testintentterminal

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest


class ApiClient(context: Context, private val callback: ClientAPICallback) {
    private val context2 = context
    private var cur_cmd :String = ""


    private fun mainPrepare(data :String, php:String ):String{
        cur_cmd=php
       // val clid = context2.getString(R.string.privat_clid)
       // val secret = context2.getString(R.string.privat_secret)
        val clid = MainActivity.CLID
        val secret = MainActivity.SECRET
        val murl=context2.getString(R.string.privat_url) // "https://dio.privatbank.ua/api/nfcpos/integrators"
        val signed = (System.currentTimeMillis() / 1000).toString()
        val signature = sha1(signed + secret + data + secret)
        val url = "$murl/$php.php?clid=$clid&signed=$signed&signature=$signature"   ///check.php?clid=$clid&signed=$signed&signature=$signature
        //val options = mapOf("Content-Type" to "application/json")
        makePostRequest(url, data)
        return "";
    }

    fun mainCheck(jwtToken :String ):String {
        val params = mapOf( "jwt" to jwtToken)
        val data = params.toJson()
        return mainPrepare(data,"check")
    }

    fun mainReq( operation: String,  amount: Double,  purpose: String,  transaction_id: String = ""  ):String {
        val params = if (transaction_id.isNotBlank()) {
            mapOf(
                "operation" to operation,
                "amount" to amount,
                "transaction_id" to transaction_id
            )
        } else {
            mapOf(
                "operation" to operation,
                "amount" to amount,
                "purpose" to purpose,
            )
        }
        val data = params.toJson()

        return mainPrepare(data,"token")
    }

    // Метод для сериализации данных в JSON
    fun Any.toJson(): String = com.google.gson.Gson().toJson(this)

    // Метод для вычисления sha1 хэша строки
    fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Метод для отправки HTTP запроса
    fun inetSendRequest(method: String, url: String, data: String, options: Map<String, String>): String {
        Log.d(TAG,">> inetSendRequest")
        val client = OkHttpClient()

        val mediaType = "application/json".toMediaType()
        val requestBody = data.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .apply {
                options.forEach { (key, value) ->
                    header(key, value)
                }
            }
            .build()

        val response: Response = client.newCall(request).execute()

        return response.body?.string() ?: ""
    }



    fun makePostRequest(url: String, data: String):String {
        val okHttpClient = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val body = data.toRequestBody(mediaType)
        val request = Request.Builder()
            .method("POST", body)
            .url(url)
            .build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle this
                callback.onSendToPOST("ERROR POST ${e.message}", cur_cmd,false)
            }

            override fun onResponse(call: Call, response: Response) {
                // Handle this

                try {

                    if (cur_cmd=="token") {

                        val content = JSONObject(response.body?.string() ?: "")
                        val res = content.getString("jwt")
                        Log.d(TAG, ">> $res")
                        callback.onSendToPOST(res, cur_cmd, true)
                    }

                    if (cur_cmd=="check") {
                        val rs=response.body?.string() ?: "пусто в ответе"
                        Log.d(TAG, ">> $rs")
                        callback.onSendToPOST(rs, cur_cmd, true)
                    }


                } catch (e: JSONException) {
                    // Error parsing JSON object
                    callback.onSendToPOST("catch ERROR POST ${e.message}", cur_cmd,false)
                }
            }
        })

        return ""

    }


}
