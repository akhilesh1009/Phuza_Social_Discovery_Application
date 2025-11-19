package com.example.phuza

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.phuza.adapters.Message
import com.example.phuza.adapters.MessagesAdapter
import com.example.phuza.api.NetResult
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.offline.AppDatabase
import com.example.phuza.offline.MessageEntity
import com.example.phuza.offline.MessagesLocalRepo
import com.example.phuza.offline.MessagesSyncScheduler
import com.example.phuza.utils.AppFirebaseMessagingService
import com.example.phuza.utils.ImageUtils
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class ConversationActivity : BaseActivity() {

    companion object {
        const val EXTRA_CHAT_ID            = "chatId"
        const val EXTRA_CHAT_TITLE         = "chatTitle"
        const val EXTRA_PEER_UID           = "peerUid"
        const val EXTRA_PEER_FIRST_NAME    = "peerFirstName"
        const val EXTRA_PEER_USERNAME      = "peerUsername"
        const val EXTRA_PEER_AVATAR_BASE64 = "peerAvatarBase64"

        private const val TAG = "ConversationActivity"
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { com.example.phuza.repo.MessagesRepository(RetrofitInstance.messageApi) }

    private val localRepo by lazy {
        MessagesLocalRepo(
            AppDatabase.getInstance(applicationContext).messageDao()
        )
    }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessagesAdapter
    private lateinit var imgAvatar: ShapeableImageView   // <── header avatar

    private var pollJob: Job? = null
    private var lastSeen: Long = 0L

    private var myUid: String? = null
    private lateinit var chatId: String
    private lateinit var peerUid: String

    // peer meta we cache into each MessageEntity for offline use
    private var peerFirstName: String? = null
    private var peerUsername: String? = null
    private var peerAvatarBase64: String? = null

    private var messageReceiver: BroadcastReceiver? = null

    // ───────────────── lifecycle ─────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        myUid = auth.currentUser?.uid
        if (myUid == null) {
            Log.e(TAG, "User not authenticated")
            finish()
            return
        }

        val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Chat"

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: run {
            Log.e(TAG, "Missing chatId extra")
            finish()
            return
        }

        peerUid = intent.getStringExtra(EXTRA_PEER_UID) ?: run {
            Log.e(TAG, "Missing peerUid extra")
            finish()
            return
        }

        // read peer meta if ChatsActivity provided it
        peerFirstName    = intent.getStringExtra(EXTRA_PEER_FIRST_NAME)
        peerUsername     = intent.getStringExtra(EXTRA_PEER_USERNAME)
        peerAvatarBase64 = intent.getStringExtra(EXTRA_PEER_AVATAR_BASE64)

        Log.d(TAG, "My UID=$myUid, Peer UID=$peerUid, Chat ID=$chatId")
        Log.d(TAG, "peerFirstName=$peerFirstName, peerUsername=$peerUsername, avatar len=${peerAvatarBase64?.length}")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvChatTitle).text = chatTitle

        // header avatar
        imgAvatar = findViewById(R.id.imgAvatar)
        bindAvatar(peerAvatarBase64)

        recycler = findViewById(R.id.recyclerMessages)
        adapter = MessagesAdapter()
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // UI always driven from Room
        observeLocalChat()

        // Seed Room once from API (if online)
        loadInitialFromApi()

        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val et = findViewById<TextInputEditText>(R.id.etMessage)
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                sendMessageOfflineFirst(text)
                et.setText("")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: registering broadcast receiver for peer=$peerUid")
        setupMessageReceiver(peerUid)
        startPolling()   // only pulls inbound messages now
    }

    override fun onStop() {
        Log.d(TAG, "onStop: stopping polling and unregistering receiver")
        pollJob?.cancel()
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
                Log.d(TAG, "✓ Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        messageReceiver = null
        super.onStop()
    }

    // ───────────────── Room observer ─────────────────

    private fun observeLocalChat() {
        val uid = myUid ?: return
        lifecycleScope.launch {
            localRepo.observeChat(chatId).collectLatest { entities ->
                val messages = entities
                    .sortedBy { it.timeSent }
                    .map { e ->
                        Message(
                            id        = e.messageId,
                            text      = e.body,
                            isMe      = e.senderId == uid,
                            timestamp = e.timeSent,
                            isPending = !e.synced && e.outbound
                        )
                    }

                val withDividers = MessagesAdapter.insertDateDividers(messages)
                adapter.submitList(withDividers) {
                    scrollToBottom()
                }
                lastSeen = max(lastSeen, messages.maxOfOrNull { it.timestamp } ?: 0L)
            }
        }
    }

    // ───────────────── Initial load from API into Room ─────────────────

    private fun loadInitialFromApi() {
        val uid = myUid ?: return
        lifecycleScope.launch {
            val hasLocal = localRepo.hasMessagesInChat(chatId)
            if (hasLocal) {
                Log.d(TAG, "loadInitialFromApi: local messages already present, skip seeding")
                return@launch
            }

            when (val res = repo.withPeer(uid = uid, peerId = peerUid, limit = 100)) {
                is NetResult.Ok -> {
                    val list = res.data.mapNotNull { d ->
                        val ts = d.createdAt ?: 0L
                        if (ts == 0L) return@mapNotNull null

                        val peerForRow =
                            if (d.fromUid == uid) d.toUid else d.fromUid

                        MessageEntity(
                            messageId   = d.id ?: "${d.fromUid}_${d.toUid}_$ts",
                            chatId      = buildChatId(d.fromUid, d.toUid),
                            senderId    = d.fromUid,
                            recipientId = d.toUid,
                            body        = d.body.orEmpty(),
                            timeSent    = ts,
                            outbound    = d.fromUid == uid,
                            inbound     = d.toUid == uid,
                            synced      = true,
                            peerUid          = peerForRow,
                            peerName         = if (peerForRow == peerUid) peerFirstName else null,
                            peerUsername     = if (peerForRow == peerUid) peerUsername else null,
                            peerAvatarBase64 = if (peerForRow == peerUid) peerAvatarBase64 else null
                        )
                    }
                    localRepo.upsertMessages(list)
                    lastSeen = list.maxOfOrNull { it.timeSent } ?: lastSeen
                }
                is NetResult.Err -> {
                    Log.e(TAG, "Failed to load initial messages: ${res.message}")
                }
            }
        }
    }


    // ───────────────── Offline-first send ─────────────────

    private fun sendMessageOfflineFirst(text: String) {
        val uid = myUid ?: return
        val now = System.currentTimeMillis()

        lifecycleScope.launch {
            val localId = "local-$now-${System.nanoTime()}"

            val entity = MessageEntity(
                messageId   = localId,
                chatId      = chatId,
                senderId    = uid,
                recipientId = peerUid,
                body        = text,
                timeSent    = now,
                outbound    = true,
                inbound     = false,
                synced      = false,
                peerUid          = peerUid,
                peerName         = peerFirstName,
                peerUsername     = peerUsername,
                peerAvatarBase64 = peerAvatarBase64
            )

            localRepo.upsertMessages(listOf(entity))
            lastSeen = max(lastSeen, now)

            MessagesSyncScheduler.oneShot(this@ConversationActivity)
        }
    }

    // ───────────────── Polling inbound ─────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(4000)
                val uid = myUid ?: return@launch
                val since = lastSeen

                when (val res = repo.since(uid = uid, sinceEpochMs = since)) {
                    is NetResult.Ok -> {
                        val inboundDtos = res.data.filter { it.toUid == uid }

                        val newEntities = inboundDtos.mapNotNull { d ->
                            val ts = d.createdAt ?: return@mapNotNull null
                            val peerForRow =
                                if (d.fromUid == uid) d.toUid else d.fromUid

                            MessageEntity(
                                messageId   = d.id ?: "${d.fromUid}_${d.toUid}_$ts",
                                chatId      = buildChatId(d.fromUid, d.toUid),
                                senderId    = d.fromUid,
                                recipientId = d.toUid,
                                body        = d.body.orEmpty(),
                                timeSent    = ts,
                                outbound    = d.fromUid == uid,
                                inbound     = d.toUid == uid,
                                synced      = true,
                                peerUid          = peerForRow,
                                peerName         = if (peerForRow == peerUid) peerFirstName else null,
                                peerUsername     = if (peerForRow == peerUid) peerUsername else null,
                                peerAvatarBase64 = if (peerForRow == peerUid) peerAvatarBase64 else null
                            )
                        }

                        if (newEntities.isNotEmpty()) {
                            localRepo.upsertMessages(newEntities)
                            lastSeen = max(lastSeen, newEntities.maxOf { it.timeSent })
                        }
                    }
                    is NetResult.Err -> {
                        Log.w(TAG, "Polling error: ${res.message}")
                    }
                }
            }
        }
    }

    private fun scrollToBottom() {
        recycler.post {
            if (adapter.itemCount > 0) {
                recycler.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    // ───────────────── Broadcast receiver ─────────────────

    private fun setupMessageReceiver(peerUidParam: String) {
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            } catch (_: Exception) { }
        }

        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedPeerUid =
                    intent?.getStringExtra(AppFirebaseMessagingService.EXTRA_PEER_UID)
                Log.d(TAG, "===== BROADCAST RECEIVED =====")
                Log.d(TAG, "Received peerUid: $receivedPeerUid, current peerUid: $peerUidParam")

                if (receivedPeerUid == peerUidParam) {
                    Log.d(TAG, "✓ Message from current chat peer, triggering one-shot sync")
                    MessagesSyncScheduler.oneShot(this@ConversationActivity)
                } else {
                    Log.d(TAG, "✗ Message from different chat, ignoring")
                }
            }
        }

        val filter = IntentFilter(AppFirebaseMessagingService.ACTION_NEW_MESSAGE)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageReceiver!!, filter)
        Log.d(
            TAG,
            "✓ Broadcast receiver registered for action: ${AppFirebaseMessagingService.ACTION_NEW_MESSAGE}"
        )
    }

    private fun buildChatId(a: String, b: String): String =
        if (a <= b) "${a}_$b" else "${b}_$a"

    // ───────────────── Avatar helper ─────────────────

    private fun bindAvatar(avatarValue: String?) {
        imgAvatar.scaleType = ImageView.ScaleType.CENTER_CROP

        if (avatarValue.isNullOrBlank()) {
            imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
            return
        }

        // base64?
        if (looksLikeBase64Image(avatarValue)) {
            val bmp = ImageUtils.decodeBase64ToBitmap(avatarValue)
            if (bmp != null) {
                imgAvatar.setImageBitmap(bmp)
            } else {
                imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
            }
            return
        }

        // local drawable name?
        val resId = resources.getIdentifier(avatarValue, "drawable", packageName)
        if (resId != 0) {
            imgAvatar.setImageResource(resId)
            return
        }

        // otherwise treat as URL
        Glide.with(this)
            .load(avatarValue)
            .placeholder(R.drawable.avatar_no_avatar)
            .error(R.drawable.avatar_no_avatar)
            .into(imgAvatar)
    }

    private fun looksLikeBase64Image(value: String): Boolean {
        if (value.startsWith("data:image")) return true
        if (value.length < 100) return false
        return value.all { it.isLetterOrDigit() || it in "+/=\n\r" }
    }
}
