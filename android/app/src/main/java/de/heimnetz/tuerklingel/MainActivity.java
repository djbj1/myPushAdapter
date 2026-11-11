package de.heimnetz.tuerklingel;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DoorbellPlugin.class);
        super.onCreate(savedInstanceState);

        applyLockscreenWakeupFlags();
        startSyncServiceIfEnabled();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLockscreenWakeupFlags();
    }

    private void applyLockscreenWakeupFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null && DoorbellConfig.isAlarmActive()) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        if (DoorbellConfig.isAlarmActive()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void startSyncServiceIfEnabled() {
        String sseUrl = DoorbellConfig.getSseUrl(this);
        if (DoorbellConfig.isServiceEnabled(this) && sseUrl != null && !sseUrl.trim().isEmpty()) {
            try {
                Intent syncIntent = new Intent(this, DoorbellSyncService.class);
                ContextCompat.startForegroundService(this, syncIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
