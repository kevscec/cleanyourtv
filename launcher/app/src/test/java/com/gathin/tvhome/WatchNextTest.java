package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WatchNextTest {
    private static final long NOW = 1_793_503_520_000L;
    private static final long DAY = WatchNext.DAY_MS;

    private static WatchNext.Item crunchy(String title, long daysAgo, int type, long position, long duration) {
        return new WatchNext.Item("com.crunchyroll.crunchyroid", title, "", "1", "2", "https://p", "",
                "crunchyroll://episode?id=" + title, position, duration, NOW - daysAgo * DAY, type, "");
    }

    private static WatchNext.Item stremio(String title, long position, long duration) {
        return new WatchNext.Item("com.stremio.one", title, "", "", "", "https://poster", "", "stremio:///detail/" + title,
                position, duration, 0, WatchNext.TYPE_CONTINUE, "Continue Watching");
    }

    private static List<String> titles(List<WatchNext.Item> items) {
        List<String> out = new ArrayList<>();
        for (WatchNext.Item item : items) out.add(item.title);
        return out;
    }

    @Test
    public void freshRowsComeFirstThenTheChannelThenOlderOnes() {
        List<WatchNext.Item> all = Arrays.asList(
                crunchy("Old", 300, WatchNext.TYPE_CONTINUE, 10, 100),
                crunchy("Week", 7, WatchNext.TYPE_NEXT, 0, 0),
                stremio("Breaking Bad", 9, 100),
                crunchy("Today", 0, WatchNext.TYPE_CONTINUE, 26, 100),
                stremio("Beetlejuice", 61, 100));
        assertEquals(Arrays.asList("Today", "Breaking Bad", "Beetlejuice", "Week"),
                titles(WatchNext.select(all, NOW, 4)));
    }

    @Test
    public void finishedOldAndOtherKindsAreLeftOut() {
        List<WatchNext.Item> all = Arrays.asList(
                crunchy("Done", 1, WatchNext.TYPE_CONTINUE, 99, 100),
                crunchy("Ancient", 61, WatchNext.TYPE_CONTINUE, 10, 100),
                crunchy("Promo", 1, WatchNext.TYPE_NEW, 0, 0),
                stremio("Finished film", 96, 100),
                crunchy("Kept", 59, WatchNext.TYPE_CONTINUE, 10, 100));
        assertEquals(Arrays.asList("Kept"), titles(WatchNext.select(all, NOW, 4)));
    }

    @Test
    public void atMostMaxAndOneCardPerTitle() {
        List<WatchNext.Item> all = Arrays.asList(stremio("A", 1, 10), stremio("a", 2, 10), stremio("B", 1, 10),
                stremio("C", 1, 10), stremio("D", 1, 10), stremio("E", 1, 10));
        assertEquals(Arrays.asList("A", "B", "C", "D"), titles(WatchNext.select(all, NOW, 4)));
    }

    @Test
    public void progressAndMinutesLeft() {
        WatchNext.Item item = stremio("Film", 72 * 60_000L, 120 * 60_000L);
        assertEquals(60, item.percent());
        assertEquals(48, item.minutesLeft());
        assertEquals(-1, stremio("Next", 0, 0).percent());
        assertEquals(-1, stremio("Next", 0, 0).minutesLeft());
    }

    @Test
    public void aWideThumbnailIsPreferredOverAPoster() {
        WatchNext.Item withThumb = new WatchNext.Item("p", "t", "", "", "", "poster", "thumb", "i", 0, 0, 0, 0, "");
        assertEquals("thumb", withThumb.image());
        assertEquals("https://poster", stremio("x", 0, 0).image());
    }

    @Test
    public void continueChannelsAreRecognisedByName() {
        assertTrue(WatchNext.isContinueChannel("Continue Watching"));
        assertTrue(WatchNext.isContinueChannel("Seguir viendo"));
        assertFalse(WatchNext.isContinueChannel("Más popular"));
        assertFalse(WatchNext.isContinueChannel(null));
    }
}
