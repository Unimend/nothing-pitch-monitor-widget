package com.unimend.pitchmonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.Locale;

final class PitchRenderer {
    static final int WIDTH = 900;
    static final int HEIGHT = 300;
    private static final float INFO_CENTER_X = 136f;
    private static final float GRAPH_LEFT = 324f;
    private static final float GRAPH_RIGHT = 700f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface doto;

    PitchRenderer(Context context) {
        Typeface loaded;
        try { loaded = Typeface.createFromAsset(context.getAssets(), "doto.ttf"); }
        catch (Exception ignored) { loaded = Typeface.DEFAULT_BOLD; }
        doto = loaded;
    }

    Bitmap drawIdle() {
        Bitmap bitmap = fresh();
        Canvas canvas = new Canvas(bitmap);
        drawBackground(canvas);
        text(canvas, "PITCH", 68f, 48f, 112f, Color.WHITE, Paint.Align.LEFT);
        text(canvas, "MONITOR", 43f, 48f, 166f, 0xff777777, Paint.Align.LEFT);
        drawIdleTrace(canvas);
        text(canvas, "TAP TO LISTEN", 34f, 852f, 265f, 0xffaaaaaa, Paint.Align.RIGHT);
        drawDbFrame(canvas, -80f, -80f, false);
        return bitmap;
    }

    Bitmap draw(Bitmap bitmap, float frequency, float midi, float db, float peakDb, float[] history) {
        bitmap.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(bitmap);
        drawBackground(canvas);

        boolean valid = !Float.isNaN(frequency) && !Float.isNaN(midi);
        String note = valid ? noteName(Math.round(midi)) : "--";
        int cents = valid ? Math.round((midi - Math.round(midi)) * 100f) : 0;
        int pitchColor = valid ? centsColor(cents) : 0xff777777;

        text(canvas, note, 104f, INFO_CENTER_X, 135f, pitchColor, Paint.Align.CENTER);
        text(canvas, valid ? String.format(Locale.US, "%+03d CENT", cents) : "NO PITCH",
                34f, INFO_CENTER_X, 201f, 0xffdddddd, Paint.Align.CENTER);
        text(canvas, valid ? String.format(Locale.US, "%.1f HZ", frequency) : "LISTENING",
                29f, INFO_CENTER_X, 249f, 0xff888888, Paint.Align.CENTER);

        drawPitchGraph(canvas, history, valid ? Math.round(midi) : Float.NaN);
        drawDbFrame(canvas, db, peakDb, true);
        return bitmap;
    }

    private Bitmap fresh() {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL); paint.setAntiAlias(false);
        for (int y = 0; y < HEIGHT; y += 30) {
            for (int x = 0; x < WIDTH; x += 30) {
                int alpha = 5 + ((x / 30 * 7 + y / 30 * 11) % 3) * 3;
                paint.setColor(Color.argb(alpha, 255, 255, 255));
                canvas.drawRect(x, y, x + 30, y + 30, paint);
            }
        }
        paint.setAntiAlias(true);
    }

    private void drawIdleTrace(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f); paint.setColor(0xff333333);
        float left = GRAPH_LEFT, right = GRAPH_RIGHT, middle = 150f;
        canvas.drawLine(left, middle, right, middle, paint);
        Path path = new Path(); path.moveTo(left, middle);
        for (int i = 0; i <= 36; i++) {
            float x = left + (right - left) * i / 36f;
            float y = middle + (float) Math.sin(i * 0.72f) * (7f + i * 0.35f);
            path.lineTo(x, y);
        }
        paint.setColor(0xff555555); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void drawPitchGraph(Canvas canvas, float[] history, float centerMidi) {
        float left = GRAPH_LEFT, right = GRAPH_RIGHT, top = 42f, bottom = 258f;
        paint.setAntiAlias(false); paint.setStrokeWidth(2f);
        for (int line = -2; line <= 2; line++) {
            float y = (top + bottom) / 2f - line * 43f;
            paint.setColor(line == 0 ? 0xff555555 : 0xff242424);
            canvas.drawLine(left, y, right, y, paint);
        }
        text(canvas, "PITCH TRACE", 24f, left, 28f, 0xff777777, Paint.Align.LEFT);
        if (Float.isNaN(centerMidi) || history == null || history.length < 2) return;

        Path path = new Path(); boolean started = false;
        float lastX = 0f, lastY = 0f;
        paint.setStrokeWidth(5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < history.length; i++) {
            float value = history[i];
            if (Float.isNaN(value)) { started = false; continue; }
            float x = left + (right - left) * i / (history.length - 1f);
            float delta = Math.max(-1.2f, Math.min(1.2f, value - centerMidi));
            float y = (top + bottom) / 2f - delta * 82f;
            if (!started) { path.moveTo(x, y); started = true; }
            else path.lineTo(x, y);
            lastX = x; lastY = y;
        }
        paint.setColor(0xffeeeeee); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xffff3038);
        if (started) canvas.drawCircle(lastX, lastY, 7f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawDbFrame(Canvas canvas, float db, float peakDb, boolean active) {
        float left = 760f, right = 838f, top = 54f, bottom = 244f;
        text(canvas, "DBFS", 24f, (left + right) / 2f, 31f, 0xff888888, Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL); paint.setAntiAlias(false);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            float yBottom = bottom - i * 14f;
            boolean on = active && db >= -72f + i * 6f;
            int color;
            if (!on) color = 0xff222222;
            else if (i >= 10) color = 0xffff3038;
            else if (i >= 8) color = 0xffffc52e;
            else color = 0xffeeeeee;
            paint.setColor(color); canvas.drawRect(left, yBottom - 9f, right, yBottom, paint);
        }
        if (active && peakDb > -79f) {
            float peakY = bottom - Math.max(0f, Math.min(1f, (peakDb + 72f) / 72f)) * 163f;
            paint.setColor(0xffff3038); canvas.drawRect(left - 5f, peakY - 2f, right + 5f, peakY + 2f, paint);
        }
        paint.setAntiAlias(true);
        String label = active ? String.format(Locale.US, "%.0f", db) : "--";
        text(canvas, label, 43f, (left + right) / 2f, 286f,
                active ? dbColor(db) : 0xff666666, Paint.Align.CENTER);
    }

    private void text(Canvas canvas, String value, float size, float x, float baseline,
                      int color, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setAntiAlias(true); paint.setTypeface(doto);
        paint.setTextSize(size); paint.setColor(color);
        if (align == Paint.Align.CENTER) {
            Rect bounds = new Rect();
            paint.setTextAlign(Paint.Align.LEFT);
            paint.getTextBounds(value, 0, value.length(), bounds);
            canvas.drawText(value, x - (bounds.left + bounds.right) / 2f, baseline, paint);
        } else {
            paint.setTextAlign(align);
            canvas.drawText(value, x, baseline, paint);
        }
    }

    private int centsColor(int cents) {
        if (Math.abs(cents) <= 5) return Color.WHITE;
        return cents > 0 ? 0xffff3038 : 0xff55bfff;
    }

    private int dbColor(float db) {
        if (db > -6f) return 0xffff3038;
        if (db > -18f) return 0xffffc52e;
        return Color.WHITE;
    }

    private String noteName(int midi) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int note = ((midi % 12) + 12) % 12;
        return names[note] + (midi / 12 - 1);
    }
}
