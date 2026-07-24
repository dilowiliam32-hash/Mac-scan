package com.hunter.macscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerTest {

    @Test
    fun testLocationInfoSummaryWithCity() {
        val location = LocationInfo(
            country = "United States",
            countryCode = "US",
            city = "New York",
            ip = "127.0.0.1"
        )
        val expectedFlag = "🇺🇸"
        val expectedSummary = "$expectedFlag United States, New York"
        assertEquals(expectedSummary, location.getSummary())
    }

    @Test
    fun testLocationInfoSummaryWithoutCity() {
        val location = LocationInfo(
            country = "Canada",
            countryCode = "CA",
            city = "Unknown",
            ip = "127.0.0.1"
        )
        val expectedFlag = "🇨🇦"
        val expectedSummary = "$expectedFlag Canada"
        assertEquals(expectedSummary, location.getSummary())
    }

    @Test
    fun testLocationInfoSummaryLowercaseCountryCode() {
        val location = LocationInfo(
            country = "Germany",
            countryCode = "de",
            city = "Berlin",
            ip = "127.0.0.1"
        )
        val expectedFlag = "🇩🇪"
        val expectedSummary = "$expectedFlag Germany, Berlin"
        assertEquals(expectedSummary, location.getSummary())
    }

    @Test
    fun testMACGeneratorProgressAndCount() {
        val generator = MACGenerator("00:1A:79:", "00:1A:79:00:00:00")

        assertEquals(0xFFFFFF, generator.getRemainingCount())
        assertEquals(0f, generator.getProgress(), 0.001f)

        val mac1 = generator.getNextMac()
        assertEquals("00:1A:79:00:00:00", mac1)
        assertEquals(0xFFFFFE, generator.getRemainingCount())

        val mac2 = generator.getNextMac()
        assertEquals("00:1A:79:00:00:01", mac2)
    }

    @Test
    fun testMACGeneratorCustomStart() {
        val generator = MACGenerator("00:1A:79:", "00:1A:79:00:00:10")
        val mac = generator.getNextMac()
        assertEquals("00:1A:79:00:00:10", mac)

        val expectedProgress = (16.0f / 0xFFFFFF) * 100f
        assertEquals(expectedProgress, generator.getProgress(), 0.001f)
    }
}
