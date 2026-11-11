# Türklingel Alarm (Schlanke Capacitor Android-App für ioBroker)

Eine schlanke, 100 % kostenlose Android-App (Capacitor 7 + Java), die **ausschließlich auf dem Smartphone** läuft und sich direkt mit **ioBroker** verbindet – ohne Pushover, ohne Google Firebase und ohne zusätzlichen Backend-Server.

Abgeleitet von der bewährten Alarm-Architektur aus [Ref/KidsScreenTime-main](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/Ref/KidsScreenTime-main).

---

## Was die App kann

* **Bricht "Nicht stören" (DND) & Lautlos:** Spielt den Alarmton (`res/raw/alarm.wav`) in einer Endlosschleife über `AudioAttributes.USAGE_ALARM` ab.
* **Anruf-Gefühl über Sperrbildschirm:** Weckt das Smartphone per `setFullScreenIntent` und `PARTIAL_WAKE_LOCK` aus dem Tiefschlaf und zeigt eine Vollbild-Anruf-Oberfläche.
* **Zwei Verbindungsmodi (in der App wählbar):**
  1. **Lokal / VPN (`http://<ioBroker-IP>:8095/events`):** Direkter Server-Sent-Events (SSE) Stream zu einem einzigen ioBroker-JavaScript (< 50 ms Latenz).
  2. **Überall ohne VPN (`https://ntfy.sh/<topic>/sse`):** Nutzt den kostenlosen ntfy SSE-Transport im Mobilfunknetz – trotzdem klingelt ausschließlich **diese eigene App** im Vollbild.
* **Quittieren & Tür öffnen:** Stoppt das Klingeln (und per `/ack` optional auch auf weiteren Familien-Handys) und sendet auf Wunsch einen Türöffner-Befehl an ioBroker.

---

## Projektstruktur

* [`src/`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/src/) – Schlanke HTML/CSS/JS Web-UI (Dashboard, Einstellungen, Vollbild-Anrufansicht).
* [`android/`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/) – Natives Android-Projekt mit:
  * [`DoorbellSyncService.java`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellSyncService.java) – Permanenter Hintergrund-SSE-Wächter.
  * [`DoorbellAlarmService.java`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellAlarmService.java) – Dauerklingelton (`USAGE_ALARM`), Vibration & Vollbild-Benachrichtigung.
  * [`DoorbellPlugin.java`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellPlugin.java) – Capacitor-Bridge zur Web-UI.
  * [`BootReceiver.java`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/BootReceiver.java) – Startet den Wächter nach Smartphone-Neustart automatisch.
* [`iobroker/klingel_sse_server.js`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/iobroker/klingel_sse_server.js) – Das fertige Skript für den ioBroker JavaScript-Adapter (`javascript.0`).
* [`build-apk.sh`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/build-apk.sh) & [`.github/workflows/build-apk.yml`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/.github/workflows/build-apk.yml) – Skripte zum Erstellen der Android `.apk`.

---

## Einrichtung in 3 Schritten

### 1. ioBroker-Skript aktivieren
1. Kopiere den Inhalt aus [`iobroker/klingel_sse_server.js`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/iobroker/klingel_sse_server.js) als neues JavaScript in ioBroker.
2. Trage oben deinen Klingel-Datenpunkt (`SENSOR_ID`) ein und starte das Skript.

### 2. Android APK kompilieren
Da auf diesem Windows-Rechner kein Java / Android SDK installiert ist, kannst du die APK auf einem von drei Wegen erzeugen:
* **Linux / Build-Server:** `./build-apk.sh` ausführen (erzeugt `android/app/build/outputs/apk/debug/app-debug.apk`).
* **GitHub Actions:** Projekt auf GitHub pushen – der Workflow [`.github/workflows/build-apk.yml`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/.github/workflows/build-apk.yml) baut die APK automatisch zum Herunterladen.
* **Android Studio:** `npm run cap:open` ausführen und auf **Build APK** klicken.

### 3. App auf dem Smartphone einrichten
1. Installiere die APK auf deinem Android-Handy.
2. Öffne die App, wähle **Lokal / VPN** (oder **Überall via ntfy.sh**), trage die ioBroker-IP (Port `8095`) ein und tippe auf **Speichern & Verbinden**.
3. Tippe unter *Android System-Optimierung* auf **Benachrichtigungen erlauben** und **Akku-Optimierung ausschalten**.
4. Nutze **"⏱️ Test in 5 Sek. (Sperre testen)"**, sperre das Handy sofort und prüfe das Vollbild-Aufwecken!

