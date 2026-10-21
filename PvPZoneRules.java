package com.gradercraft.pvpzonecycle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure, server-independent helper logic for PvPZoneCycle.
 *
 * <p>Everything in this class is deterministic and free of Bukkit/Paper API
 * calls, so it can be unit-tested on any JDK without a running server. The
 * main {@link PvPZoneCycle} plugin delegates to these helpers for arena-name
 * validation, timer formatting, cuboid containment, duration validation and
 * legacy&lt;-&gt;modern potion-effect alias resolution. Keeping this logic in one
 * tested place avoids subtle drift between the command handlers and the
 * runtime event handlers.
 */
final class PvPZoneRules {
    private PvPZoneRules() {}

    /** Arena identifiers: 1-32 characters, lowercase letters, digits, underscore or hyphen. */
    static final Pattern ARENA_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    /** Inclusive bounds for configurable durations (seconds): 1 second .. 24 hours. */
    static final long MIN_SECONDS = 1L;
    static final long MAX_SECONDS = 86400L;

    /** Inclusive bounds for potion-effect amplifier levels as entered by admins (1 == level I). */
    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 255;

    /**
     * Symmetric legacy&lt;-&gt;modern potion-effect name aliases. Minecraft renamed
     * many effects (e.g. {@code SLOW} -&gt; {@code SLOWNESS}); this map lets a
     * name typed on any version resolve on any other supported version.
     */
    static final Map<String, String> EFFECT_ALIASES;
    static {
        Map<String, String> m = new HashMap<>();
        String[][] pairs = {
            {"SLOW", "SLOWNESS"}, {"FAST_DIGGING", "HASTE"}, {"SLOW_DIGGING", "MINING_FATIGUE"},
            {"INCREASE_DAMAGE", "STRENGTH"}, {"HEAL", "INSTANT_HEALTH"}, {"HARM", "INSTANT_DAMAGE"},
            {"JUMP", "JUMP_BOOST"}, {"CONFUSION", "NAUSEA"}, {"DAMAGE_RESISTANCE", "RESISTANCE"}, {"UNLUCK", "BAD_LUCK"}
        };
        for (String[] pr : pairs) { m.put(pr[0], pr[1]); m.put(pr[1], pr[0]); }
        EFFECT_ALIASES = Collections.unmodifiableMap(m);
    }

    /** @return {@code true} when {@code id} is a valid arena identifier. */
    static boolean isValidArenaName(String id) {
        return id != null && ARENA_NAME.matcher(id).matches();
    }

    /** Formats a remaining-seconds value as {@code M:SS} (minutes may exceed 59). */
    static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** @return {@code true} when {@code seconds} is within the accepted duration range. */
    static boolean isValidDuration(long seconds) {
        return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS;
    }

    /** @return {@code true} when {@code level} is within the accepted amplifier range. */
    static boolean isValidLevel(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    /**
     * Normalizes a raw user-supplied effect token: upper-case and map spaces /
     * hyphens to underscores so {@code night vision}, {@code night-vision} and
     * {@code NIGHT_VISION} all compare equal.
     */
    static String normalizeEffectToken(String raw) {
        if (raw == null) return null;
        return raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /** @return the legacy/modern alias for {@code name}, or {@code null} if none is known. */
    static String aliasOf(String name) {
        return name == null ? null : EFFECT_ALIASES.get(name);
    }

    /** @return the inclusive minimum of two coordinates. */
    static int lower(int a, int b) { return Math.min(a, b); }

    /** @return the inclusive maximum of two coordinates. */
    static int upper(int a, int b) { return Math.max(a, b); }

    /**
     * Inclusive cuboid containment test against ALREADY-NORMALIZED bounds
     * ({@code x1<=x2}, {@code y1<=y2}, {@code z1<=z2}).
     */
    static boolean within(int x1, int y1, int z1, int x2, int y2, int z2, int bx, int by, int bz) {
        return bx >= x1 && bx <= x2 && by >= y1 && by <= y2 && bz >= z1 && bz <= z2;
    }
}
