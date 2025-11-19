package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class Message(
    val id: String,
    val text: String,
    val isMe: Boolean,
    val timestamp: Long,
    val isPending: Boolean = false       // <─ NEW: to show ⏳ while unsent
)

sealed class MessageItem {
    data class MessageData(val message: Message) : MessageItem()
    data class DateDivider(val label: String) : MessageItem()
}

class MessagesAdapter :
    ListAdapter<MessageItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_DATE = 1
        private const val VIEW_TYPE_ME   = 2
        private const val VIEW_TYPE_THEM = 3

        private val DIFF = object : DiffUtil.ItemCallback<MessageItem>() {
            override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
                return when {
                    oldItem is MessageItem.DateDivider && newItem is MessageItem.DateDivider ->
                        oldItem.label == newItem.label
                    oldItem is MessageItem.MessageData && newItem is MessageItem.MessageData ->
                        oldItem.message.id == newItem.message.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
                return oldItem == newItem
            }
        }

        /**
         * Insert date divider items into a sorted list of messages.
         * Labels:
         *  - Today
         *  - Yesterday
         *  - Mon/Tue/... for last 7 days
         *  - EEE, d MMM yyyy for older
         */
        fun insertDateDividers(messages: List<Message>): List<MessageItem> {
            if (messages.isEmpty()) return emptyList()

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val yesterday = today.minusDays(1)

            val items = mutableListOf<MessageItem>()
            var lastDate: LocalDate? = null

            val formatter = DateTimeFormatter.ofPattern(
                "EEE, d MMM yyyy",
                Locale.getDefault()
            )

            val sorted = messages.sortedBy { it.timestamp }

            for (m in sorted) {
                val msgDate = Instant.ofEpochMilli(m.timestamp)
                    .atZone(zone)
                    .toLocalDate()

                if (lastDate == null || msgDate != lastDate) {
                    val label = when {
                        msgDate == today -> "Today"
                        msgDate == yesterday -> "Yesterday"
                        msgDate.isAfter(today.minusDays(7)) && msgDate.isBefore(yesterday) -> {
                            msgDate.dayOfWeek.getDisplayName(
                                TextStyle.FULL,
                                Locale.getDefault()
                            )
                        }
                        else -> msgDate.format(formatter)
                    }

                    items += MessageItem.DateDivider(label)
                    lastDate = msgDate
                }

                items += MessageItem.MessageData(m)
            }

            return items
        }

        private val timeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

        fun formatTimeOrClock(msg: Message): String {
            // Show ⏳ for pending outbound messages
            if (msg.isPending || msg.timestamp <= 0L) return "\u23F3"

            return Instant.ofEpochMilli(msg.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFormatter)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageItem.DateDivider -> VIEW_TYPE_DATE
            is MessageItem.MessageData -> if (item.message.isMe) VIEW_TYPE_ME else VIEW_TYPE_THEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val v = inflater.inflate(R.layout.item_date_divider, parent, false)
                DateViewHolder(v)
            }
            VIEW_TYPE_ME -> {
                val v = inflater.inflate(R.layout.item_message_sent, parent, false)
                MeViewHolder(v)
            }
            else -> { // VIEW_TYPE_THEM
                val v = inflater.inflate(R.layout.item_message_received, parent, false)
                ThemViewHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MessageItem.DateDivider ->
                (holder as DateViewHolder).bind(item.label)

            is MessageItem.MessageData -> {
                val msg = item.message
                when (holder) {
                    is MeViewHolder   -> holder.bind(msg)
                    is ThemViewHolder -> holder.bind(msg)
                }
            }
        }
    }

    // ---- ViewHolders ----

    class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IMPORTANT: this must match your item_date_divider.xml id
        private val tv: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(label: String) {
            tv.text = label
        }
    }

    class MeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView    = itemView.findViewById(R.id.tvTime)

        fun bind(msg: Message) {
            tvMessage.text = msg.text
            tvTime.text = formatTimeOrClock(msg)
        }
    }

    class ThemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView    = itemView.findViewById(R.id.tvTime)

        fun bind(msg: Message) {
            tvMessage.text = msg.text
            tvTime.text = formatTimeOrClock(msg)
        }
    }
}
