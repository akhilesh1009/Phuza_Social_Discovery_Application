package com.example.phuza

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.api.PubGolfGame
import com.example.phuza.api.PubGolfGameViewModel
import com.example.phuza.api.UiState
import com.google.android.material.appbar.MaterialToolbar

class PubGolfResultsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"
    }

    private val vm: PubGolfGameViewModel by viewModels()
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: ResultsAdapter
    private lateinit var gameId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pub_golf_results)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootPubGolfResults)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }

        gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: ""
        if (gameId.isBlank()) {
            Toast.makeText(this, "Missing gameId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<MaterialToolbar>(R.id.topBarResults)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rvResults = findViewById(R.id.rvResults)
        adapter = ResultsAdapter()
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        vm.gameState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {}
                is UiState.Error -> Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                is UiState.Success -> renderResults(state.data)
                else -> {}
            }
        }

        vm.loadGame(gameId)
    }

    private fun renderResults(game: PubGolfGame) {
        val holesCount = game.holes.size
        val sorted = game.players.sortedBy { it.totalStrokes ?: Int.MAX_VALUE }
        adapter.submit(sorted, holesCount)
    }

    // --- adapter for results list ---
    private class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.VH>() {

        data class Row(
            val position: Int,
            val name: String,
            val totalStrokes: Int?,
            val scoreToPar: Int?
        )

        private var rows: List<Row> = emptyList()

        fun submit(players: List<com.example.phuza.api.PubGolfPlayer>, holesCount: Int) {
            rows = players.mapIndexed { index, p ->
                Row(
                    position = index + 1,
                    name = p.uid, // replace with display name if you have it
                    totalStrokes = p.totalStrokes,
                    scoreToPar = p.scoreToPar
                )
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                setPadding(8, 12, 8, 12)
                textSize = 15f
                setTextColor(parent.context.getColor(R.color.milk))
            }
            return VH(tv)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(rows[position])
        }

        class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            fun bind(row: Row) {
                val tv = itemView as TextView
                val total = row.totalStrokes?.toString() ?: "–"
                val toPar = when {
                    row.scoreToPar == null -> ""
                    row.scoreToPar > 0 -> " (+${row.scoreToPar})"
                    row.scoreToPar < 0 -> " (${row.scoreToPar})"
                    else -> " (E)"
                }
                tv.text = "#${row.position}  ${row.name}   •   $total strokes$toPar"
            }
        }
    }
}
