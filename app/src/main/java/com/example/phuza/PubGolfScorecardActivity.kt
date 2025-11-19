package com.example.phuza

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.phuza.adapters.PubGolfScorecardAdapter
import com.example.phuza.api.PubGolfApi
import com.example.phuza.api.PubGolfGame
import com.example.phuza.api.PubGolfGameViewModel
import com.example.phuza.api.PubGolfPlayer
import com.example.phuza.api.RespondInviteRequest
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.UiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PubGolfScorecardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"
        const val EXTRA_MY_UID = "extra_my_uid"

        // Used when opened from an invite notification
        const val EXTRA_INVITE_ID = "extra_invite_id"
        const val EXTRA_FROM_INVITE_NOTIFICATION = "extra_from_invite_notification"
    }

    private val vm: PubGolfGameViewModel by viewModels()

    private lateinit var rvScorecard: RecyclerView
    private var isSilentRefresh = false
    private lateinit var btnInviteFriends: MaterialButton
    private lateinit var btnStartGame: MaterialButton
    private lateinit var btnRules: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private lateinit var gameId: String
    private lateinit var myUid: String
    private var currentGame: PubGolfGame? = null
    private var isHost: Boolean = false

    private lateinit var tvCurrentHole: TextView
    private lateinit var btnPrevHole: ImageButton
    private lateinit var btnNextHole: ImageButton

    private var currentHoleNumber: Int = 1
    private lateinit var adapter: PubGolfScorecardAdapter

    // Invite / notification extras
    private var inviteId: String? = null
    private var launchedFromInviteNotification: Boolean = false

    // Simple coroutine scope for network calls from this screen
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pubGolfApi: PubGolfApi
        get() = RetrofitInstance.pubGolfApi

    // Auto-refresh job while screen is visible
    private var autoRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pubgolf_scorecard)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootScorecard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ----- Read extras -----
        gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: ""
        if (gameId.isBlank()) {
            Toast.makeText(this, "Missing gameId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Actual signed-in user ONLY, no "demo-user" fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
        myUid = intent.getStringExtra(EXTRA_MY_UID) ?: currentUser?.uid ?: run {
            Toast.makeText(this, "You must be signed in to view this game.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        inviteId = intent.getStringExtra(EXTRA_INVITE_ID)
        launchedFromInviteNotification =
            intent.getBooleanExtra(EXTRA_FROM_INVITE_NOTIFICATION, false)

        // ----- Views -----
        rvScorecard = findViewById(R.id.rvScorecard)
        btnInviteFriends = findViewById(R.id.btnInviteFriends)
        btnStartGame = findViewById(R.id.btnStartGame)
        btnRules = findViewById(R.id.btnRules)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        tvCurrentHole = findViewById(R.id.tvCurrentHole)
        btnPrevHole = findViewById(R.id.btnPrevHole)
        btnNextHole = findViewById(R.id.btnNextHole)

        // Back button
        findViewById<ImageButton>(R.id.btnBackScorecard).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Rules button
        btnRules.setOnClickListener {
            startActivity(Intent(this, PubGolfRulesActivity::class.java))
        }

        // Initial hole label
        tvCurrentHole.text = "Hole 1"

        btnPrevHole.setOnClickListener {
            val game = currentGame ?: return@setOnClickListener
            val maxHole = game.holes.size.coerceAtLeast(1)
            if (currentHoleNumber > 1) {
                setCurrentHole(currentHoleNumber - 1, maxHole)
            }
        }

        btnNextHole.setOnClickListener {
            val game = currentGame ?: return@setOnClickListener
            val maxHole = game.holes.size.coerceAtLeast(1)
            if (currentHoleNumber < maxHole) {
                setCurrentHole(currentHoleNumber + 1, maxHole)
            }
        }

        adapter = PubGolfScorecardAdapter(
            onHoleClick = { playerUid, holeNumber, currentStrokes ->
                if (!isHost) {
                    Toast.makeText(this, "Only the host can edit scores", Toast.LENGTH_SHORT).show()
                    return@PubGolfScorecardAdapter
                }
                showEditScoreDialog(playerUid, holeNumber, currentStrokes)
            },
            onHoleLongPress = { holeNumber ->
                showHoleDetailsBottomSheet(holeNumber)
            }
        )

        rvScorecard.layoutManager = LinearLayoutManager(this)
        rvScorecard.adapter = adapter

        // Start / End / Back button
        btnStartGame.setOnClickListener {
            val status = currentGame?.status

            when (status) {
                "finished" -> {
                    // After game is finished, always go back to dashboard
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }

                "pending" -> {
                    if (!isHost) {
                        Toast.makeText(this, "Only the host can start the game", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.startGame(gameId)
                    }
                }
                "active" -> {
                    if (!isHost) {
                        Toast.makeText(this, "Only the host can end the game", Toast.LENGTH_SHORT).show()
                    } else {
                        // Show custom yellow-themed End Game dialog
                        val dialogView = layoutInflater.inflate(R.layout.dialog_end_game, null)
                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .create()

                        dialogView.findViewById<Button>(R.id.btnEndGame).setOnClickListener {
                            dialog.dismiss()
                            vm.finishGame(gameId)
                        }

                        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                            dialog.dismiss()
                        }

                        dialog.show()
                    }
                }

                else -> {
                    // Fallback: try to start if we get an unknown state and I'm host
                    if (isHost) {
                        vm.startGame(gameId)
                    } else {
                        Toast.makeText(this, "Only the host can control the game", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnInviteFriends.setOnClickListener {
            if (!isHost) {
                Toast.makeText(this, "Only the host can invite players", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, PubGolfInviteFriendsActivity::class.java).apply {
                    putExtra(PubGolfInviteFriendsActivity.EXTRA_GAME_ID, gameId)
                }
                startActivity(i)
            }
        }

        // Swipe-to-refresh: reload the game from backend
        swipeRefresh.setOnRefreshListener {
            vm.loadGame(gameId)
        }

        observeGame()
        vm.loadGame(gameId)

        // As soon as we open this screen, treat this as the "current game"
        PubGolfPrefs.setLastGameId(this, gameId)
    }

    override fun onResume() {
        super.onResume()
        // Refresh when we come back
        vm.loadGame(gameId)
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        // Poll every 5 seconds while activity is in foreground
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (isActive) {
                delay(5000L)
                isSilentRefresh = true
                vm.loadGame(gameId)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun setCurrentHole(holeNumber: Int, maxHole: Int) {
        currentHoleNumber = holeNumber
        tvCurrentHole.text = "Hole $holeNumber of $maxHole"
        adapter.setCurrentHole(holeNumber)
    }

    private fun observeGame() {
        vm.gameState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    // Only show the classic "Loading…" text when not already refreshing
                    if (!swipeRefresh.isRefreshing && !isSilentRefresh) {
                        btnStartGame.isEnabled = false
                        btnStartGame.text = "Loading…"
                    }
                }
                is UiState.Error -> {
                    isSilentRefresh = false
                    swipeRefresh.isRefreshing = false
                    btnStartGame.isEnabled = true
                    btnStartGame.text = "Begin game"
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is UiState.Success -> {
                    isSilentRefresh = false
                    swipeRefresh.isRefreshing = false
                    val game = state.data

                    // Decide if I'm host
                    isHost = game.hostUid == myUid

                    // Sorting logic:
                    // - pending/active: host first, then by uid
                    // - finished: lowest total score first
                    val sortedPlayers: List<PubGolfPlayer> = when (game.status) {
                        "finished" -> {
                            game.players.sortedWith(
                                compareBy<PubGolfPlayer> { it.totalStrokes ?: Int.MAX_VALUE }
                                    .thenBy { it.uid }
                            )
                        }
                        else -> {
                            game.players.sortedWith(
                                compareByDescending<PubGolfPlayer> { it.uid == game.hostUid }
                                    .thenBy { it.uid }
                            )
                        }
                    }

                    val fixedGame = game.copy(players = sortedPlayers)
                    currentGame = fixedGame

                    val maxHole = fixedGame.holes.size.coerceAtLeast(1)
                    if (currentHoleNumber > maxHole) currentHoleNumber = maxHole
                    setCurrentHole(currentHoleNumber, maxHole)

                    updateHeaderUi(fixedGame)
                    adapter.submitGame(fixedGame, isHost)
                    adapter.setCurrentHole(currentHoleNumber)

                    // In finished state, host/non-host both see "Back to dashboard"
                    btnStartGame.isEnabled = when (fixedGame.status) {
                        "finished" -> true
                        else -> isHost
                    }

                    // If we came from an invite notification, prompt to join if not already in players
                    if (launchedFromInviteNotification && inviteId != null) {
                        val alreadyInGame = fixedGame.players.any { it.uid == myUid }
                        if (!alreadyInGame) {
                            // Only show once
                            launchedFromInviteNotification = false
                            showJoinGameDialog(inviteId!!, fixedGame)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun updateHeaderUi(game: PubGolfGame) {
        when (game.status) {
            "pending" -> {
                btnStartGame.text = if (isHost) "Begin game" else "Waiting to start"
            }
            "active" -> {
                // Host sees "End game", others see "Game in progress"
                btnStartGame.text = if (isHost) "End game" else "Game in progress"
            }
            "finished" -> {
                // Stay on this page, show final standings & back action
                btnStartGame.text = "Back to dashboard"
                btnStartGame.isEnabled = true

                // Stop auto refresh and clear "resume game"
                stopAutoRefresh()
                PubGolfPrefs.setLastGameId(this, null)
            }
            else -> btnStartGame.text = "Begin game"
        }
    }

    // ---- JOIN GAME POPUP (when opened from notification) ----
    private fun showJoinGameDialog(inviteId: String, game: PubGolfGame) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_game, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Set dynamic text
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Join ${game.title}?"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "You've been invited to join this pub golf game starting at ${game.origin.name}."

        // Button actions
        dialogView.findViewById<Button>(R.id.btnJoin).setOnClickListener {
            dialog.dismiss()
            joinGameFromInvite(inviteId)
        }

        dialogView.findViewById<Button>(R.id.btnDecline).setOnClickListener {
            dialog.dismiss()
            declineInvite(inviteId)
        }

        dialog.show()
    }


    private fun joinGameFromInvite(inviteId: String) {
        scope.launch {
            try {
                val resp = pubGolfApi.respondToInvite(
                    inviteId,
                    RespondInviteRequest(action = "accept")
                )
                val env = resp.body()
                if (resp.isSuccessful && env?.success != false) {
                    Toast.makeText(
                        this@PubGolfScorecardActivity,
                        "Joined game!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Remember this game as "current" for resume
                    PubGolfPrefs.setLastGameId(this@PubGolfScorecardActivity, gameId)

                    // Backend already adds player to game; just reload
                    vm.loadGame(gameId)
                } else {
                    Toast.makeText(
                        this@PubGolfScorecardActivity,
                        env?.message ?: "Failed to join game",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@PubGolfScorecardActivity,
                    e.message ?: "Error joining game",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun declineInvite(inviteId: String) {
        scope.launch {
            try {
                pubGolfApi.respondToInvite(
                    inviteId,
                    RespondInviteRequest(action = "decline")
                )
                // No need to reload game; user just stays as viewer
            } catch (_: Exception) {
                // Ignore errors on decline
            }
        }
    }

    private fun showEditScoreDialog(
        playerUid: String,
        holeNumber: Int,
        currentStrokes: Int?
    ) {
        val ctx = this
        val game = currentGame ?: return
        val hole = game.holes.firstOrNull { it.holeNumber == holeNumber } ?: return

        val inflater = LayoutInflater.from(ctx)
        val view = inflater.inflate(R.layout.dialog_strokes_penalties, null)

        val tvHoleInfo = view.findViewById<TextView>(R.id.tvHoleInfo)
        val tvParInfo = view.findViewById<TextView>(R.id.tvParInfo)
        val etBase = view.findViewById<EditText>(R.id.etBaseStrokes)
        val btnMinus = view.findViewById<ImageButton>(R.id.btnMinus)
        val btnPlus = view.findViewById<ImageButton>(R.id.btnPlus)
        val cbWater = view.findViewById<CheckBox>(R.id.cbWaterHazard)
        val cbBunker = view.findViewById<CheckBox>(R.id.cbBunker)
        val cbSpill = view.findViewById<CheckBox>(R.id.cbSpill)
        val cbCheat = view.findViewById<CheckBox>(R.id.cbCheating)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotalStrokes)

        tvHoleInfo.text = "Hole ${hole.holeNumber} – ${hole.name}"
        tvParInfo.text = "Par ${hole.par}"

        // Hazards
        cbWater.isEnabled = hole.waterHazard
        if (!hole.waterHazard) {
            cbWater.text = "Water hazard (not active on this hole)"
        }

        cbBunker.isEnabled = hole.bunkerHazard
        if (!hole.bunkerHazard) {
            cbBunker.text = "Bunker (not active on this hole)"
        }

        // Initial base strokes
        val baseStart = (currentStrokes ?: hole.par).coerceAtLeast(1)
        etBase.setText(baseStart.toString())

        fun readIntSafe(): Int {
            val t = etBase.text?.toString()?.trim()
            return t?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        }

        fun recalcTotal(): Int {
            val base = readIntSafe()
            var total = base
            if (cbWater.isChecked && cbWater.isEnabled) total += 1
            if (cbBunker.isChecked && cbBunker.isEnabled) total += 3
            if (cbSpill.isChecked) total += 3
            if (cbCheat.isChecked) total += 5
            tvTotal.text = "Total strokes: $total"
            return total
        }

        btnMinus.setOnClickListener {
            val cur = readIntSafe()
            val newVal = (cur - 1).coerceAtLeast(1)
            etBase.setText(newVal.toString())
            etBase.setSelection(etBase.text?.length ?: 0)
            recalcTotal()
        }

        btnPlus.setOnClickListener {
            val cur = readIntSafe()
            val newVal = (cur + 1).coerceAtMost(99)
            etBase.setText(newVal.toString())
            etBase.setSelection(etBase.text?.length ?: 0)
            recalcTotal()
        }

        etBase.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                recalcTotal()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        cbWater.setOnCheckedChangeListener { _, _ -> recalcTotal() }
        cbBunker.setOnCheckedChangeListener { _, _ -> recalcTotal() }
        cbSpill.setOnCheckedChangeListener { _, _ -> recalcTotal() }
        cbCheat.setOnCheckedChangeListener { _, _ -> recalcTotal() }

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(view)

        // Find the Save/Cancel buttons from the layout
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveScore)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelScore)

        btnSave.setOnClickListener {
            val total = recalcTotal()
            vm.updateScore(
                gameId = gameId,
                playerUid = playerUid,
                holeNumber = holeNumber,
                strokes = total
            )
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showHoleDetailsBottomSheet(holeNumber: Int) {
        val game = currentGame ?: return
        val hole = game.holes.firstOrNull { it.holeNumber == holeNumber } ?: return

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_pubgolf_hole_details, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvHoleTitle)
        val tvAddress = view.findViewById<TextView>(R.id.tvHoleAddress)
        val tvMeta = view.findViewById<TextView>(R.id.tvHoleMeta)
        val listDrinks = view.findViewById<LinearLayout>(R.id.listDrinks)

        tvTitle.text = "Hole ${hole.holeNumber} – ${hole.name}"
        tvAddress.text = hole.address

        val hazardParts = mutableListOf<String>()
        hazardParts += "Par ${hole.par}"
        if (hole.waterHazard) hazardParts += "Water hazard 💧"
        if (hole.bunkerHazard) hazardParts += "Bunker hazard 🏖️"
        tvMeta.text = hazardParts.joinToString(" • ")

        listDrinks.removeAllViews()
        if (hole.drinks.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No specific drinks assigned. Host can choose any drink for this hole."
                setTextColor(resources.getColor(R.color.mist, theme))
                textSize = 13f
                typeface = resources.getFont(R.font.sora_regular)
            }
            listDrinks.addView(tv)
        } else {
            hole.drinks.forEach { d ->
                val tv = TextView(this).apply {
                    text = "• ${d.name}  (par ${d.par})"
                    setTextColor(resources.getColor(R.color.milk, theme))
                    textSize = 14f
                    typeface = resources.getFont(R.font.sora_regular)
                }
                listDrinks.addView(tv)
            }
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        scope.cancel()
    }
}
