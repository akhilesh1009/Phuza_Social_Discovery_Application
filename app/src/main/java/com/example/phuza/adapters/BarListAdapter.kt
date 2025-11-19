package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.BarUi
import java.util.Locale

class BarListAdapter(
    private var recentBars: List<BarUi>,
    private var allBars: List<BarUi>,
    private val onBarClick: (BarUi) -> Unit,
    private val onRemoveRecent: (BarUi) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER_RECENT = 0
        private const val TYPE_RECENT = 1
        private const val TYPE_HEADER_ALL = 2
        private const val TYPE_ALL = 3
    }

    private var filteredRecent = recentBars
    private var filteredAll = allBars

    // ------------------- ViewHolders -------------------

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvHeader)
    }

    inner class BarVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvBarName)
    }

    // ------------------- Adapter Core -------------------

    override fun getItemViewType(position: Int): Int {

        val hasRecent = filteredRecent.isNotEmpty()

        return when {
            // Header: Recently Searched
            hasRecent && position == 0 ->
                TYPE_HEADER_RECENT

            // Recently searched items
            hasRecent && position in 1..filteredRecent.size ->
                TYPE_RECENT

            // Header: All Bars
            position == (if (hasRecent) filteredRecent.size + 1 else 0) ->
                TYPE_HEADER_ALL

            // Regular items
            else -> TYPE_ALL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return when (viewType) {

            TYPE_HEADER_RECENT, TYPE_HEADER_ALL -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                HeaderVH(v)
            }

            else -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_bars_list, parent, false)
                BarVH(v)
            }
        }
    }

    override fun getItemCount(): Int {
        val recentSection = if (filteredRecent.isNotEmpty()) filteredRecent.size + 1 else 0
        val allSection = filteredAll.size + if (filteredAll.isNotEmpty()) 1 else 0 // add header only if there are bars
        return recentSection + allSection
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val hasRecent = filteredRecent.isNotEmpty()

        when (holder) {

            is HeaderVH -> {
                val hasRecent = filteredRecent.isNotEmpty()

                holder.tvHeader.text =
                    if (hasRecent && position == 0)
                        "Recently Searched"
                    else
                        "All Bars"

            }

            is BarVH -> {
                val bar = getItemAt(position)
                holder.tvName.text = bar.name

                holder.itemView.setOnClickListener { onBarClick(bar) }
            }
        }
    }

    private fun getItemAt(position: Int): BarUi {
        val hasRecent = filteredRecent.isNotEmpty()

        // recent bars
        if (hasRecent && position in 1..filteredRecent.size) {
            return filteredRecent[position - 1]
        }

        val hasAllHeader = filteredAll.isNotEmpty()
        val allStartPosition = if (hasRecent) filteredRecent.size + 1 else 0 // header pos
        val allIndex = position - allStartPosition
        return filteredAll[allIndex]
    }


    fun removeRecentAt(position: Int) {
        val indexInRecent = position - 1
        if (indexInRecent !in filteredRecent.indices) return

        val removed = filteredRecent[indexInRecent]

        filteredRecent = filteredRecent.toMutableList().apply { removeAt(indexInRecent) }
        recentBars = recentBars.filter { it.name != removed.name }

        notifyDataSetChanged() // <-- instead of notifyItemRemoved(position)
        onRemoveRecent(removed)
    }


    // ------------------- Updating Items -------------------

    fun setItems(recentBars: List<BarUi>, allBars: List<BarUi>) {
        this.recentBars = recentBars
        this.allBars = allBars
        this.filteredRecent = recentBars
        this.filteredAll = allBars
        notifyDataSetChanged()
    }

    // ------------------- Filtering Search -------------------

    fun filter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())

        if (q.isEmpty()) {
            filteredRecent = recentBars
            filteredAll = allBars
        } else {
            filteredRecent = recentBars.filter {
                it.name?.lowercase()?.contains(q) == true
            }

            filteredAll = allBars.filter {
                it.name?.lowercase()?.contains(q) == true
            }
        }

        notifyDataSetChanged()
    }
}
