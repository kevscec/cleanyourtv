package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * continue_watching: what other apps publish to the TV provider for "Continue watching" (docs/watch-next.md), read
 * with READ_TV_LISTINGS: the Watch Next table, and the preview programs of a channel named like "Continue watching"
 * (Stremio keeps one, without dates). Queried whole, without a selection (a selection throws without
 * ACCESS_ALL_EPG_DATA), off the main thread. Rows the caller cannot see (not searchable) are simply absent.
 */
final class WatchNextReader {
    private static final String TAG = "WatchNext";

    private WatchNextReader() {}

    /** Every readable row; empty when the permission is missing or the provider fails. */
    static List<WatchNext.Item> read(Context context) {
        List<WatchNext.Item> items = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                items.add(new WatchNext.Item(
                        string(c, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME),
                        string(c, TvContract.WatchNextPrograms.COLUMN_TITLE),
                        string(c, TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE),
                        string(c, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
                        string(c, TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
                        string(c, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
                        string(c, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI),
                        string(c, TvContract.WatchNextPrograms.COLUMN_INTENT_URI),
                        number(c, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
                        number(c, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS),
                        number(c, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS),
                        (int) number(c, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE), ""));
            }
        } catch (Exception e) {
            Log.w(TAG, "watch_next query failed: " + e);
        }
        Set<Long> continueChannels = new HashSet<>();
        try (Cursor c = resolver.query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (WatchNext.isContinueChannel(string(c, TvContract.Channels.COLUMN_DISPLAY_NAME))) {
                    continueChannels.add(number(c, TvContract.Channels._ID));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "channels query failed: " + e);
        }
        if (continueChannels.isEmpty()) return items;
        try (Cursor c = resolver.query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (!continueChannels.contains(number(c, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID))) continue;
                items.add(new WatchNext.Item(
                        string(c, TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME),
                        string(c, TvContract.PreviewPrograms.COLUMN_TITLE),
                        string(c, TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE),
                        string(c, TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
                        string(c, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
                        string(c, TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI),
                        string(c, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI),
                        string(c, TvContract.PreviewPrograms.COLUMN_INTENT_URI),
                        number(c, TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
                        number(c, TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS),
                        0, WatchNext.TYPE_CONTINUE, "continue"));
            }
        } catch (Exception e) {
            Log.w(TAG, "preview query failed: " + e);
        }
        return items;
    }

    private static String string(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? "" : c.getString(i);
    }

    private static long number(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }
}
