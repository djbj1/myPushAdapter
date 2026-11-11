package de.heimnetz.tuerklingel;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;

public class DoorbellConfig {
    private static final String PREFS_NAME = "DoorbellPrefs";

    private static volatile String connectionState = "disconnected";
    private static volatile boolean alarmActive = false;
    private static volatile String alarmTitle = "Haustürklingel";
    private static volatile String alarmMessage = "Es klingelt an der Haustür!";
    private static volatile String alarmCameraUrl = "";

    public static void save(Context context, String sseUrl, String ackUrl, String openDoorUrl, String cameraUrl, boolean serviceEnabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString("sseUrl", sseUrl != null ? sseUrl : "")
            .putString("ackUrl", ackUrl != null ? ackUrl : "")
            .putString("openDoorUrl", openDoorUrl != null ? openDoorUrl : "")
            .putString("cameraUrl", cameraUrl != null ? cameraUrl : "")
            .putBoolean("serviceEnabled", serviceEnabled)
            .apply();
    }

    public static String getSseUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("sseUrl", "");
    }

    public static String getAckUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("ackUrl", "");
    }

    public static String getOpenDoorUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("openDoorUrl", "");
    }

    public static String getCameraUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("cameraUrl", "");
    }

    public static boolean isServiceEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("serviceEnabled", true);
    }

    public static String getConnectionState() {
        return connectionState;
    }

    public static void setConnectionState(String state) {
        connectionState = state;
        DoorbellPlugin.emitConnectionState(state);
    }

    public static boolean isAlarmActive() {
        return alarmActive;
    }

    public static String getAlarmTitle() {
        return alarmTitle;
    }

    public static String getAlarmMessage() {
        return alarmMessage;
    }

    public static String getAlarmCameraUrl() {
        return alarmCameraUrl;
    }

    public static void setAlarmState(boolean active, String title, String message, String cameraUrl) {
        alarmActive = active;
        if (title != null && !title.isEmpty()) alarmTitle = title;
        if (message != null && !message.isEmpty()) alarmMessage = message;
        if (cameraUrl != null) alarmCameraUrl = cameraUrl;
        DoorbellPlugin.emitAlarmState(active, alarmTitle, alarmMessage, alarmCameraUrl);
    }

    public static void fireHttpGetAsync(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(targetUrl.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
