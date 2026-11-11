package de.heimnetz.tuerklingel;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DoorbellPlugin")
public class DoorbellPlugin extends Plugin {
    private static volatile DoorbellPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    public static void emitConnectionState(String state) {
        DoorbellPlugin plugin = instance;
        if (plugin != null) {
            JSObject data = new JSObject();
            data.put("state", state);
            plugin.notifyListeners("connectionStateChanged", data);
        }
    }

    public static void emitAlarmState(boolean active, String title, String message, String cameraUrl) {
        DoorbellPlugin plugin = instance;
        if (plugin != null) {
            JSObject data = new JSObject();
            data.put("active", active);
            data.put("title", title != null ? title : "");
            data.put("message", message != null ? message : "");
            data.put("cameraUrl", cameraUrl != null ? cameraUrl : "");
            plugin.notifyListeners("alarmStateChanged", data);
        }
    }

    @PluginMethod
    public void saveConfig(PluginCall call) {
        Context context = getContext();
        String sseUrl = call.getString("sseUrl", "");
        String ackUrl = call.getString("ackUrl", "");
        String openDoorUrl = call.getString("openDoorUrl", "");
        String cameraUrl = call.getString("cameraUrl", "");
        boolean serviceEnabled = Boolean.TRUE.equals(call.getBoolean("serviceEnabled", true));

        DoorbellConfig.save(context, sseUrl, ackUrl, openDoorUrl, cameraUrl, serviceEnabled);

        Intent syncIntent = new Intent(context, DoorbellSyncService.class);
        if (serviceEnabled && sseUrl != null && !sseUrl.trim().isEmpty()) {
            syncIntent.setAction(DoorbellSyncService.ACTION_RECONNECT);
            ContextCompat.startForegroundService(context, syncIntent);
        } else {
            context.stopService(syncIntent);
            DoorbellConfig.setConnectionState("disconnected");
        }

        JSObject res = new JSObject();
        res.put("ok", true);
        call.resolve(res);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject res = new JSObject();
        res.put("connectionState", DoorbellConfig.getConnectionState());
        res.put("isAlarmActive", DoorbellConfig.isAlarmActive());
        res.put("alarmTitle", DoorbellConfig.getAlarmTitle());
        res.put("alarmMessage", DoorbellConfig.getAlarmMessage());
        res.put("alarmCameraUrl", DoorbellConfig.getAlarmCameraUrl());
        call.resolve(res);
    }

    @PluginMethod
    public void triggerTestAlarm(PluginCall call) {
        Context context = getContext();
        Intent alarmIntent = new Intent(context, DoorbellAlarmService.class);
        alarmIntent.setAction(DoorbellAlarmService.ACTION_START_ALARM);
        alarmIntent.putExtra("title", "Test-Klingel");
        alarmIntent.putExtra("message", "Testalarm über Alarm-Audio-Kanal (USAGE_ALARM)");
        alarmIntent.putExtra("cameraUrl", DoorbellConfig.getCameraUrl(context));
        ContextCompat.startForegroundService(context, alarmIntent);

        JSObject res = new JSObject();
        res.put("ok", true);
        call.resolve(res);
    }

    @PluginMethod
    public void stopActiveAlarm(PluginCall call) {
        Context context = getContext();
        boolean sendAck = Boolean.TRUE.equals(call.getBoolean("sendAck", true));

        Intent stopIntent = new Intent(context, DoorbellAlarmService.class);
        stopIntent.setAction(DoorbellAlarmService.ACTION_STOP_ALARM);
        stopIntent.putExtra("sendAck", sendAck);
        context.startService(stopIntent);

        JSObject res = new JSObject();
        res.put("ok", true);
        call.resolve(res);
    }

    @PluginMethod
    public void triggerOpenDoor(PluginCall call) {
        Context context = getContext();
        Intent stopIntent = new Intent(context, DoorbellAlarmService.class);
        stopIntent.setAction(DoorbellAlarmService.ACTION_STOP_ALARM);
        stopIntent.putExtra("sendAck", false);
        context.startService(stopIntent);

        DoorbellConfig.fireHttpGetAsync(DoorbellConfig.getOpenDoorUrl(context));

        JSObject res = new JSObject();
        res.put("ok", true);
        call.resolve(res);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void openBatteryOptimizationSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            getActivity().startActivity(fallback);
        }
        call.resolve();
    }

    @PluginMethod
    public void openNotificationPolicySettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            getActivity().startActivity(intent);
        }
        call.resolve();
    }
}

