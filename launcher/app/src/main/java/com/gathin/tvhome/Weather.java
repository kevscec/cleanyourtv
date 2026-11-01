package com.gathin.tvhome;

import java.util.Locale;

/**
 * weather: the current temperature, sky and today's range from Open-Meteo (no account, no key), for the latitude
 * and longitude in config.xml. Pure logic here; WeatherPanel fetches and shows it, only while Home is on screen.
 */
final class Weather {
    private Weather() {}

    /** A reading this old is fetched again. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** After a failed fetch, wait this long before the next try. */
    static final long RETRY_MS = 5 * 60_000L;
    /** A reading older than this is not shown at all: an old temperature reads as the current one. */
    static final long STALE_MS = 3 * 60 * 60_000L;

    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, SHOWERS = 7,
            STORM = 8;

    /** One reading: degrees Celsius rounded, the WMO weather code and whether the sun is up. */
    static final class Reading {
        final int temperature, max, min, code;
        final boolean day;

        Reading(int temperature, int max, int min, int code, boolean day) {
            this.temperature = temperature;
            this.max = max;
            this.min = min;
            this.code = code;
            this.day = day;
        }
    }

    static String url(String latitude, String longitude) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + latitude.trim() + "&longitude=" + longitude.trim()
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1";
    }

    /**
     * The reading in an Open-Meteo response, or null when a field is missing. Read by hand rather than with
     * org.json so the JVM tests can run it: the "*_units" objects come first and carry the same keys with strings,
     * so each value is looked up inside its own "current" or "daily" object.
     */
    static Reading parse(String json) {
        int current = json.indexOf("\"current\":{");
        int daily = json.indexOf("\"daily\":{");
        if (current < 0 || daily < 0) return null;
        Double temperature = number(json, "temperature_2m", current);
        Double code = number(json, "weather_code", current);
        Double day = number(json, "is_day", current);
        Double max = number(json, "temperature_2m_max", daily);
        Double min = number(json, "temperature_2m_min", daily);
        if (temperature == null || code == null || day == null || max == null || min == null) return null;
        return new Reading((int) Math.round(temperature), (int) Math.round(max), (int) Math.round(min),
                code.intValue(), day != 0);
    }

    /** The first number after "key": from the given index, skipping a '[' for the daily arrays. */
    private static Double number(String json, String key, int from) {
        String name = "\"" + key + "\":";
        int at = json.indexOf(name, from);
        if (at < 0) return null;
        int i = at + name.length();
        while (i < json.length() && (json.charAt(i) == '[' || json.charAt(i) == ' ')) i++;
        int start = i;
        while (i < json.length() && "-+.0123456789eE".indexOf(json.charAt(i)) >= 0) i++;
        try {
            return Double.parseDouble(json.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A WMO weather code as one of the conditions above; an unknown code counts as cloudy. */
    static int condition(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if (code >= 61 && code <= 67) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 80 && code <= 82) return SHOWERS;
        if (code >= 95 && code <= 99) return STORM;
        return CLOUDY;
    }

    /** The condition as an emoji; a clear or partly cloudy night shows the moon or a plain cloud. */
    static String emoji(int condition, boolean day) {
        switch (condition) {
            case CLEAR: return day ? "☀️" : "🌙";
            case PARTLY: return day ? "⛅" : "☁️";
            case FOG: return "🌫️";
            case DRIZZLE:
            case SHOWERS: return "🌦️";
            case RAIN: return "🌧️";
            case SNOW: return "❄️";
            case STORM: return "⛈️";
            default: return "☁️";
        }
    }

    /** "↑25° ↓16°": today's high and low. */
    static String range(Reading reading) {
        return String.format(Locale.ROOT, "↑%d°  ↓%d°", reading.max, reading.min);
    }

    /** Whether to fetch now: the reading is due, and a failed try is not too recent. A clock set back counts as due. */
    static boolean due(long fetchedAt, long attemptedAt, long now) {
        boolean old = now - fetchedAt >= REFRESH_MS || now < fetchedAt;
        boolean rested = now - attemptedAt >= RETRY_MS || now < attemptedAt;
        return old && rested;
    }

    /** Whether a reading fetched then may still be shown. */
    static boolean fresh(long fetchedAt, long now) {
        return fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < STALE_MS;
    }
}
