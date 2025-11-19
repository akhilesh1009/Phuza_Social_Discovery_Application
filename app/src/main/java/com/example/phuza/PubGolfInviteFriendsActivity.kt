package com.example.phuza

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.api.ApiService
import com.example.phuza.api.PubGolfApi
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.SendInvitesRequest
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.repo.FriendsRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class PubGolfInviteFriendsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"
    }

    private lateinit var gameId: String

    private lateinit var rvFriends: RecyclerView
    private lateinit var btnSendInvites: MaterialButton
    private lateinit var tvEmptyFriends: TextView

    private lateinit var adapter: PubGolfInviteFriendsAdapter

    // Use your existing Retrofit + repository setup
    private val apiService: ApiService get() = RetrofitInstance.api
    private val pubGolfApi: PubGolfApi get() = RetrofitInstance.pubGolfApi

    private val friendsRepo by lazy {
        FriendsRepository(
            api = apiService,
            auth = FirebaseAuth.getInstance(),
            db = FirebaseFirestore.getInstance()
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure this matches the new XML filename
        setContentView(R.layout.activity_pub_golf_invites)

        gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: ""
        if (gameId.isBlank()) {
            Toast.makeText(this, "Missing gameId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // New header style: back ImageButton instead of MaterialToolbar
        findViewById<ImageButton>(R.id.btnBackInviteFriends).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        rvFriends = findViewById(R.id.rvFriends)
        btnSendInvites = findViewById(R.id.btnSendInvites)
        tvEmptyFriends = findViewById(R.id.tvEmptyFriends)

        adapter = PubGolfInviteFriendsAdapter { selected ->
            btnSendInvites.isEnabled = selected.isNotEmpty()
        }

        rvFriends.layoutManager = LinearLayoutManager(this)
        rvFriends.adapter = adapter

        btnSendInvites.isEnabled = false
        btnSendInvites.setOnClickListener { sendInvites() }

        loadFriendsWhoAreAccepted()
    }

    private fun loadFriendsWhoAreAccepted() {
        scope.launch {
            try {
                // 1) Observe my friendships once, get accepted ones
                val obs = friendsRepo.observeMyFriendships().first()
                val friendIds = obs.statusByOtherUid
                    .filterValues { it == FriendshipStatus.follow }
                    .keys

                if (friendIds.isEmpty()) {
                    adapter.submitList(emptyList<UserDto>())
                    tvEmptyFriends.visibility = View.VISIBLE
                    Toast.makeText(
                        this@PubGolfInviteFriendsActivity,
                        "You have no accepted friends yet.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // 2) Fetch all users via your backend
                val envelope = friendsRepo.listUsers(q = null)
                if (!envelope.success || envelope.data == null) {
                    tvEmptyFriends.visibility = View.VISIBLE
                    Toast.makeText(
                        this@PubGolfInviteFriendsActivity,
                        envelope.message ?: "Error loading users",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val allUsers: List<UserDto> = envelope.data
                val onlyFriends = allUsers.filter { friendIds.contains(it.uid) }

                adapter.submitList(onlyFriends)
                tvEmptyFriends.visibility =
                    if (onlyFriends.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                tvEmptyFriends.visibility = View.VISIBLE
                Toast.makeText(
                    this@PubGolfInviteFriendsActivity,
                    e.message ?: "Error loading friends",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun sendInvites() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        btnSendInvites.isEnabled = false
        btnSendInvites.text = "Sending…"

        scope.launch {
            try {
                val resp = pubGolfApi.sendInvites(
                    gameId,
                    SendInvitesRequest(toUserIds = ids)
                )
                val env = resp.body()

                if (resp.isSuccessful && env?.success != false) {
                    Toast.makeText(
                        this@PubGolfInviteFriendsActivity,
                        "Invites sent!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@PubGolfInviteFriendsActivity,
                        env?.message ?: "Error sending invites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@PubGolfInviteFriendsActivity,
                    e.message ?: "Error sending invites",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnSendInvites.isEnabled = true
                btnSendInvites.text = "Send invites"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
