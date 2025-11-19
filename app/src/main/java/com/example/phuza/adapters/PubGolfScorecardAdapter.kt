package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.api.PubGolfGame
import com.example.phuza.api.PubGolfPlayer

class PubGolfScorecardAdapter(
    private val onHoleClick: (playerUid: String, holeNumber: Int, currentStrokes: Int?) -> Unit,
    private val onHoleLongPress: (holeNumber: Int) -> Unit
) : RecyclerView.Adapter<PubGolfScorecardAdapter.RowVH>() {

    private var game: PubGolfGame? = null
    private var isHost: Boolean = false
    private var currentHoleNumber: Int = 1

    fun submitGame(newGame: PubGolfGame, isHost: Boolean) {
        this.game = newGame
        this.isHost = isHost
        notifyDataSetChanged()
    }

    fun setCurrentHole(holeNumber: Int) {
        currentHoleNumber = holeNumber
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pubgolf_score_row, parent, false)
        return RowVH(v)
    }

    override fun getItemCount(): Int = game?.players?.size ?: 0

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val g = game ?: return
        val player = g.players[position]
        holder.bind(
            game = g,
            player = player,
            isHost = isHost,
            currentHoleNumber = currentHoleNumber,
            onHoleClick = onHoleClick,
            onHoleLongPress = onHoleLongPress
        )
    }

    class RowVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvPlayerName: TextView = itemView.findViewById(R.id.tvPlayerName)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvTotalStrokes)
        private val tvToPar: TextView = itemView.findViewById(R.id.tvScoreToPar)

        private data class HoleCell(
            val root: View,
            val strokesView: TextView,
            val vsParView: TextView
        )

        private val holeCells: List<HoleCell>

        init {
            val rowHoles: LinearLayout = itemView.findViewById(R.id.rowHoles)
            val cells = mutableListOf<HoleCell>()
            for (i in 0 until rowHoles.childCount) {
                val cellRoot = rowHoles.getChildAt(i)
                val strokesView: TextView = cellRoot.findViewById(R.id.tvHoleStrokes)
                val vsParView: TextView = cellRoot.findViewById(R.id.tvHoleVsPar)
                cells += HoleCell(cellRoot, strokesView, vsParView)
            }
            holeCells = cells
        }
        fun bind(
            game: PubGolfGame,
            player: PubGolfPlayer,
            isHost: Boolean,
            currentHoleNumber: Int,
            onHoleClick: (playerUid: String, holeNumber: Int, currentStrokes: Int?) -> Unit,
            onHoleLongPress: (holeNumber: Int) -> Unit
        ) {
            // name / host flag
            val baseName = player.name?.takeIf { it.isNotBlank() } ?: player.uid
            val displayName = if (player.uid == game.hostUid) {
                "$baseName (host)"
            } else {
                baseName
            }
            tvPlayerName.text = displayName

            val strokes = player.strokes ?: emptyList()
            val ctx = itemView.context

            val colorActive = ContextCompat.getColor(ctx, R.color.milk)
            val colorDim = ContextCompat.getColor(ctx, R.color.mist)
            val colorOverPar = ContextCompat.getColor(ctx, R.color.yellow)   // tune these
            val colorUnderPar = ContextCompat.getColor(ctx, R.color.aquamarine)    // define in colors.xml
            val colorEvenPar = ContextCompat.getColor(ctx, R.color.lavender)

            holeCells.forEachIndexed { index, cell ->
                val holeNumber = index + 1
                val s = strokes.getOrNull(index)               // player strokes for this hole
                val hole = game.holes.getOrNull(index)
                val par = hole?.par

                // TOP: strokes (number of strokes)
                cell.strokesView.text = s?.toString() ?: "–"

                // BOTTOM: vs par – just +2 / -1 / E
                if (par == null || s == null) {
                    cell.vsParView.text = "–"
                    cell.vsParView.setTextColor(colorDim)
                } else {
                    val diff = s - par
                    val label = when {
                        diff > 0 -> "+$diff"
                        diff < 0 -> diff.toString()
                        else -> "E"
                    }
                    cell.vsParView.text = label

                    val diffColor = when {
                        diff > 0 -> colorOverPar
                        diff < 0 -> colorUnderPar
                        else -> colorEvenPar
                    }
                    cell.vsParView.setTextColor(diffColor)
                }

                // highlight current hole
                if (holeNumber == currentHoleNumber) {
                    cell.root.alpha = 1f
                    cell.strokesView.setTextColor(colorActive)
                } else {
                    cell.root.alpha = 0.6f
                    cell.strokesView.setTextColor(colorDim)
                }

                // tap (edit) only for host + active + current hole
                if (isHost && game.status == "active" && holeNumber == currentHoleNumber) {
                    cell.root.isEnabled = true
                    cell.root.setOnClickListener {
                        onHoleClick(player.uid, holeNumber, s)
                    }
                } else {
                    cell.root.isEnabled = false
                    cell.root.setOnClickListener(null)
                }

                // long press → hole details
                cell.root.setOnLongClickListener {
                    onHoleLongPress(holeNumber)
                    true
                }
            }

            // header totals
            val total = player.totalStrokes
            val toPar = player.scoreToPar

            tvTotal.text = total?.toString() ?: "–"
            tvToPar.text = when {
                toPar == null -> "–"
                toPar > 0 -> "+$toPar"
                else -> toPar.toString()
            }
        }
    }
}
