package com.example.ime.emoji

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.ime.modal.ModalBottomBarView

class VianEmojiModalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onDismissToAlpha: (() -> Unit)? = null
    var onEmojiCommit: ((String) -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null

    private val categories = EmojiCategoryData.getCategories(context)
    private var selectedCategoryIndex = 0

    private val llCategoryTabs: LinearLayout
    private val rvEmojiGrid: RecyclerView
    private val modalBottomBar: ModalBottomBarView
    private val glyphAdapter: EmojiGlyphAdapter
    private val tabViews = mutableListOf<ImageView>()

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_emoji_modal, this, true)

        llCategoryTabs = view.findViewById(R.id.llCategoryTabs)
        rvEmojiGrid = view.findViewById(R.id.rvEmojiGrid)
        modalBottomBar = view.findViewById(R.id.modalBottomBar)

        rvEmojiGrid.layoutManager = GridLayoutManager(context, 8)
        glyphAdapter = EmojiGlyphAdapter { emoji ->
            EmojiCategoryData.recordEmojiUse(context, emoji)
            onEmojiCommit?.invoke(emoji)
        }
        rvEmojiGrid.adapter = glyphAdapter

        buildCategoryTabs()
        setupBottomBar()
        selectCategory(0)
    }

    private fun buildCategoryTabs() {
        llCategoryTabs.removeAllViews()
        tabViews.clear()

        val inflater = LayoutInflater.from(context)
        for (i in categories.indices) {
            val cat = categories[i]
            val tabItem = inflater.inflate(R.layout.item_emoji_tab_pill, llCategoryTabs, false)
            val ivIcon = tabItem.findViewById<ImageView>(R.id.ivCategoryIcon)

            val drawable = ContextCompat.getDrawable(context, cat.iconResId)?.mutate()
            ivIcon.setImageDrawable(drawable)
            ivIcon.contentDescription = cat.name

            tabItem.setOnClickListener {
                selectCategory(i)
            }

            tabViews.add(ivIcon)
            llCategoryTabs.addView(tabItem)
        }
    }

    private fun selectCategory(index: Int) {
        if (index !in categories.indices) return
        selectedCategoryIndex = index

        for (i in tabViews.indices) {
            val iconView = tabViews[i]
            val isSelected = (i == index)
            if (isSelected) {
                iconView.setBackgroundResource(R.drawable.bg_emoji_tab_pill_selected)
                iconView.setColorFilter(0xFF0F172A.toInt())
            } else {
                iconView.background = null
                iconView.setColorFilter(0xFF64748B.toInt())
            }
        }

        val category = categories[index]
        glyphAdapter.setEmojis(category.emojis)
        rvEmojiGrid.scrollToPosition(0)
    }

    private fun setupBottomBar() {
        modalBottomBar.onAbcClick = { onDismissToAlpha?.invoke() }
        modalBottomBar.onSpaceClick = { onEmojiCommit?.invoke(" ") }
        modalBottomBar.onDeleteClick = { onDelete?.invoke() }
        modalBottomBar.onEnterClick = { onEnter?.invoke() }
    }
}
