package de.heimnetz.tuerklingel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            String sseUrl = DoorbellConfig.getSseUrl(context);
            if (DoorbellConfig.isServiceEnabled(context) && sseUrl != null && !sseUrl.trim().isEmpty()) {
                Intent serviceIntent = new Intent(context, DoorbellSyncService.class);
                try {
                    ContextCompat.startForegroundService(context, serviceIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

