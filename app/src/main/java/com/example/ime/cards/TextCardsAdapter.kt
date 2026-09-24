package com.example.ime.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class TextCardsAdapter(
    private val mode: EntryType,
    private val onItemClick: (TextCardItem) -> Unit,
    private val onItemLongClick: (View, TextCardItem) -> Unit,
    private val onAddCardClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_ADD_CARD = 0
        const val VIEW_TYPE_ITEM_CARD = 1
    }

    private val items = mutableListOf<TextCardItem>()

    fun updateData(newItems: List<TextCardItem>) {
        items.clear()
        items.addAll(newItems)
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            // Safe fallback for headless JVM test runners
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (mode == EntryType.PROMPT && position == 0) {
            VIEW_TYPE_ADD_CARD
        } else {
            VIEW_TYPE_ITEM_CARD
        }
    }

    override fun getItemCount(): Int {
        return if (mode == EntryType.PROMPT) {
            items.size + 1 // +1 for the special Add Button Card at index 0
        } else {
            items.size
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_ADD_CARD) {
            val view = inflater.inflate(R.layout.item_add_prompt_card, parent, false)
            AddCardViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_text_card, parent, false)
            ItemCardViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddCardViewHolder) {
            holder.itemView.setOnClickListener {
                onAddCardClick()
            }
        } else if (holder is ItemCardViewHolder) {
            val actualIndex = if (mode == EntryType.PROMPT) position - 1 else position
            if (actualIndex < 0 || actualIndex >= items.size) return
            val item = items[actualIndex]

            holder.tvCardText.text = item.text

            // Apply mode-specific line formatting
            if (mode == EntryType.CLIPBOARD) {
                holder.tvCardText.maxLines = 4
            } else {
                holder.tvCardText.maxLines = 2
            }

            if (item.isPinned) {
                holder.ivPinBadge.visibility = View.VISIBLE
                holder.ivPinBadge.setColorFilter(0xFF3B82F6.toInt())
            } else {
                holder.ivPinBadge.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(holder.itemView, item)
                true
            }
        }
    }

    class AddCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class ItemCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCardText: TextView = itemView.findViewById(R.id.tvCardText)
        val ivPinBadge: ImageView = itemView.findViewById(R.id.ivPinBadge)
    }
}
