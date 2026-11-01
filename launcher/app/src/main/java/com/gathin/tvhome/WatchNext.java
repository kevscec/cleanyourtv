package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** continue_watching: the rows WatchNextReader finds, and which of them Home shows. Pure logic, JVM-tested. */
final class WatchNext {
    private WatchNext() {}

    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*: continue, next, new, watchlist. */
    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1, TYPE_NEW = 2, TYPE_WATCHLIST = 3;
    static final long DAY_MS = 24 * 60 * 60_000L;
    /** A dated row older than this has been left, not paused: Crunchyroll keeps rows for years. */
    static final long MAX_AGE_MS = 60 * DAY_MS;
    /** A dated row this fresh comes before the undated ones (a channel such as Stremio's shows its current list). */
    static final long FRESH_MS = 2 * DAY_MS;
    /** From this far in, a "continue" row is finished, not in progress. */
    static final int DONE_PERCENT = 95;

    /** One program: a show or film the viewer has started, or the next episode of one. */
    static final class Item {
        final String packageName, title, episodeTitle, season, episode, poster, thumb, intent, channel;
        final long position, duration, engaged;
        final int type;

        Item(String packageName, String title, String episodeTitle, String season, String episode, String poster,
             String thumb, String intent, long position, long duration, long engaged, int type, String channel) {
            this.packageName = packageName;
            this.title = title;
            this.episodeTitle = episodeTitle;
            this.season = season;
            this.episode = episode;
            this.poster = poster;
            this.thumb = thumb;
            this.intent = intent;
            this.position = position;
            this.duration = duration;
            this.engaged = engaged;
            this.type = type;
            this.channel = channel;
        }

        /** How far in, 0 to 100; -1 when the row has no position or duration. */
        int percent() {
            if (position <= 0 || duration <= 0) return -1;
            return (int) Math.max(0, Math.min(100, position * 100 / duration));
        }

        /** Whole minutes still to watch, or -1 when unknown. */
        int minutesLeft() {
            if (position <= 0 || duration <= position) return -1;
            return (int) Math.max(1, Math.round((duration - position) / 60_000.0));
        }

        /** The picture for a wide card: the wide thumbnail when there is one, else the poster. */
        String image() {
            return thumb.isEmpty() ? poster : thumb;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s | %s | %s | S%s E%s | %d%% | engaged %d | type %d | image %s | intent %s",
                    packageName, title, episodeTitle, season, episode, percent(), engaged, type, image(), intent);
        }
    }

    /** Whether a preview channel's name means the viewer's progress ("Continue Watching", "Seguir viendo", ...). */
    static boolean isContinueChannel(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("continue") || n.contains("continuar") || n.contains("seguir viendo")
                || n.contains("continúa") || n.contains("continua viendo");
    }

    /**
     * What the row shows, at most max: continue and next-episode rows engaged in the last MAX_AGE_MS and not
     * finished; undated rows (a continue channel) as listed. Order: dated rows from the last FRESH_MS newest first,
     * then the undated ones, then the older dated ones. One card per app and title.
     */
    static List<Item> select(List<Item> all, long now, int max) {
        List<Item> fresh = new ArrayList<>(), undated = new ArrayList<>(), older = new ArrayList<>();
        for (Item item : all) {
            if (item.title.isEmpty() || item.intent.isEmpty()) continue;
            if (item.type != TYPE_CONTINUE && item.type != TYPE_NEXT) continue;
            if (item.type == TYPE_CONTINUE && item.percent() >= DONE_PERCENT) continue;
            if (item.engaged <= 0) {
                undated.add(item);
            } else if (now - item.engaged <= MAX_AGE_MS && item.engaged <= now + DAY_MS) {
                (now - item.engaged <= FRESH_MS ? fresh : older).add(item);
            }
        }
        Collections.sort(fresh, (a, b) -> Long.compare(b.engaged, a.engaged));
        Collections.sort(older, (a, b) -> Long.compare(b.engaged, a.engaged));
        List<Item> shown = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Item> group : Arrays.asList(fresh, undated, older)) {
            for (Item item : group) {
                if (shown.size() >= max) return shown;
                if (seen.add(item.packageName + "\n" + item.title.toLowerCase(Locale.ROOT))) shown.add(item);
            }
        }
        return shown;
    }
}
