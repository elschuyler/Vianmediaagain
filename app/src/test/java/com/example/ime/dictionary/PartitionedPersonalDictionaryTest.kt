package com.example.ime.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionedPersonalDictionaryTest {

    @Test
    fun testDictionaryPartitionEnum() {
        val partitions = DictionaryPartition.values()
        assertEquals(2, partitions.size)
        assertTrue(partitions.contains(DictionaryPartition.NORMAL))
        assertTrue(partitions.contains(DictionaryPartition.PRIVACY_VAULT))
    }

    @Test
    fun testPersonalDictionaryEntryProperties() {
        val entry = PersonalDictionaryEntry(
            id = "entry-123",
            phrase = "123 Broadway, Apt 4B, New York, NY",
            shortcut = "myaddr",
            weight = 250,
            locale = "en",
            partition = DictionaryPartition.PRIVACY_VAULT,
            category = "Address"
        )

        assertEquals("entry-123", entry.id)
        assertEquals("123 Broadway, Apt 4B, New York, NY", entry.phrase)
        assertEquals("myaddr", entry.shortcut)
        assertEquals(250, entry.weight)
        assertEquals("en", entry.locale)
        assertEquals(DictionaryPartition.PRIVACY_VAULT, entry.partition)
        assertEquals("Address", entry.category)
    }

    @Test
    fun testSmartMaskingAnchors() {
        // Email masking: preserves first 3 letters and domain
        val email = "schuylervianilewis@gmail.com"
        val maskedEmail = PersonalDictionaryStorage.maskPhrase(email)
        assertEquals("sch••••@gmail.com", maskedEmail)

        // Address masking: preserves first 3 and last 2 characters
        val addr = "123 Broadway, Apt 4B, New York, NY"
        val maskedAddr = PersonalDictionaryStorage.maskPhrase(addr)
        assertEquals("123••••NY", maskedAddr)

        // Tax / ID masking: preserves last 4 digits
        val taxId = "987-65-4321"
        val maskedTaxId = PersonalDictionaryStorage.maskPhrase(taxId)
        assertTrue(maskedTaxId.endsWith("4321"))
        assertTrue(maskedTaxId.contains("••••"))

        // Phone number with country code
        val phone = "+1-555-839-2019"
        val maskedPhone = PersonalDictionaryStorage.maskPhrase(phone)
        assertTrue(maskedPhone.startsWith("+1"))
        assertTrue(maskedPhone.endsWith("2019"))

        // Short word: completely masked
        assertEquals("••••", PersonalDictionaryStorage.maskPhrase("pin"))
        assertEquals("••••", PersonalDictionaryStorage.maskPhrase("1234"))
    }
}
