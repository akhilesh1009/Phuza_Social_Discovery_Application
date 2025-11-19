package com.example.phuza

import com.google.android.material.R as MaterialR
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.adapters.ChatPreview
import com.example.phuza.adapters.ChatsAdapter
import com.example.phuza.adapters.ContactsAdapter
import com.example.phuza.data.Friendship
import com.example.phuza.data.User
import com.example.phuza.offline.AppDatabase
import com.example.phuza.offline.MessagesLocalRepo
import com.example.phuza.utils.AppFirebaseMessagingService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class ChatsActivity : BaseActivity() {

    private lateinit var recentAdapter: ChatsAdapter
    private var fullChatList: List<ChatPreview> = emptyList()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fs by lazy { FirebaseFirestore.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }

    // Local Room cache for offline chat previews / messages
    private val localMessages by lazy {
        MessagesLocalRepo(
            AppDatabase.getInstance(applicationContext).messageDao()
        )
    }

    private lateinit var bottomNav: BottomNavigationView
    private var messageReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "ChatsActivity"
    }

    // ───────────────── lifecycle ─────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        applyInsets(R.id.main)

        bottomNav = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_chats)

        // Recent chats list
        findViewById<RecyclerView>(R.id.rvRecent).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            recentAdapter = ChatsAdapter { chat ->
                // We only know displayName + avatar here; treat displayName as the name
                startConversation(
                    peerUid          = chat.peerUid,
                    displayName      = chat.displayName,
                    peerName         = chat.displayName,
                    peerUsername     = null,          // no separate username here
                    peerAvatarBase64 = chat.avatar    // may be base64 / url / null
                )
            }
            adapter = recentAdapter
        }

        findViewById<View>(R.id.btnAddChat).setOnClickListener { showNewChatSheet() }
        findViewById<View>(R.id.btnSearch).setOnClickListener { showSearchChatsSheet() }

        findViewById<EditText>(R.id.etSearchChats)
            ?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    filterChats(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })

        refreshRecent()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_chats
        setupMessageReceiver()
        refreshRecent()
    }

    override fun onPause() {
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        messageReceiver = null
        super.onPause()
    }

    // ───────────────── search / filter in-memory list ─────────────────

    private fun filterChats(query: String) {
        if (query.isBlank()) {
            recentAdapter.submitList(fullChatList)
        } else {
            val filtered = fullChatList.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                        it.lastMessage.contains(query, ignoreCase = true)
            }
            recentAdapter.submitList(filtered)
        }
    }

    // ───────────────── recent chats: online vs offline ─────────────────

    private fun refreshRecent() {
        val myUid = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val previews: List<ChatPreview> = if (isOnline(this@ChatsActivity)) {
                    loadRecentFromFirestore(myUid)
                } else {
                    Log.d(TAG, "refreshRecent: offline -> using Room cache")
                    loadRecentFromLocal(myUid)
                }

                fullChatList = previews

                val emptyState = findViewById<View>(R.id.emptyChatsState)
                val recentLabel = findViewById<View>(R.id.tvRecent)

                if (previews.isEmpty()) {
                    emptyState?.visibility = View.VISIBLE
                    recentLabel?.visibility = View.GONE
                } else {
                    emptyState?.visibility = View.GONE
                    recentLabel?.visibility = View.VISIBLE
                }

                recentAdapter.submitList(previews)
            } catch (t: Throwable) {
                Log.e(TAG, "refreshRecent exception", t)
            }
        }
    }

    // Online: Firestore Chats
    private suspend fun loadRecentFromFirestore(myUid: String): List<ChatPreview> =
        withContext(Dispatchers.IO) {
            val chatsSnapshot = fs.collection("Chats")
                .whereArrayContains("participants", myUid)
                .limit(50)
                .get()
                .await()

            val peerUids: Set<String> = chatsSnapshot.documents.mapNotNull { doc ->
                val participants = doc.get("participants") as? List<*>
                    ?: return@mapNotNull null
                participants.firstOrNull { it != myUid }?.toString()
            }.toSet()

            val users = resolveUsersOneByOne(peerUids)
            val nameMap = users.associate { u ->
                val displayName = buildDisplayName(u)
                u.uid to displayName
            }

            val avatarMap = fetchAvatars(peerUids)

            chatsSnapshot.documents.mapNotNull { doc ->
                val chatId = doc.id
                val participants = doc.get("participants") as? List<*>
                    ?: return@mapNotNull null
                val peerUid = participants.firstOrNull { it != myUid }?.toString()
                    ?: return@mapNotNull null

                val lastMessage = doc.get("lastMessage") as? Map<*, *>

                val lastMessageText = (lastMessage?.get("preview")
                    ?: lastMessage?.get("body"))
                    ?.toString()
                    ?: "No messages yet"

                val lastTimestamp = (lastMessage?.get("createdAt") as? Long)
                    ?: (lastMessage?.get("updatedAt") as? Long)
                    ?: 0L

                val displayName = nameMap[peerUid] ?: "User ${peerUid.take(6)}"
                val avatarValue = avatarMap[peerUid]

                ChatPreview(
                    chatId        = chatId,
                    peerUid       = peerUid,
                    displayName   = displayName,
                    lastMessage   = lastMessageText,
                    lastTimestamp = lastTimestamp,
                    avatar        = avatarValue
                )
            }.sortedByDescending { it.lastTimestamp }
        }

    // Offline: Room messages
    private suspend fun loadRecentFromLocal(myUid: String): List<ChatPreview> =
        withContext(Dispatchers.IO) {
            val latest = localMessages.latestChatsForUser(myUid)
            if (latest.isEmpty()) {
                return@withContext emptyList<ChatPreview>()
            }

            latest.map { m ->
                val peerUid =
                    if (m.senderId == myUid) m.recipientId else m.senderId

                val displayName =
                    m.peerName?.takeIf { it.isNotBlank() }
                        ?: m.peerUsername?.takeIf { it.isNotBlank() }
                        ?: "User ${peerUid.take(6)}"

                ChatPreview(
                    chatId        = m.chatId,
                    peerUid       = peerUid,
                    displayName   = displayName,
                    lastMessage   = m.body,
                    lastTimestamp = m.timeSent,
                    avatar        = m.peerAvatarBase64
                )
            }.sortedByDescending { it.lastTimestamp }
        }

    private suspend fun fetchAvatars(peerUids: Set<String>): Map<String, String?> =
        withContext(Dispatchers.IO) {
            peerUids.map { uid ->
                async {
                    val snap = runCatching {
                        rtdb.reference.child("users").child(uid).child("avatar").get().await()
                    }.getOrNull()
                    uid to snap?.getValue(String::class.java)
                }
            }.awaitAll().toMap()
        }

    // ───────────────── New Chat bottom sheet ─────────────────

    private fun showNewChatSheet() {
        val dlg = BottomSheetDialog(this, R.style.AppBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_new_chat, null)
        dlg.setContentView(view)
        forceExpand(dlg)

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        val progress = view.findViewById<View>(R.id.progressContacts)
        val empty = view.findViewById<View>(R.id.emptyState)

        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(
                peerUid          = user.uid,
                displayName      = buildDisplayName(user),
                peerName         = user.firstName.takeIf { it.isNotBlank() },
                peerUsername     = user.username.takeIf { it.isNotBlank() },
                peerAvatarBase64 = user.avatar.takeIf { !it.isNullOrBlank() }
            )
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        progress?.visibility = View.VISIBLE
        rv.visibility = View.GONE
        empty?.visibility = View.GONE

        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { loadFriendsForChat() }
            Log.d(TAG, "showNewChatSheet: contacts.size=${contacts.size}")
            progress?.visibility = View.GONE

            if (contacts.isEmpty()) {
                rv.visibility = View.GONE
                empty?.visibility = View.VISIBLE
            } else {
                adapter.submitListSafe(contacts)
                rv.visibility = View.VISIBLE
                empty?.visibility = View.GONE
                rv.itemAnimator = null
                //rv.setHasFixedSize(true)
                val searchEt = view.findViewById<EditText>(R.id.etSearch)
                searchEt?.addTextChangedListener(filterWatcher(adapter))
            }
        }

        dlg.show()
    }

    // ───────────────── Search Chats bottom sheet ─────────────────

    private fun showSearchChatsSheet() {
        val dlg = BottomSheetDialog(this, R.style.AppBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_search_chat, null)
        dlg.setContentView(view)
        forceExpand(dlg)

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        val progress = view.findViewById<View>(R.id.progressResults)
        val empty = view.findViewById<View>(R.id.emptyResults)

        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(
                peerUid          = user.uid,
                displayName      = buildDisplayName(user),
                peerName         = user.firstName.takeIf { it.isNotBlank() },
                peerUsername     = user.username.takeIf { it.isNotBlank() },
                peerAvatarBase64 = null
            )
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        progress?.visibility = View.VISIBLE
        rv.visibility = View.GONE
        empty?.visibility = View.GONE

        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { loadFriendsForChat() }
            Log.d(TAG, "showSearchChatsSheet: contacts.size=${contacts.size}")
            progress?.visibility = View.GONE

            if (contacts.isEmpty()) {
                rv.visibility = View.GONE
                empty?.visibility = View.VISIBLE
            } else {
                adapter.submitListSafe(contacts)
                rv.visibility = View.VISIBLE
                empty?.visibility = View.GONE
                rv.itemAnimator = null
                //rv.setHasFixedSize(true)
                val searchEt = view.findViewById<EditText>(R.id.etSearch)
                searchEt?.addTextChangedListener(filterWatcher(adapter))
            }
        }

        dlg.show()
    }

    private fun forceExpand(dlg: BottomSheetDialog) {
        dlg.behavior.skipCollapsed = true
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.setOnShowListener {
            val sheet =
                dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val beh = BottomSheetBehavior.from(it)
                beh.peekHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                beh.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun filterWatcher(adapter: ContactsAdapter) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            adapter.filter(s?.toString().orEmpty())
        }
    }

    // ───────────────── navigation to ConversationActivity ─────────────────

    private fun startConversation(
        peerUid: String,
        displayName: String,
        peerName: String? = null,
        peerUsername: String? = null,
        peerAvatarBase64: String? = null
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val chatId = buildChatId(myUid, peerUid)

        val intent = Intent(this@ChatsActivity, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ConversationActivity.EXTRA_CHAT_TITLE, displayName)
            putExtra(ConversationActivity.EXTRA_PEER_UID, peerUid)

            // cache meta for offline use
            putExtra(
                ConversationActivity.EXTRA_PEER_FIRST_NAME,
                peerName ?: displayName
            )
            putExtra(ConversationActivity.EXTRA_PEER_USERNAME, peerUsername)
            putExtra(ConversationActivity.EXTRA_PEER_AVATAR_BASE64, peerAvatarBase64)
        }
        startActivity(intent)
    }

    private fun buildChatId(a: String, b: String) =
        if (a <= b) "${a}_$b" else "${b}_$a"

    // ───────────────── friends list for starting chats ─────────────────

    private suspend fun loadFriendsForChat(): List<User> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            Log.d(TAG, "loadFriendsForChat: no current user, returning empty list")
            return@withContext emptyList<User>()
        }

        val friendshipsSnap = try {
            fs.collection("friendships")
                .get()
                .await()
        } catch (t: Throwable) {
            Log.e(TAG, "loadFriendsForChat: error fetching friendships", t)
            return@withContext emptyList<User>()
        }

        Log.d(TAG, "loadFriendsForChat: friendships count = ${friendshipsSnap.size()}")

        val friendIds = mutableSetOf<String>()

        for (doc in friendshipsSnap.documents) {
            Log.d(TAG, "loadFriendsForChat: doc=${doc.id}, data=${doc.data}")

            val f = doc.toObject(Friendship::class.java) ?: continue

            val status = f.status?.trim()?.lowercase(Locale.getDefault())
            val isAccepted = (status == "accepted" || status == "accept")

            val involvesMe = (f.user1Id == myUid || f.user2Id == myUid)

            if (isAccepted && involvesMe) {
                val friendId = when {
                    f.user1Id == myUid -> f.user2Id
                    f.user2Id == myUid -> f.user1Id
                    else -> null
                }

                if (!friendId.isNullOrBlank()) {
                    friendIds += friendId
                    Log.d(
                        TAG,
                        "loadFriendsForChat: found friendId=$friendId for myUid=$myUid"
                    )
                }
            } else {
                Log.d(
                    TAG,
                    "loadFriendsForChat: skipping doc=${doc.id} (status=$status, involvesMe=$involvesMe)"
                )
            }
        }

        Log.d(TAG, "loadFriendsForChat: friendIds=$friendIds")

        if (friendIds.isEmpty()) {
            Log.d(TAG, "loadFriendsForChat: no friends found, returning empty list")
            return@withContext emptyList<User>()
        }

        val users = resolveUsersOneByOne(friendIds).filter { it.uid != myUid }
        val sorted = users.sortedWith(
            compareBy(
                { it.firstName.lowercase(Locale.getDefault()) },
                { it.username.lowercase(Locale.getDefault()) }
            )
        )

        Log.d(TAG, "loadFriendsForChat: resolved ${sorted.size} users")
        sorted
    }

    private suspend fun resolveUsersOneByOne(uids: Set<String>): List<User> =
        withContext(Dispatchers.IO) {
            uids.map { uid ->
                async {
                    // 1. Try RTDB first
                    fetchRtdbUser(uid)
                    // 2. If missing, fall back to Firestore
                        ?: runCatching {
                            fs.collection("users").document(uid).get().await()
                        }.getOrNull()
                            ?.takeIf { it.exists() }
                            ?.toUserFromFs(uid)
                        // 3. Last fallback: UID only
                        ?: User(uid = uid)
                }
            }.awaitAll()
        }

    private fun DocumentSnapshot.toUserFromFs(fallbackUid: String): User {
        val uid = (getString("uid") ?: id).ifBlank { fallbackUid }

        val first = (getString("firstName") ?: getString("firstname") ?: "").trim()
        val nameRaw = (getString("name") ?: "").trim()
        val firstName =
            if (first.isNotEmpty()) first
            else if (nameRaw.contains(" ")) {
                nameRaw.split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
            } else nameRaw

        val username = getString("username").orEmpty()
        val email = getString("email").orEmpty()
        val updatedAt = readMillisFlexible("updatedAt")?.takeIf { it > 0 }
            ?: readMillisFlexible("createdAt") ?: 0L

        return User(
            uid = uid,
            firstName = firstName,
            username = username,
            email = email,
            createdAt = updatedAt
        )
    }

    private suspend fun fetchRtdbUser(uid: String): User? {
        return try {
            val snap = rtdb.reference.child("users").child(uid).get().await()
            if (!snap.exists()) return null

            val first = snap.child("firstName").getValue(String::class.java) ?: ""
            val username = snap.child("username").getValue(String::class.java) ?: ""
            val email = snap.child("email").getValue(String::class.java) ?: ""
            val avatar = snap.child("avatar").getValue(String::class.java)
            val updatedAt = snap.child("updatedAt").getValue(Long::class.java)
                ?: snap.child("createdAt").getValue(Long::class.java) ?: 0L

            User(
                uid = uid,
                firstName = first,
                username = username,
                email = email,
                avatar = avatar,
                createdAt = updatedAt
            )
        } catch (t: Throwable) {
            Log.e(TAG, "fetchRtdbUser($uid) failed", t); null
        }
    }

    private fun DocumentSnapshot.readMillisFlexible(field: String): Long? {
        val v = get(field) ?: return null
        return when (v) {
            is Number   -> v.toLong()
            is Timestamp -> v.toDate().time
            is Date     -> v.time
            is String   -> try {
                java.time.Instant.parse(v).toEpochMilli()
            } catch (_: Throwable) {
                v.toLongOrNull()
            }
            else        -> null
        }
    }

    // ───────────────── FCM local broadcast listener ─────────────────

    private fun setupMessageReceiver() {
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }

        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val peerUid =
                    intent?.getStringExtra(AppFirebaseMessagingService.EXTRA_PEER_UID)
                Log.d(TAG, "ChatsActivity: broadcast for peer=$peerUid, refreshing recent")
                refreshRecent()
            }
        }

        val filter = IntentFilter(AppFirebaseMessagingService.ACTION_NEW_MESSAGE)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(messageReceiver!!, filter)
    }

    // ───────────────── connectivity helper ─────────────────

    private fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildDisplayName(u: User): String {
        return when {
            u.firstName.isNotBlank() && u.username.isNotBlank() ->
                "${u.firstName} (${u.username})"
            u.firstName.isNotBlank() -> u.firstName
            u.username.isNotBlank()  -> u.username
            u.email.isNotBlank()     -> u.email
            else                     -> "User ${u.uid.take(6)}"
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val intent = Intent(this, DashboardActivity::class.java)
        // You probably don't need NEW_TASK here
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

}
