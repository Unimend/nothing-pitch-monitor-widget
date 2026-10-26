package com.unimend.pitchmonitor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.util.Arrays;

public class PitchService extends Service {
    static final String ACTION_START = "com.unimend.pitchmonitor.START";
    private static final int SAMPLE_RATE = 22050;
    private static final int FRAME_SIZE = 2048;
    private static final long RENDER_INTERVAL_MS = 200L;
    private static final long SILENCE_TIMEOUT_MS = 2L * 60L * 1000L;
    private static final long MAX_SESSION_MS = 30L * 60L * 1000L;
    private static volatile long lastHeartbeat;

    private final float[] history = new float[48];
    private final Bitmap[] frameBuffers = new Bitmap[2];
    private volatile boolean recording;
    private Thread worker;
    private AudioRecord audioRecord;
    private YinDetector detector;
    private PitchRenderer renderer;
    private float smoothedMidi = Float.NaN;
    private float smoothedDb = -80f;
    private float peakDb = -80f;
    private long peakAt;
    private long lastRender;
    private long lastSignalAt;
    private long sessionStartedAt;
    private int frameBufferIndex;
    private boolean receiverRegistered;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                publishIdleAndStop();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        Arrays.fill(history, Float.NaN);
        detector = new YinDetector(FRAME_SIZE);
        renderer = new PitchRenderer(this);
        frameBuffers[0] = Bitmap.createBitmap(PitchRenderer.WIDTH, PitchRenderer.HEIGHT,
                Bitmap.Config.ARGB_8888);
        frameBuffers[1] = Bitmap.createBitmap(PitchRenderer.WIDTH, PitchRenderer.HEIGHT,
                Bitmap.Config.ARGB_8888);
        frameBuffers[0].setDensity(Bitmap.DENSITY_NONE);
        frameBuffers[1].setDensity(Bitmap.DENSITY_NONE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(71, notification());
        if (PitchWidget.ids(this).length == 0 || !hasMicPermission() || !isInteractive()) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (!recording) startRecording();
        return START_NOT_STICKY;
    }

    private void startRecording() {
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minimum, FRAME_SIZE * 4);
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException();
            audioRecord.startRecording();
        } catch (RuntimeException error) {
            publishIdleAndStop(); return;
        }
        recording = true;
        sessionStartedAt = SystemClock.elapsedRealtime();
        lastSignalAt = sessionStartedAt;
        lastHeartbeat = sessionStartedAt;
        worker = new Thread(new Runnable() {
            @Override public void run() { captureLoop(); }
        }, "PitchCapture");
        worker.start();
    }

    private void captureLoop() {
        short[] samples = new short[FRAME_SIZE];
        while (recording) {
            lastHeartbeat = SystemClock.elapsedRealtime();
            int offset = 0;
            while (recording && offset < samples.length) {
                int read = audioRecord.read(samples, offset, samples.length - offset);
                if (read <= 0) break;
                offset += read;
            }
            if (!recording || offset < FRAME_SIZE / 2) continue;
            float db = calculateDb(samples, offset);
            smoothedDb = smoothedDb * 0.66f + db * 0.34f;
            long now = SystemClock.elapsedRealtime();
            if (db > peakDb || now - peakAt > 900L) { peakDb = db; peakAt = now; }

            float frequency = db > -58f ? detector.detect(samples, offset, SAMPLE_RATE) : Float.NaN;
            float midi = Float.NaN;
            if (!Float.isNaN(frequency)) {
                midi = (float) (69d + 12d * Math.log(frequency / 440d) / Math.log(2d));
                if (Float.isNaN(smoothedMidi) || Math.abs(midi - smoothedMidi) > 1.5f) smoothedMidi = midi;
                else smoothedMidi = smoothedMidi * 0.72f + midi * 0.28f;
                midi = smoothedMidi;
            }
            if (!Float.isNaN(midi) || db > -45f) lastSignalAt = now;
            if (now - lastSignalAt >= SILENCE_TIMEOUT_MS
                    || now - sessionStartedAt >= MAX_SESSION_MS) {
                publishIdleAndStop();
                break;
            }
            pushHistory(midi);
            if (now - lastRender >= RENDER_INTERVAL_MS) {
                publish(frequency, midi, smoothedDb, peakDb);
                lastRender = now;
            }
        }
    }

    private float calculateDb(short[] samples, int count) {
        double sum = 0d;
        for (int i = 0; i < count; i++) {
            double normalized = samples[i] / 32768d;
            sum += normalized * normalized;
        }
        double rms = Math.sqrt(sum / Math.max(1, count));
        return (float) Math.max(-80d, 20d * Math.log10(Math.max(rms, 0.0001d)));
    }

    private void pushHistory(float midi) {
        System.arraycopy(history, 1, history, 0, history.length - 1);
        history[history.length - 1] = midi;
    }

    private void publish(float frequency, float midi, float db, float peak) {
        int[] ids = PitchWidget.ids(this);
        if (ids.length == 0) { stopSelf(); return; }
        frameBufferIndex = (frameBufferIndex + 1) % frameBuffers.length;
        Bitmap bitmap = renderer.draw(frameBuffers[frameBufferIndex],
                frequency, midi, db, peak, history);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        for (int id : ids) {
            RemoteViews views = PitchWidget.baseViews(this, id);
            views.setImageViewBitmap(R.id.content, bitmap);
            manager.updateAppWidget(id, views);
        }
    }

    private void publishIdleAndStop() {
        PitchWidget.publishIdle(this); stopSelf();
    }

    static boolean isRunning(Context context) {
        return SystemClock.elapsedRealtime() - lastHeartbeat < 1000L;
    }

    private boolean hasMicPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isInteractive() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        return manager == null || manager.isInteractive();
    }

    private Notification notification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "pitch_monitor", "音高监视器", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("实时麦克风音高和声音强度；熄屏自动停止");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(this, 72, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "pitch_monitor") : new Notification.Builder(this);
        return builder.setContentTitle("点阵音高监视器")
                .setContentText("正在监听；熄屏、静音超时或 30 分钟后自动停止")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending).setOngoing(true).build();
    }

    @Override public void onDestroy() {
        recording = false;
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (RuntimeException ignored) {}
            audioRecord.release(); audioRecord = null;
        }
        if (worker != null && worker != Thread.currentThread()) {
            try { worker.join(300L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        lastHeartbeat = 0L;
        PitchWidget.publishIdle(this);
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        for (Bitmap bitmap : frameBuffers) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
