package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {
    // An Open-Meteo answer as it comes back, units objects first with the same keys as strings.
    private static final String RESPONSE = "{\"latitude\":14.625,\"longitude\":-90.5,\"generationtime_ms\":0.05,"
            + "\"utc_offset_seconds\":-21600,\"timezone\":\"America/Guatemala\",\"elevation\":1500.0,"
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\","
            + "\"weather_code\":\"wmo code\",\"is_day\":\"\"},"
            + "\"current\":{\"time\":\"2026-10-31T20:15\",\"interval\":900,\"temperature_2m\":18.6,\"weather_code\":2,"
            + "\"is_day\":0},"
            + "\"daily_units\":{\"time\":\"iso8601\",\"temperature_2m_max\":\"°C\",\"temperature_2m_min\":\"°C\"},"
            + "\"daily\":{\"time\":[\"2026-10-31\"],\"temperature_2m_max\":[24.5],\"temperature_2m_min\":[-0.4]}}";

    @Test
    public void aResponseIsReadFromItsOwnObjectsNotTheUnits() {
        Weather.Reading reading = Weather.parse(RESPONSE);
        assertEquals(19, reading.temperature);
        assertEquals(25, reading.max);
        assertEquals(0, reading.min);
        assertEquals(2, reading.code);
        assertFalse(reading.day);
    }

    @Test
    public void aResponseMissingAFieldIsNoReading() {
        assertNull(Weather.parse(RESPONSE.replace("\"weather_code\":2,", "")));
        assertNull(Weather.parse("{\"error\":true,\"reason\":\"Latitude must be in range\"}"));
        assertNull(Weather.parse(""));
    }

    @Test
    public void wmoCodesGroupIntoConditions() {
        assertEquals(Weather.CLEAR, Weather.condition(0));
        assertEquals(Weather.PARTLY, Weather.condition(2));
        assertEquals(Weather.CLOUDY, Weather.condition(3));
        assertEquals(Weather.FOG, Weather.condition(45));
        assertEquals(Weather.DRIZZLE, Weather.condition(53));
        assertEquals(Weather.RAIN, Weather.condition(63));
        assertEquals(Weather.SNOW, Weather.condition(75));
        assertEquals(Weather.SHOWERS, Weather.condition(81));
        assertEquals(Weather.STORM, Weather.condition(95));
        assertEquals(Weather.CLOUDY, Weather.condition(42));
    }

    @Test
    public void aClearNightShowsTheMoon() {
        assertEquals("🌙", Weather.emoji(Weather.CLEAR, false));
        assertEquals("☀️", Weather.emoji(Weather.CLEAR, true));
    }

    @Test
    public void aFetchIsDueEveryHalfHourButNotRightAfterAFailedTry() {
        long now = 10_000_000L;
        assertTrue(Weather.due(0, 0, now));
        assertFalse(Weather.due(now - Weather.REFRESH_MS + 1, now - Weather.REFRESH_MS + 1, now));
        assertTrue(Weather.due(now - Weather.REFRESH_MS, now - Weather.REFRESH_MS, now));
        assertFalse(Weather.due(now - Weather.REFRESH_MS, now - 60_000L, now));
        assertTrue(Weather.due(now + 60_000L, now + 60_000L, now));
    }

    @Test
    public void anOldReadingIsNotShown() {
        long now = 10_000_000L;
        assertTrue(Weather.fresh(now - 60_000L, now));
        assertFalse(Weather.fresh(now - Weather.STALE_MS, now));
        assertFalse(Weather.fresh(0, now));
    }

    @Test
    public void theUrlAsksForTodayAtTheGivenPlace() {
        String url = Weather.url(" 14.63", "-90.51 ");
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=14.63&longitude=-90.51&"));
        assertTrue(url.contains("forecast_days=1"));
    }
}
