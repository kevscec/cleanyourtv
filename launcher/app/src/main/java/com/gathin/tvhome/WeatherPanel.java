package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * weather: the reading beside the clock, after a thin line: "⛅ 23°", the sky in words, and today's range. Fetched
 * on a worker thread when Home resumes and on the minute tick, only when due (Weather.due), never in the
 * background. The last reading is kept, so Home shows it at once; one older than Weather.STALE_MS is hidden.
 */
final class WeatherPanel {
    private static final String TAG = "WeatherPanel";
    private static final int[] CONDITIONS = {R.string.weather_clear, R.string.weather_partly, R.string.weather_cloudy,
            R.string.weather_fog, R.string.weather_drizzle, R.string.weather_rain, R.string.weather_snow,
            R.string.weather_showers, R.string.weather_storm};

    private final Context context;
    private final Handler handler;
    private final String url;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final View divider;
    private final LinearLayout block;
    private final TextView now, sky, range;
    private boolean loading;

    WeatherPanel(Context context, LinearLayout clockRow, Handler handler, String latitude, String longitude) {
        this.context = context;
        this.handler = handler;
        url = Weather.url(latitude, longitude);
        preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        // A reading belongs to its place: a new latitude or longitude drops it and fetches at once.
        if (!url.equals(preferences.getString("url", ""))) {
            preferences.edit().clear().putString("url", url).apply();
        }
        float dp = context.getResources().getDisplayMetrics().density;
        divider = new View(context);
        divider.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(Math.max(1, Math.round(dp)), Math.round(64 * dp));
        line.gravity = Gravity.CENTER_VERTICAL;
        line.setMarginStart(Math.round(26 * dp));
        line.setMarginEnd(Math.round(26 * dp));
        clockRow.addView(divider, line);
        block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        now = text(30, Color.WHITE);
        now.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        sky = text(14, 0xD9FFFFFF);
        range = text(13, 0xA6FFFFFF);
        range.setLetterSpacing(0.08f);
        block.addView(now);
        block.addView(sky);
        block.addView(range);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        clockRow.addView(block, lp);
        show();
    }

    private TextView text(float sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setPadding(0, 0, 0, Math.round(3 * context.getResources().getDisplayMetrics().density));
        return view;
    }

    /** Show the kept reading, and fetch a new one when it is due. Called on resume and every minute. */
    void refresh() {
        show();
        long time = System.currentTimeMillis();
        if (loading || !Weather.due(preferences.getLong("fetched", 0), preferences.getLong("attempted", 0), time)) {
            return;
        }
        loading = true;
        preferences.edit().putLong("attempted", time).apply();
        worker.execute(() -> {
            Weather.Reading reading = fetch();
            handler.post(() -> {
                loading = false;
                if (reading != null) {
                    preferences.edit().putLong("fetched", System.currentTimeMillis())
                            .putInt("temperature", reading.temperature).putInt("max", reading.max)
                            .putInt("min", reading.min).putInt("code", reading.code)
                            .putBoolean("day", reading.day).apply();
                }
                show();
            });
        });
    }

    private Weather.Reading fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0 && body.size() < 65_536; ) body.write(buffer, 0, n);
            }
            return Weather.parse(new String(body.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "weather fetch failed: " + e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** The kept reading, or nothing (no line either) when there is none recent enough. */
    private void show() {
        boolean fresh = Weather.fresh(preferences.getLong("fetched", 0), System.currentTimeMillis());
        int visibility = fresh ? View.VISIBLE : View.GONE;
        divider.setVisibility(visibility);
        block.setVisibility(visibility);
        if (!fresh) return;
        Weather.Reading reading = new Weather.Reading(preferences.getInt("temperature", 0),
                preferences.getInt("max", 0), preferences.getInt("min", 0), preferences.getInt("code", 3),
                preferences.getBoolean("day", true));
        int condition = Weather.condition(reading.code);
        now.setText(Weather.emoji(condition, reading.day) + " " + reading.temperature + "°");
        sky.setText(CONDITIONS[condition]);
        range.setText(Weather.range(reading));
    }

    void close() {
        worker.shutdownNow();
    }
}
