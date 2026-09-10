package com.appenza.smsapi

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RelayWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val endpoint = RelayStore.p(applicationContext).getString(RelayStore.URL, null) ?: return@withContext Result.failure()
        val token = RelayStore.token(applicationContext) ?: return@withContext Result.failure()
        var connection: HttpURLConnection? = null
        try {
            val payload = JSONObject()
                .put("event_id", inputData.getString("id"))
                .put("sender", inputData.getString("sender"))
                .put("body", inputData.getString("body"))
                .put("received_at", inputData.getLong("at", 0))
                .toString()
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
            }
            connection.outputStream.use { it.write(payload.toByteArray()) }
            when (connection.responseCode) {
                in 200..299 -> Result.success()
                in 500..599 -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        fun enqueue(context: Context, sender: String, body: String, receivedAt: Long) {
            val eventId = MessageDigest.getInstance("SHA-256")
                .digest("$sender|$receivedAt|$body".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val data = Data.Builder()
                .putString("id", eventId)
                .putString("sender", sender)
                .putString("body", body)
                .putLong("at", receivedAt)
                .build()
            val request = OneTimeWorkRequestBuilder<RelayWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("relay-$eventId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
