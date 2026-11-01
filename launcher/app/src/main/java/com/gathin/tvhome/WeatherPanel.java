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
 * weather: the reading beside the clock, after a thin line, as in the editorial design: "☁ 21° Nublado", then
 * "Máx 29° · Mín 20°" and the place's name, centred on the clock and date. Fetched
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
    private final TextView icon, temperature, sky, range, place;
    private boolean loading;

    /** beside: the horizontal group the line and the reading are added to, after what it already holds. */
    WeatherPanel(Context context, LinearLayout beside, Handler handler, String latitude, String longitude) {
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
        divider.setBackgroundColor(0x38FFFFFF);
        // Full height: the line runs from the panel's top padding to its bottom one.
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(Math.max(1, Math.round(dp)),
                LinearLayout.LayoutParams.MATCH_PARENT);
        line.setMarginStart(Math.round(23 * dp));
        line.setMarginEnd(Math.round(23 * dp));
        beside.addView(divider, line);
        block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout now = new LinearLayout(context);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        icon = text(24, Color.WHITE, 0);
        temperature = text(27, Color.WHITE, 0);
        temperature.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        sky = text(15, 0xE6FFFFFF, 0);
        now.addView(icon);
        now.addView(temperature, gap(8 * dp));
        now.addView(sky, gap(10 * dp));
        block.addView(now);
        range = text(11, 0xB3FFFFFF, Math.round(8 * dp));
        place = text(11, 0xB3FFFFFF, Math.round(3 * dp));
        String name = context.getString(R.string.weather_place).trim();
        place.setText(name);
        place.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        block.addView(range);
        block.addView(place);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        beside.addView(block, lp);
        show();
    }

    private TextView text(float sp, int color, int top) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setPadding(0, top, 0, 0);
        return view;
    }

    private static LinearLayout.LayoutParams gap(float start) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Math.round(start));
        return lp;
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
        icon.setText(Weather.emoji(condition, reading.day));
        temperature.setText(reading.temperature + "°");
        sky.setText(CONDITIONS[condition]);
        range.setText(context.getString(R.string.weather_range, reading.max, reading.min));
    }

    void close() {
        worker.shutdownNow();
    }
}
