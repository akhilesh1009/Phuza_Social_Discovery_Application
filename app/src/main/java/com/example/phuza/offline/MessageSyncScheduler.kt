package com.example.phuza.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.phuza.api.NetResult
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.repo.MessagesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class MessagesSyncScheduler(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "MessagesSyncScheduler"

    private val msgsRepo by lazy { MessagesRepository(RetrofitInstance.messageApi) }
    private val local by lazy {
        MessagesLocalRepo(
            AppDatabase.getInstance(applicationContext).messageDao()
        )
    }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!NetworkChecker.isOnline(applicationContext)) {
            Log.d(TAG, "No network; retry later")
            return@withContext Result.retry()
        }

        val myUid = auth.currentUser?.uid.orEmpty()
        if (myUid.isBlank()) {
            Log.w(TAG, "No logged-in user; nothing to sync")
            return@withContext Result.success()
        }

        // ─────────── 1) Push pending outbound ───────────
        val pending = try {
            local.getPendingOutbound()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load pending outbound messages: ${t.message}", t)
            return@withContext Result.failure()
        }

        for (m in pending) {
            try {
                val sent = msgsRepo.send(
                    fromUid = myUid,
                    toUid = m.recipientId,
                    body = m.body,
                    clientId = m.messageId // idempotent key
                )

                when (sent) {
                    is NetResult.Ok -> {
                        val dto = sent.data
                        val ts = dto.createdAt ?: m.timeSent

                        // Keep peer* fields exactly as they were on the pending message
                        val updated = m.copy(
                            timeSent   = ts,
                            synced     = true,
                            outbound   = dto.fromUid == myUid,
                            inbound    = dto.toUid == myUid,
                            body       = dto.body ?: m.body
                        )

                        local.upsertMessages(listOf(updated))
                        Log.d(
                            TAG,
                            "Synced pending messageId=${m.messageId} -> synced=true, ts=$ts"
                        )
                    }

                    is NetResult.Err -> {
                        Log.w(
                            TAG,
                            "Send failed for messageId=${m.messageId}: ${sent.message} [code=${sent.code}]"
                        )
                        return@withContext if ((sent.code ?: 0) in 500..599) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }
                }
            } catch (io: IOException) {
                Log.e(TAG, "Network I/O error while sending messages: ${io.message}", io)
                return@withContext Result.retry()
            } catch (t: Throwable) {
                Log.e(
                    TAG,
                    "Unexpected error while sending messageId=${m.messageId}: ${t.message}",
                    t
                )
                // continue with other pending messages
            }
        }

        // 2) Pull inbound delta
        val since = local.maxInboundTs(myUid) ?: 0L

        return@withContext try {
            val res = msgsRepo.since(
                uid = myUid,
                sinceEpochMs = since
            )

            when (res) {
                is NetResult.Ok -> {
                    if (res.data.isNotEmpty()) {

                        // Only messages *to* me – avoid duplicating my own outbound ones
                        val inboundDtos = res.data.filter { it.toUid == myUid }

                        val hydrated = inboundDtos.map { d ->
                            val ts = d.createdAt ?: run {
                                Log.w(TAG, "Incoming message ${d.id} has no createdAt; using 0")
                                0L
                            }

                            MessageEntity(
                                messageId   = d.id ?: "${d.fromUid}_${d.toUid}_$ts",
                                chatId      = buildChatId(d.fromUid, d.toUid),
                                senderId    = d.fromUid,
                                recipientId = d.toUid,
                                body        = d.body.orEmpty(),
                                timeSent    = ts,
                                outbound    = false,             // it’s FROM them
                                inbound     = true,              // TO me
                                synced      = true,
                                // basic peer info (we at least know who "they" are)
                                peerUid         = d.fromUid,
                                peerName        = null,
                                peerUsername    = null,
                                peerAvatarBase64 = null
                            )
                        }

                        if (hydrated.isNotEmpty()) {
                            local.upsertMessages(hydrated)
                        }
                    }
                    Result.success()
                }

                is NetResult.Err -> {
                    Log.w(
                        TAG,
                        "Pull messages since=$since failed: ${res.message} [code=${res.code}]"
                    )
                    if ((res.code ?: 0) in 500..599) Result.retry() else Result.failure()
                }
            }
        } catch (io: IOException) {
            Log.e(TAG, "Network I/O error while pulling messages: ${io.message}", io)
            Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error while pulling messages: ${t.message}", t)
            Result.failure()
        }
    }

    private fun buildChatId(a: String, b: String): String =
        if (a <= b) "${a}_$b" else "${b}_$a"

    companion object {

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MessagesSyncScheduler>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(
                    WorkerCfg.backoffPolicy,
                    WorkerCfg.backoffDelaySeconds,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MessageWorkNames.MESSAGES_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun oneShot(context: Context) {
            val once = OneTimeWorkRequestBuilder<MessagesSyncScheduler>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(
                    WorkerCfg.backoffPolicy,
                    WorkerCfg.backoffDelaySeconds,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(once)
        }
    }
}

internal object WorkerCfg {
    val connectedConstraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val backoffPolicy = BackoffPolicy.EXPONENTIAL
    val backoffDelaySeconds = 3L
}

private object MessageWorkNames {
    const val MESSAGES_SYNC = "messages_sync"
}
