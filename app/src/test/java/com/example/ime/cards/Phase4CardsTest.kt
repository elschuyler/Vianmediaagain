package com.example.ime.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4CardsTest {

    @Test
    fun testEntryTypeEnum() {
        val types = EntryType.values()
        assertEquals(2, types.size)
        assertTrue(types.contains(EntryType.CLIPBOARD))
        assertTrue(types.contains(EntryType.PROMPT))
    }

    @Test
    fun testTextCardItemSerialization() {
        val item = TextCardItem(
            id = "test-uuid-1234",
            text = "Hello, this is a test clip",
            type = EntryType.CLIPBOARD,
            isPinned = true,
            timestamp = 1700000000000L
        )

        val encoded = TextCardItem.encodeList(listOf(item))
        assertTrue(encoded.contains("test-uuid-1234"))
        assertTrue(encoded.contains("Hello, this is a test clip"))
        assertTrue(encoded.contains("CLIPBOARD"))

        val decodedList = TextCardItem.decodeList(encoded)
        assertEquals(1, decodedList.size)
        val restored = decodedList[0]
        assertEquals(item.id, restored.id)
        assertEquals(item.text, restored.text)
        assertEquals(item.type, restored.type)
        assertEquals(item.isPinned, restored.isPinned)
        assertEquals(item.timestamp, restored.timestamp)
    }

    @Test
    fun testTextCardItemPromptPartition() {
        val promptItem = TextCardItem(
            id = "prompt-uuid-5678",
            text = "Summarize the following text:",
            type = EntryType.PROMPT,
            isPinned = false
        )

        val encoded = TextCardItem.encodeList(listOf(promptItem))
        val restoredList = TextCardItem.decodeList(encoded)
        assertEquals(1, restoredList.size)
        val restored = restoredList[0]
        assertEquals(EntryType.PROMPT, restored.type)
        assertFalse(restored.isPinned)
    }

    @Test
    fun testAtomicPartitionSwitch() {
        val item = TextCardItem(
            text = "Important note copied from web",
            type = EntryType.CLIPBOARD,
            isPinned = false
        )
        assertEquals(EntryType.CLIPBOARD, item.type)

        // Atomic partition switch to Prompt List
        item.type = EntryType.PROMPT
        assertEquals(EntryType.PROMPT, item.type)

        val encoded = TextCardItem.encodeList(listOf(item))
        val restoredList = TextCardItem.decodeList(encoded)
        assertEquals(1, restoredList.size)
        assertEquals(EntryType.PROMPT, restoredList[0].type)
    }

    @Test
    fun testAdapterItemCountsAndTypes() {
        val clipboardAdapter = TextCardsAdapter(
            mode = EntryType.CLIPBOARD,
            onItemClick = {},
            onItemLongClick = { _, _ -> },
            onAddCardClick = {}
        )
        clipboardAdapter.updateData(
            listOf(
                TextCardItem(text = "Clip 1", type = EntryType.CLIPBOARD),
                TextCardItem(text = "Clip 2", type = EntryType.CLIPBOARD)
            )
        )
        // In Clipboard mode, no Add Card is shown
        assertEquals(2, clipboardAdapter.itemCount)
        assertEquals(TextCardsAdapter.VIEW_TYPE_ITEM_CARD, clipboardAdapter.getItemViewType(0))
        assertEquals(TextCardsAdapter.VIEW_TYPE_ITEM_CARD, clipboardAdapter.getItemViewType(1))

        val promptAdapter = TextCardsAdapter(
            mode = EntryType.PROMPT,
            onItemClick = {},
            onItemLongClick = { _, _ -> },
            onAddCardClick = {}
        )
        promptAdapter.updateData(
            listOf(
                TextCardItem(text = "Prompt 1", type = EntryType.PROMPT),
                TextCardItem(text = "Prompt 2", type = EntryType.PROMPT)
            )
        )
        // In Prompt mode, the very first card at position 0 is the special Add Card (+1)
        assertEquals(3, promptAdapter.itemCount)
        assertEquals(TextCardsAdapter.VIEW_TYPE_ADD_CARD, promptAdapter.getItemViewType(0))
        assertEquals(TextCardsAdapter.VIEW_TYPE_ITEM_CARD, promptAdapter.getItemViewType(1))
        assertEquals(TextCardsAdapter.VIEW_TYPE_ITEM_CARD, promptAdapter.getItemViewType(2))
    }
}
