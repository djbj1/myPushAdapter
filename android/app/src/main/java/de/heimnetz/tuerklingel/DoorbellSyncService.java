package de.heimnetz.tuerklingel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DoorbellSyncService extends Service {
    private static final String TAG = "DoorbellSyncService";
    private static final String CHANNEL_ID = "doorbell_sync_channel_v1";
    private static final int NOTIFICATION_ID = 9901;
    public static final String ACTION_RECONNECT = "de.heimnetz.tuerklingel.ACTION_RECONNECT";

    private Thread syncThread;
    private volatile boolean isRunning = false;
    private volatile HttpURLConnection activeConn;
    private ConnectivityManager.NetworkCallback networkCallback;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        boolean isWifi = false;
                        try {
                            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                            isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                        } catch (Exception ignored) {}

                        if (isWifi) {
                            Log.i(TAG, "Heim-WLAN verbunden! Starte sofortige SSE-Wiederverbindung...");
                            acquireWifiLock();
                        } else {
                            Log.i(TAG, "Mobilfunk aktiv. Gehe in Energiespar-Wartemodus...");
                            releaseWifiLock();
                        }
                        forceReconnect();
                    }

                    @Override
                    public void onLost(Network network) {
                        Log.i(TAG, "Netzwerk getrennt.");
                        releaseWifiLock();
                        forceReconnect();
                    }
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(networkCallback);
                } else {
                    NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                    cm.registerNetworkCallback(req, networkCallback);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to register network callback: " + e.getMessage());
        }
    }

    private void acquireWifiLock() {
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    int mode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                    wifiLock = wm.createWifiLock(mode, "tuerklingel:sync_wifi_lock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.d(TAG, "WifiLock aktiv (verhindert WLAN-Einschlafen zuhause).");
            }
        } catch (Exception e) {
            Log.w(TAG, "WifiLock error: " + e.getMessage());
        }
    }

    private void releaseWifiLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.d(TAG, "WifiLock freigegeben (schont Akku außer Haus).");
            }
        } catch (Exception ignored) {}
    }

    private void unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
                networkCallback = null;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!DoorbellConfig.isServiceEnabled(getApplicationContext())) {
            DoorbellConfig.setConnectionState("disconnected");
            releaseWifiLock();
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notif = buildForegroundNotification("Wartet im Hintergrund auf Klingelsignale");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } catch (Exception e) {
                startForeground(NOTIFICATION_ID, notif);
            }
        } else {
            startForeground(NOTIFICATION_ID, notif);
        }

        if (intent != null && ACTION_RECONNECT.equals(intent.getAction())) {
            forceReconnect();
        }

        if (!isRunning) {
            isRunning = true;
            syncThread = new Thread(this::runSyncLoop, "DoorbellSseListener");
            syncThread.start();
        }

        return START_STICKY;
    }

    private void forceReconnect() {
        new Thread(() -> {
            try {
                if (activeConn != null) {
                    activeConn.disconnect();
                }
            } catch (Exception ignored) {}
            if (syncThread != null) {
                syncThread.interrupt();
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        unregisterNetworkCallback();
        releaseWifiLock();
        forceReconnect();
        DoorbellConfig.setConnectionState("disconnected");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Türklingel Wächter",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Hält die Verbindung zu ioBroker im Hintergrund offen");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(String statusText) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildForegroundNotification(statusText));
            }
        } catch (Exception ignored) {}
    }

    private Notification buildForegroundNotification(String statusText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Türklingel-Wächter")
            .setContentText(statusText != null ? statusText : "Wartet auf Klingelsignale")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void runSyncLoop() {
        Log.i(TAG, "Doorbell SSE sync loop started.");
        long backoffMs = 3000;

        while (isRunning) {
            String sseUrl = DoorbellConfig.getSseUrl(getApplicationContext());
            if (sseUrl == null || sseUrl.trim().isEmpty()) {
                DoorbellConfig.setConnectionState("disconnected");
                sleepQuietly(3000);
                continue;
            }

            try {
                DoorbellConfig.setConnectionState("connecting");
                updateNotification("🟡 Verbinde mit ioBroker...");
                connectEventStream(sseUrl.trim());
                backoffMs = 3000; // Reset nach erfolgreicher Verbindung
            } catch (Exception e) {
                Log.w(TAG, "SSE connection error: " + e.getMessage());
            }

            DoorbellConfig.setConnectionState("disconnected");
            releaseWifiLock();
            updateNotification("⚪ Außer Haus (Wartet auf Heim-WLAN)");
            if (isRunning) {
                sleepQuietly(backoffMs);
                // Sanfter Backoff unterwegs, wird durch NetworkCallback sofort unterbrochen
                backoffMs = Math.min(backoffMs + 3000, 15000);
            }
        }
    }

    private void connectEventStream(String sseUrl) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(sseUrl);
            conn = (HttpURLConnection) url.openConnection();
            activeConn = conn;
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(50000); // 50s (Heartbeat alle 25s reicht völlig)

            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "Unexpected HTTP code from SSE: " + conn.getResponseCode());
                return;
            }

            DoorbellConfig.setConnectionState("connected");
            acquireWifiLock();
            updateNotification("🟢 Zuhause verbunden mit ioBroker");
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String line;
            String currentEvent = "message";
            StringBuilder dataBuf = new StringBuilder();

            while (isRunning && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (dataBuf.length() > 0) {
                        handleSseEvent(currentEvent, dataBuf.toString());
                        dataBuf.setLength(0);
                    }
                    currentEvent = "message";
                } else if (line.startsWith(":")) {
                    // SSE Comment / Ping -> hält Socket am Leben
                } else if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (dataBuf.length() > 0) dataBuf.append('\n');
                    dataBuf.append(line.substring(5).trim());
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "SSE stream closed: " + e.getMessage());
        } finally {
            activeConn = null;
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void handleSseEvent(String eventType, String rawData) {
        try {
            if ("ping".equals(eventType) || "keepalive".equals(eventType) || "open".equals(eventType) || "connected".equals(eventType)) {
                return;
            }

            String title = "Haustürklingel";
            String message = "Es klingelt an der Haustür!";
            String cameraUrl = "";
            boolean shouldRing = false;
            boolean shouldStop = false;

            if (rawData.startsWith("{")) {
                JSONObject json = new JSONObject(rawData);
                String ntfyEvent = json.optString("event", "");

                if ("keepalive".equals(ntfyEvent) || "open".equals(ntfyEvent)) {
                    return;
                }

                if ("doorbell_ring".equals(eventType) || "message".equals(ntfyEvent)) {
                    shouldRing = true;
                    title = json.optString("title", title);
                    message = json.optString("message", message);
                    cameraUrl = json.optString("cameraUrl", "");
                    if (cameraUrl.isEmpty() && json.has("attachment")) {
                        JSONObject att = json.optJSONObject("attachment");
                        if (att != null) {
                            cameraUrl = att.optString("url", "");
                        }
                    }
                } else if ("doorbell_stop".equals(eventType)) {
                    shouldStop = true;
                }
            } else {
                if ("doorbell_ring".equals(eventType)) {
                    shouldRing = true;
                    if (!rawData.isEmpty()) message = rawData;
                } else if ("doorbell_stop".equals(eventType)) {
                    shouldStop = true;
                }
            }

            if (shouldRing) {
                triggerRingAlarm(title, message, cameraUrl);
            } else if (shouldStop) {
                stopRingAlarm();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse SSE event: " + e.getMessage(), e);
        }
    }

    private void triggerRingAlarm(String title, String message, String cameraUrl) {
        Context context = getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tuerklingel:sse_wakeup");
            wl.acquire(10000L);
        }

        Intent alarmIntent = new Intent(context, DoorbellAlarmService.class);
        alarmIntent.setAction(DoorbellAlarmService.ACTION_START_ALARM);
        alarmIntent.putExtra("title", title);
        alarmIntent.putExtra("message", message);
        alarmIntent.putExtra("cameraUrl", cameraUrl);
        ContextCompat.startForegroundService(context, alarmIntent);
    }

    private void stopRingAlarm() {
        Context context = getApplicationContext();
        Intent stopIntent = new Intent(context, DoorbellAlarmService.class);
        stopIntent.setAction(DoorbellAlarmService.ACTION_STOP_ALARM);
        stopIntent.putExtra("sendAck", false);
        context.startService(stopIntent);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
