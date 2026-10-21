package com.gradercraft.pvpzonecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the server-independent logic that backs PvPZoneCycle's
 * commands, timers, cuboid checks and cross-version potion-effect handling.
 * These run without a Bukkit/Paper server, so they execute on any JDK >= 17.
 */
class PvPZoneRulesTest {

    // --- Arena name validation (command input hardening) ---

    @Test
    void acceptsWellFormedArenaNames() {
        assertTrue(PvPZoneRules.isValidArenaName("pvp1"));
        assertTrue(PvPZoneRules.isValidArenaName("arena_01"));
        assertTrue(PvPZoneRules.isValidArenaName("a-b_c"));
        assertTrue(PvPZoneRules.isValidArenaName("a"));
        assertTrue(PvPZoneRules.isValidArenaName("0123456789012345678901234567890a")); // 32 chars
    }

    @Test
    void rejectsMalformedOrOversizedArenaNames() {
        assertFalse(PvPZoneRules.isValidArenaName(null));
        assertFalse(PvPZoneRules.isValidArenaName(""));
        assertFalse(PvPZoneRules.isValidArenaName("UPPER"));          // must be lowercased first
        assertFalse(PvPZoneRules.isValidArenaName("has space"));
        assertFalse(PvPZoneRules.isValidArenaName("bad!char"));
        assertFalse(PvPZoneRules.isValidArenaName("path/traversal"));
        assertFalse(PvPZoneRules.isValidArenaName("arenas.injected")); // dots would be a config-path risk
        assertFalse(PvPZoneRules.isValidArenaName("0123456789012345678901234567890ab")); // 33 chars
    }

    // --- Timer formatting ---

    @Test
    void formatsTimerAsMinutesAndPaddedSeconds() {
        assertEquals("0:00", PvPZoneRules.formatTime(0));
        assertEquals("0:05", PvPZoneRules.formatTime(5));
        assertEquals("1:00", PvPZoneRules.formatTime(60));
        assertEquals("1:01", PvPZoneRules.formatTime(61));
        assertEquals("2:03", PvPZoneRules.formatTime(123));
        assertEquals("60:00", PvPZoneRules.formatTime(3600));
    }

    @Test
    void formatTimeClampsNegativeToZero() {
        assertEquals("0:00", PvPZoneRules.formatTime(-10));
    }

    // --- Duration / level validation ---

    @Test
    void durationBoundsAreInclusive() {
        assertTrue(PvPZoneRules.isValidDuration(1));
        assertTrue(PvPZoneRules.isValidDuration(86400));
        assertFalse(PvPZoneRules.isValidDuration(0));
        assertFalse(PvPZoneRules.isValidDuration(-1));
        assertFalse(PvPZoneRules.isValidDuration(86401));
    }

    @Test
    void levelBoundsAreInclusive() {
        assertTrue(PvPZoneRules.isValidLevel(1));
        assertTrue(PvPZoneRules.isValidLevel(255));
        assertFalse(PvPZoneRules.isValidLevel(0));
        assertFalse(PvPZoneRules.isValidLevel(256));
    }

    // --- Cross-version potion-effect aliases ---

    @Test
    void effectAliasMapIsSymmetric() {
        assertEquals("SLOWNESS", PvPZoneRules.aliasOf("SLOW"));
        assertEquals("SLOW", PvPZoneRules.aliasOf("SLOWNESS"));
        assertEquals("JUMP_BOOST", PvPZoneRules.aliasOf("JUMP"));
        assertEquals("JUMP", PvPZoneRules.aliasOf("JUMP_BOOST"));
        assertEquals("RESISTANCE", PvPZoneRules.aliasOf("DAMAGE_RESISTANCE"));
    }

    @Test
    void everyAliasRoundTrips() {
        for (String key : PvPZoneRules.EFFECT_ALIASES.keySet()) {
            String mapped = PvPZoneRules.aliasOf(key);
            assertNotNull(mapped, "alias missing for " + key);
            assertEquals(key, PvPZoneRules.aliasOf(mapped), "alias not symmetric for " + key);
        }
    }

    @Test
    void unknownAliasReturnsNull() {
        assertNull(PvPZoneRules.aliasOf("NIGHT_VISION"));
        assertNull(PvPZoneRules.aliasOf(null));
    }

    @Test
    void normalizesEffectTokens() {
        assertEquals("NIGHT_VISION", PvPZoneRules.normalizeEffectToken("night vision"));
        assertEquals("NIGHT_VISION", PvPZoneRules.normalizeEffectToken("night-vision"));
        assertEquals("NIGHT_VISION", PvPZoneRules.normalizeEffectToken("NIGHT_VISION"));
        assertNull(PvPZoneRules.normalizeEffectToken(null));
    }

    // --- Cuboid math (arena containment / block protection footprint) ---

    @Test
    void coordinateNormalizationOrdersBounds() {
        assertEquals(3, PvPZoneRules.lower(10, 3));
        assertEquals(10, PvPZoneRules.upper(10, 3));
        assertEquals(-5, PvPZoneRules.lower(-5, -5));
    }

    @Test
    void containmentIsInclusiveOnAllFaces() {
        // Normalized cuboid 0..10 on each axis
        assertTrue(PvPZoneRules.within(0, 0, 0, 10, 10, 10, 0, 0, 0));    // min corner
        assertTrue(PvPZoneRules.within(0, 0, 0, 10, 10, 10, 10, 10, 10)); // max corner
        assertTrue(PvPZoneRules.within(0, 0, 0, 10, 10, 10, 5, 5, 5));    // interior
    }

    @Test
    void containmentRejectsPointsOutsideAnyAxis() {
        assertFalse(PvPZoneRules.within(0, 0, 0, 10, 10, 10, -1, 5, 5));
        assertFalse(PvPZoneRules.within(0, 0, 0, 10, 10, 10, 5, 11, 5));
        assertFalse(PvPZoneRules.within(0, 0, 0, 10, 10, 10, 5, 5, 42));
    }
}
