package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * continue_watching: the "Continue watching" row above the dock, as in the editorial design: a serif heading and up
 * to MAX wide cards (picture, the app's name, title, a detail line and a progress bar). The rows come from
 * WatchNextReader on every return to Home; the pictures are fetched once per address and kept while shown. OK opens
 * the row's own link in its app. Gone when there is nothing to continue.
 */
final class ContinueRow {
    private static final String TAG = "ContinueRow";
    static final int MAX = 4;

    /** What the row asks of Home: where focus goes when it leaves the row, and a change of shown or not. */
    interface Host {
        void focusDockFrom(int card);

        void focusTopBar();

        void onRowChanged(boolean showing);
    }

    private final Activity activity;
    private final Handler handler;
    private final Host host;
    private final float dp;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> pictures = new HashMap<>();
    private final List<View> cards = new ArrayList<>();
    final LinearLayout view;
    private final LinearLayout strip;
    private String shownKey = "";
    private boolean reading;

    ContinueRow(Activity activity, FrameLayout root, Handler handler, Host host) {
        this.activity = activity;
        this.handler = handler;
        this.host = host;
        dp = activity.getResources().getDisplayMetrics().density;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setVisibility(View.GONE);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        TextView heading = new TextView(activity);
        heading.setText(R.string.cw_title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        heading.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        heading.setShadowLayer(8 * dp, 0, dp, 0x99000000);
        heading.setPadding(0, 0, 0, Math.round(12 * dp));
        view.addView(heading);
        strip = new LinearLayout(activity);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        view.addView(strip);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
    }

    boolean showing() {
        return !cards.isEmpty();
    }

    /** Focus card i (clamped to the row); false when the row is empty. */
    boolean focusCard(int i) {
        if (cards.isEmpty()) return false;
        return cards.get(Math.max(0, Math.min(i, cards.size() - 1))).requestFocus();
    }

    /** Read the provider again and show what changed. Called whenever Home comes back. */
    void refresh() {
        if (reading) return;
        reading = true;
        worker.execute(() -> {
            List<WatchNext.Item> items = WatchNext.select(WatchNextReader.read(activity), System.currentTimeMillis(), MAX);
            handler.post(() -> {
                reading = false;
                show(items);
            });
        });
    }

    private void show(List<WatchNext.Item> items) {
        StringBuilder key = new StringBuilder();
        for (WatchNext.Item item : items) key.append(item).append('\n');
        if (key.toString().equals(shownKey)) return;
        shownKey = key.toString();
        boolean hadFocus = view.hasFocus();
        strip.removeAllViews();
        cards.clear();
        Map<String, Bitmap> keep = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            WatchNext.Item item = items.get(i);
            View card = card(item, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(200 * dp), Math.round(112 * dp));
            if (i > 0) lp.setMarginStart(Math.round(14 * dp));
            strip.addView(card, lp);
            cards.add(card);
            Bitmap known = pictures.get(item.image());
            if (known != null) keep.put(item.image(), known);
        }
        pictures.clear();
        pictures.putAll(keep);
        view.setVisibility(cards.isEmpty() ? View.GONE : View.VISIBLE);
        if (hadFocus && !focusCard(0)) host.focusTopBar();
        host.onRowChanged(showing());
    }

    private View card(WatchNext.Item item, int index) {
        FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setContentDescription(item.title);
        float radius = 12 * dp;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0x26FFFFFF);
        ImageView picture = new ImageView(activity);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(picture, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        load(item.image(), picture);
        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x33000000, 0xD9000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView badge = text(appName(item.packageName), 9, Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x8C000000);
        pill.setCornerRadius(10 * dp);
        badge.setBackground(pill);
        badge.setPadding(Math.round(7 * dp), Math.round(2 * dp), Math.round(7 * dp), Math.round(3 * dp));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        badgeLp.setMargins(Math.round(8 * dp), Math.round(8 * dp), 0, 0);
        card.addView(badge, badgeLp);
        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(10 * dp);
        info.setPadding(pad, 0, pad, Math.round(9 * dp));
        TextView title = text(item.title, 13, Color.WHITE);
        title.setTypeface(UiFont.get(activity, R.string.font_medium));
        info.addView(title);
        String detail = detail(item);
        if (!detail.isEmpty()) info.addView(text(detail, 10, 0xC7FFFFFF));
        int percent = item.percent();
        if (percent >= 0) info.addView(bar(percent));
        card.addView(info, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        card.setForeground(ring);
        Runnable look = () -> {
            boolean on = card.isFocused();
            ring.setStroke(Math.round((on ? 2.5f : 1f) * dp), on ? Color.WHITE : 0x2EFFFFFF);
            float scale = on ? 1.06f : 1f;
            card.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            card.setElevation(on ? 8 * dp : 0);
        };
        look.run();
        card.setOnFocusChangeListener((v, focused) -> look.run());
        card.setOnClickListener(v -> open(item));
        card.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.focusDockFrom(index);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    host.focusTopBar();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return index == 0;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return index == cards.size() - 1;
                default:
                    return false;
            }
        });
        return card;
    }

    /** "T3 · E25 · 12 min left", "Next episode · E2", or the minutes left of a film. */
    private String detail(WatchNext.Item item) {
        List<String> parts = new ArrayList<>();
        if (item.type == WatchNext.TYPE_NEXT) parts.add(activity.getString(R.string.cw_next));
        if (!item.season.isEmpty() && !item.episode.isEmpty()) {
            parts.add(activity.getString(R.string.cw_episode, item.season, item.episode));
        } else if (!item.episode.isEmpty()) {
            parts.add(activity.getString(R.string.cw_episode_only, item.episode));
        }
        int left = item.minutesLeft();
        if (item.type == WatchNext.TYPE_CONTINUE && left > 0) parts.add(activity.getString(R.string.cw_left, left));
        return TextUtils.join(" · ", parts);
    }

    private View bar(int percent) {
        LinearLayout track = new LinearLayout(activity);
        GradientDrawable back = new GradientDrawable();
        back.setColor(0x40FFFFFF);
        back.setCornerRadius(2 * dp);
        track.setBackground(back);
        View fill = new View(activity);
        GradientDrawable front = new GradientDrawable();
        front.setColor(Color.WHITE);
        front.setCornerRadius(2 * dp);
        fill.setBackground(front);
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, percent)));
        track.addView(new View(activity), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(0, 100 - percent)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(3 * dp));
        lp.topMargin = Math.round(6 * dp);
        track.setLayoutParams(lp);
        return track;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setShadowLayer(4 * dp, 0, 0, 0x80000000);
        return view;
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** The row's own link (an intent: URI or a deep link) in its app; the app itself when the link fails. */
    private void open(WatchNext.Item item) {
        Intent intent;
        try {
            intent = item.intent.startsWith("intent:") ? Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(item.intent));
            intent.setPackage(item.packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return;
        } catch (Exception e) {
            Log.w(TAG, "could not open " + item.intent + ": " + e);
        }
        Intent app = activity.getPackageManager().getLeanbackLaunchIntentForPackage(item.packageName);
        if (app == null) app = activity.getPackageManager().getLaunchIntentForPackage(item.packageName);
        if (app == null) return;
        try {
            activity.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
            // nothing more to try
        }
    }

    /** The picture at address, from memory or fetched and scaled down to the card's width. */
    private void load(String address, ImageView into) {
        Bitmap known = pictures.get(address);
        if (known != null) {
            into.setImageBitmap(known);
            return;
        }
        if (!address.startsWith("http")) return;
        int width = Math.round(220 * dp);
        images.execute(() -> {
            Bitmap bitmap = fetch(address, width);
            if (bitmap == null) return;
            handler.post(() -> {
                pictures.put(address, bitmap);
                into.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap fetch(String address, int width) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) > 0 && body.size() < 4_000_000; ) body.write(buffer, 0, n);
            }
            byte[] bytes = body.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / (options.inSampleSize * 2) >= width) options.inSampleSize *= 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception e) {
            Log.w(TAG, "picture failed: " + e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    void close() {
        worker.shutdownNow();
        images.shutdownNow();
    }
}
