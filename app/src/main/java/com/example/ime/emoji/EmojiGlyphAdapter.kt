package com.example.ime.emoji

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class EmojiGlyphAdapter(
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiGlyphAdapter.ViewHolder>() {

    private val emojis = mutableListOf<String>()

    fun setEmojis(newEmojis: List<String>) {
        emojis.clear()
        emojis.addAll(newEmojis)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji_glyph, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.tvEmojiGlyph.text = emoji
        holder.itemView.setOnClickListener { onEmojiClick(emoji) }
    }

    override fun getItemCount(): Int = emojis.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEmojiGlyph: TextView = itemView.findViewById(R.id.tvEmojiGlyph)
    }
}
