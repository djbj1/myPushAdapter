# 05. Umsetzung mit Capacitor & Node.js (Web-App als Android-APK)

Dieses Dokument beschreibt die vollständige Umsetzung einer eigenen Android-Türklingel-App mit **Capacitor**. 

Mit diesem Ansatz schreibst du die Benutzeroberfläche (Klingelbildschirm, Kamerabild, Türöffner-Button) in **HTML/CSS/JavaScript** und nutzt eine schlanke native Android-Bridge (ca. 60 Zeilen Kotlin) für den DND-Bypass, Dauerton und das Aufwecken des Sperrbildschirms.

---

## 1. Gesamtarchitektur

```
┌────────────────────────────────────────────────────────┐
│  ioBroker (Node.js)                                    │
│  1. Klingel-Taster wird gedrückt                       │
│  2. ioBroker sendet FCM Data-Push an Smartphone        │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼ FCM High-Priority Data Push
┌────────────────────────────────────────────────────────┐
│  Android Gerät (Capacitor App)                         │
│                                                        │
│  [Nativer Teil - Kotlin Bridge]                        │
│  1. FirebaseMessagingService fängt Data-Push ab        │
│  2. Startet Dauerschleife über Audio USAGE_ALARM       │
│  3. Weckt Display auf & startet FullScreenIntent       │
│                           │                            │
│                           ▼                            │
│  [Webview - Deine Web-UI in HTML/CSS/JS]               │
│  - Zeigt "Es klingelt!" & Kamera-Livebild             │
│  - Button "Stumm" -> ruft DoorbellPlugin.stopAlarm()   │
│  - Button "Tür öffnen" -> sendet fetch() an ioBroker   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Projekt-Setup (Schritt für Schritt)

### Schritt 2.1: Web-Projekt initialisieren
Erstelle im Workspace (oder in einem Unterordner `client/`) das Capacitor-Projekt:

```powershell
# Paket initialisieren
npm init -y

# Capacitor-Kern und Android-Plattform installieren
npm install @capacitor/core @capacitor/cli @capacitor/android

# Capacitor konfigurieren
npx cap init "Doorbell" "de.heimnetz.doorbell" --web-dir dist
```

### Schritt 2.2: Android-Plattform hinzufügen
```powershell
npx cap add android
```
*(Es wird der native Android-Ordner `android/` generiert).*

---

## 3. Die native Android-Bridge (Das Herzstück)

Damit die App den Bildschirm im gesperrten Zustand aufwecken und DND umgehen kann, werden folgende 3 Anpassungen im `android/`-Ordner vorgenommen:

### A. Android-Rechte & Activity-Attribute (`android/app/src/main/AndroidManifest.xml`)

Füge folgende Rechte oberhalb des `<application>`-Tags ein:
```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

Passe das Tag der `MainActivity` so an, dass sie über dem Sperrbildschirm angezeigt werden darf:
```xml
<activity
    android:name=".MainActivity"
    android:label="@string/app_name"
    android:theme="@style/AppTheme.NoActionBar"
    android:launchMode="singleTop"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

---

### B. Das Capacitor Plugin zur Ton-Steuerung (`DoorbellPlugin.kt`)
Erstelle die Datei `android/app/src/main/java/de/heimnetz/doorbell/DoorbellPlugin.kt`:

```kotlin
package de.heimnetz.doorbell

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "DoorbellPlugin")
class DoorbellPlugin : Plugin() {

    companion object {
        var mediaPlayer: MediaPlayer? = null

        fun startAlarmSound(context: android.content.Context) {
            if (mediaPlayer?.isPlaying == true) return

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // Bricht DND!
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true // Dauerschleife bis Quittierung
                prepare()
                start()
            }
        }

