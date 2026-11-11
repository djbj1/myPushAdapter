package de.heimnetz.tuerklingel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class DoorbellAlarmService extends Service {
    private static final String TAG = "DoorbellAlarmService";
    public static final String ACTION_START_ALARM = "de.heimnetz.tuerklingel.ACTION_START_ALARM";
    public static final String ACTION_STOP_ALARM = "de.heimnetz.tuerklingel.ACTION_STOP_ALARM";
    public static final String CHANNEL_ID = "doorbell_emergency_alarm_v1";
    public static final int NOTIFICATION_ID = 999201;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            boolean sendAck = intent.getBooleanExtra("sendAck", false);
            Log.i(TAG, "Stopping alarm (sendAck=" + sendAck + ")");
            stopAlarm();
            if (sendAck) {
                DoorbellConfig.fireHttpGetAsync(DoorbellConfig.getAckUrl(getApplicationContext()));
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = intent != null ? intent.getStringExtra("title") : null;
        String message = intent != null ? intent.getStringExtra("message") : null;
        String cameraUrl = intent != null ? intent.getStringExtra("cameraUrl") : null;

        if (title == null || title.isEmpty()) title = "Haustürklingel";
        if (message == null || message.isEmpty()) message = "Es klingelt an der Haustür!";
        if (cameraUrl == null || cameraUrl.isEmpty()) cameraUrl = DoorbellConfig.getCameraUrl(getApplicationContext());

        startAlarm(title, message, cameraUrl);
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Türklingel Notfall-Alarm",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Dringende Türklingel-Alarme mit DND-Umgehung");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setBypassDnd(true);
            channel.enableVibration(true);
            channel.enableLights(true);

            // Sound wird direkt durch unseren MediaPlayer (USAGE_ALARM) im Loop gespielt
            channel.setSound(null, null);
            nm.createNotificationChannel(channel);
        }
    }

    private void startAlarm(String title, String message, String cameraUrl) {
        acquireWakeLock();
        DoorbellConfig.setAlarmState(true, title, message, cameraUrl);

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("fromAlarm", true);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags);

        Intent stopIntent = new Intent(this, DoorbellAlarmService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        stopIntent.putExtra("sendAck", true);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔔 " + title)
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🔇 STUMM / QUITTIEREN", stopPendingIntent);

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } catch (Exception e) {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Auch Activity direkt versuchen zu öffnen, falls Gerät entsperrt/wach
        try {
            startActivity(openAppIntent);
        } catch (Exception ignored) {}

        playLoopingSound();
        startVibration();

        // Sicherheits-Auto-Stop nach 2 Minuten
        if (autoStopHandler == null) {
            autoStopHandler = new Handler(Looper.getMainLooper());
        }
        if (autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }
        autoStopRunnable = () -> {
            Log.i(TAG, "Auto-stopping alarm after 2 minutes timeout");
            stopAlarm();
            stopSelf();
        };
        autoStopHandler.postDelayed(autoStopRunnable, 2 * 60 * 1000L);
    }

    private void playLoopingSound() {
        try {
            releasePlayer();

            mediaPlayer = new MediaPlayer();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

            mediaPlayer.setAudioAttributes(audioAttributes);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);

            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm);
            try {
                mediaPlayer.setDataSource(getApplicationContext(), soundUri);
            } catch (Exception e) {
                Uri fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (fallbackUri == null) {
                    fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
                mediaPlayer.setDataSource(getApplicationContext(), fallbackUri);
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Log.i(TAG, "Playing looping doorbell alarm via USAGE_ALARM");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MediaPlayer: " + e.getMessage(), e);
        }
    }

    private void startVibration() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 350, 600, 350};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes attr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attr);
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Vibration failed: " + e.getMessage());
        }
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "tuerklingel:alarm_ringing"
                    );
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(3 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
    }

    private void releasePlayer() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
    }

    private void stopAlarm() {
        DoorbellConfig.setAlarmState(false, null, null, null);

        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }

        releasePlayer();
        releaseWakeLock();

        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