        fun stopAlarmSound() {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        }
    }

    // Von JavaScript aus aufrufbar: DoorbellPlugin.stopAlarm()
    @PluginMethod
    fun stopAlarm(call: PluginCall) {
        stopAlarmSound()
        call.resolve()
    }
}
```

---

### C. FCM Service für das Vollbild-Aufwecken (`DoorbellFirebaseService.kt`)
Erstelle die Datei `android/app/src/main/java/de/heimnetz/doorbell/DoorbellFirebaseService.kt`:

```kotlin
package de.heimnetz.doorbell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DoorbellFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Prüfen, ob es sich um unseren Türklingel-Alarm handelt
        if (remoteMessage.data["type"] == "DOORBELL_CALL") {
            // 1. Dauerton starten
            DoorbellPlugin.startAlarmSound(applicationContext)

            // 2. Vollbild-Activity aufwecken
            triggerFullScreenCall(remoteMessage.data["camera_url"])
        }
    }

    private fun triggerFullScreenCall(cameraUrl: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("camera_url", cameraUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "doorbell_emergency_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Türklingel Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🚨 Es klingelt an der Tür!")
            .setContentText("Tippe zum Ansehen / Öffnen")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true) // Weckt Display im Vollbild auf
            .setAutoCancel(true)
            .build()

        manager.notify(2001, notification)
    }
}
```

---

## 4. Die Web-Benutzeroberfläche (HTML, CSS & JS)

Erstelle im Ordner `dist/` (oder `public/`) deine Web-App:

### `dist/index.html`
```html
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>Haustürklingel</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: #121212;
            color: #ffffff;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            height: 100vh;
            padding: 24px;
            text-align: center;
        }
        .header { margin-top: 40px; }
        .header h1 { font-size: 28px; color: #ff5252; margin-bottom: 8px; }
        .camera-container {
            width: 100%;
            height: 250px;
            background: #2a2a2a;
            border-radius: 16px;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .camera-container img { width: 100%; height: 100%; object-fit: cover; }
        .actions { display: flex; flex-direction: column; gap: 16px; margin-bottom: 30px; }
        button {
            padding: 18px;
            font-size: 18px;
            font-weight: bold;
            border-radius: 12px;
            border: none;
            cursor: pointer;
        }
        .btn-open { background-color: #00c853; color: white; }
        .btn-mute { background-color: #424242; color: #e0e0e0; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔔 Türklingel</h1>
        <p id="status-text">Es klingelt an der Haustür...</p>
    </div>

    <div class="camera-container">
        <!-- Optional: Live Snapshot der Kamera -->
        <img id="camera-image" src="https://images.unsplash.com/photo-1558002038-1055907df827?w=600" alt="Kamera Livebild">
    </div>

    <div class="actions">
        <button class="btn-open" onclick="openDoor()">🚪 Tür öffnen</button>
        <button class="btn-mute" onclick="muteAlarm()">🔇 Stumm / Quittieren</button>
    </div>

    <script type="module" src="app.js"></script>
</body>
</html>
```

### `dist/app.js`
```javascript
import { registerPlugin } from '@capacitor/core';
const DoorbellPlugin = registerPlugin('DoorbellPlugin');

// ioBroker Simple-API Endpunkt (oder dein Backend)
const IOBROKER_URL = "http://192.168.1.10:8087"; 

// 1. Ton stummschalten
window.muteAlarm = async function() {
    try {
        await DoorbellPlugin.stopAlarm();
        document.getElementById("status-text").innerText = "Alarm quittiert.";
    } catch (e) {
        console.error("Fehler beim Stoppen des Alarms", e);
    }
};

// 2. Tür öffnen via ioBroker Simple-API
window.openDoor = async function() {
    // Zuerst Ton aus
    await window.muteAlarm();

    document.getElementById("status-text").innerText = "Öffne Tür...";

    try {
        // Schreibt true in den Türöffner-Datenpunkt in ioBroker
        await fetch(`${IOBROKER_URL}/set/alias.0.tuer.summer?value=true`);
        document.getElementById("status-text").innerText = "Tür wurde geöffnet!";
    } catch (error) {
        alert("Verbindungsfehler zu ioBroker: " + error.message);
    }
};
```

---

## 5. Das ioBroker-Gegenstück (FCM Push auslösen)

In ioBroker kannst du diesen Push mit dem Firebase Admin SDK über ein JavaScript absenden:

```javascript
// ioBroker JavaScript: FCM Push an Capacitor App senden
const admin = require("firebase-admin");

// Service Account Key von deiner Firebase Konsole
const serviceAccount = require("/opt/iobroker/firebase-adminsdk.json");

if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
}

const SMARTPHONE_FCM_TOKEN = "DEIN_SMARTPHONE_FCM_TOKEN";

// Auf Klingel-Sensor lauschen
on({ id: "deconz.0.Sensors.12.button", change: "any", val: true }, async function () {
    const message = {
        token: SMARTPHONE_FCM_TOKEN,
        android: {
            priority: "high", // Erforderlich, um Handy aus dem Tiefschlaf zu wecken
            ttl: 60 * 1000
        },
        data: {
            type: "DOORBELL_CALL",
            camera_url: "http://192.168.1.50/snapshot.jpg",
            timestamp: Date.now().toString()
        }
    };

    try {
        const response = await admin.messaging().send(message);
        log("[FCM] Klingel-Push erfolgreich gesendet: " + response, "info");
    } catch (err) {
        log("[FCM] Fehler beim Senden: " + err.message, "error");
    }
});
```

---

## 6. App kompilieren & auf dem Handy installieren

```powershell
# 1. Web-Assets in den Android-Ordner kopieren
npx cap sync android

# 2. In Android Studio öffnen (oder direkt per CLI bauen)
npx cap open android
```

In Android Studio:
* Schließe dein Android-Handy per USB an.
* Drücke auf **"Run"** (grüner Play-Button).
* Die App wird direkt auf deinem Handy installiert und registriert.

