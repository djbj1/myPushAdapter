# 06. Wie die Smartphone-Anbindung genau funktioniert (SSE-Architektur)

Dieses Dokument erklärt im Detail, wie dein Android-Smartphone und **ioBroker** ohne Cloud-Zwang, ohne Firebase und ohne ständiges Polling in Echtzeit miteinander kommunizieren.

---

## 1. Grundprinzip: Wer verbindet sich mit wem?

Ein häufiges Missverständnis ist die Richtung der Verbindung: **Nicht ioBroker „ruft“ das Smartphone an**, sondern **das Smartphone öffnet eine dauerhafte Verbindung zu ioBroker** und lässt diese Leitung offen stehen.

Das hat drei große Vorteile:
1. **ioBroker muss die IP-Adresse des Handys nicht kennen** (egal, welche IP das Handy per DHCP bekommt).
2. **Beliebig viele Smartphones** (z. B. Partner/Familie) können sich gleichzeitig an Port `8095` andocken.
3. **Echtzeit (< 50 ms):** Sobald jemand klingelt, schiebt ioBroker die Nachricht einfach durch die bereits offene Leitung.

Die Technik dahinter heißt **SSE (Server-Sent Events)** – das ist eine normale HTTP-GET-Verbindung, die vom Server nicht beendet wird, sondern offen bleibt (`Content-Type: text/event-stream`).

---

## 2. Ablauf vom Speichern bis zum Klingeln

```text
[Smartphone: App-UI]                  [Android Hintergrund]                [ioBroker :8095]
         |                                      |                                 |
         |-- 1. Speichern (IP:8095) ----------->| DoorbellSyncService             |
         |                                      |-- 2. GET /events (SSE) -------->|
         |                                      |<-- 3. 200 OK + connected -------|
         |                                      |                                 |
         |                                      |<-- 4. Alle 25s: ": ping" -------| (Leitung bleibt offen)
         |                                      |                                 |
         |                                      |      [Klingel-Sensor = true]    |
         |                                      |<-- 5. event: doorbell_ring -----|
         |                                      |                                 |
         |                                      |-- 6. WakeLock + AlarmService -->|
         |<-- 7. Vollbild + Dauerton (ALARM) ---|    (DoorbellAlarmService)       |
         |                                      |                                 |
         |-- 8. "Stumm / Quittieren" ---------->|                                 |
         |                                      |-- 9. GET /ack ----------------->|
         |                                      |<-- 10. event: doorbell_stop ----| (stoppt alle Handys)
```

---

## 3. Die beteiligten Komponenten im Detail

### A. Konfiguration & Hintergrund-Wächter auf dem Handy
1. **Speichern in der App ([`src/main.js`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/src/main.js)):**
   * Wenn du auf *„Speichern & Verbinden“* tippst, baut die UI die Ziel-URLs zusammen (z. B. `sseUrl = http://192.168.178.68:8095/events`, `ackUrl = .../ack`, `openDoorUrl = .../open-door`).
   * [`DoorbellPlugin`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellPlugin.java) speichert diese URLs dauerhaft in den Android-`SharedPreferences` ([`DoorbellConfig`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellConfig.java)) und startet [`DoorbellSyncService`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellSyncService.java).

2. **Warum der Wächter auch ohne offene App läuft:**
   * [`DoorbellSyncService`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellSyncService.java) ist ein **Android Foreground Service** (`foregroundServiceType="dataSync"`, `stopWithTask="false"`, Rückgabewert `START_STICKY`).
   * Dadurch erscheint die dauerhafte leise Meldung *„Türklingel-Wächter aktiv“* – im Gegenzug beendet Android den Dienst nicht sofort, wenn du die App wegwischst.
   * Nach einem Handy-Neustart startet [`BootReceiver`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/BootReceiver.java) den Wächter automatisch wieder.

---

### B. Der SSE-Stream & der Herzschlag (`: ping`)
In [`DoorbellSyncService.java`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellSyncService.java) läuft ein eigener Netzwerk-Thread (`runSyncLoop`):

* Er öffnet per `HttpURLConnection` die URL `/events` mit:
  * `ConnectTimeout = 10.000 ms` (10 Sekunden)
  * `ReadTimeout = 90.000 ms` (90 Sekunden)
* **Wofür der 25-Sekunden-Ping von ioBroker da ist:**
  * [`iobroker/klingel_sse_server.js`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/iobroker/klingel_sse_server.js) sendet alle **25 Sekunden** einen unsichtbaren SSE-Kommentar `: ping\n\n`.
  * Dadurch bleibt die TCP-Verbindung für Router und Android sichtbar aktiv, und das Handy empfängt mindestens alle 25s ein Byte – der 90s-`ReadTimeout` läuft im Normalbetrieb also nie ab.
  * Bricht das WLAN wirklich ab (z. B. beim Verlassen des Hauses), läuft spätestens nach 90s der `ReadTimeout` ab (oder der Socket meldet sofort einen Fehler). Die App geht auf `Getrennt` und versucht alle **4 Sekunden** (`sleepQuietly(4000)`) automatisch einen Neuaufbau.

---

### C. Auslösen des Alarms & Aufwecken im Sperrzustand
1. In ioBroker ändert sich `SENSOR_ID` auf `true`.
2. Nach Prüfung der Entprellzeit (`COOLDOWN_SEK = 15`) schreibt `broadcastEvent("doorbell_ring", ...)` folgenden Block in alle offenen `clients`:
   ```text
   event: doorbell_ring
   data: {"timestamp":1731380000,"title":"Haustuerklingel","message":"Es klingelt an der Haustuer!","cameraUrl":""}

   ```
3. [`DoorbellSyncService`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellSyncService.java) liest diesen Block ein, holt sich einen **10-Sekunden-`PARTIAL_WAKE_LOCK`** (damit die CPU im Tiefschlaf nicht einschläft) und startet [`DoorbellAlarmService`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellAlarmService.java).
4. [`DoorbellAlarmService`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/DoorbellAlarmService.java):
   * Spielt [`alarm.wav`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/res/raw/alarm.wav) per `MediaPlayer` in einer **Endlosschleife** über **`AudioAttributes.USAGE_ALARM`** (umgeht damit „Nicht stören“ und Medien-Stummschaltung).
   * Startet das Vibrationsmuster.
   * Setzt eine `PRIORITY_MAX`-Benachrichtigung (`CATEGORY_CALL`) mit **`setFullScreenIntent(...)`**, wodurch [`MainActivity`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/android/app/src/main/java/de/heimnetz/tuerklingel/MainActivity.java) (`showWhenLocked="true"`, `turnScreenOn="true"`) im Vollbild über dem Sperrbildschirm erscheint.
   * Beendet sich nach **2 Minuten** automatisch zur Sicherheit, falls niemand zuhause ist.

---

### D. Der Rückweg: Quittieren (`/ack`) & Tür öffnen (`/open-door`)
* **Tippen auf „🔇 Stumm / Quittieren“** (in der App oder direkt auf dem Button der Benachrichtigung):
  * Stoppt `MediaPlayer`, Vibration und Benachrichtigung sofort auf dem Handy.
  * Sendet im Hintergrund einen kurzen `HTTP GET` an `http://<IOBROKER_IP>:8095/ack`.
  * ioBroker empfängt `/ack` und sendet `event: doorbell_stop` an alle anderen verbundenen Smartphones – sodass auch alle übrigen Familien-Handys sofort aufhören zu klingeln.
* **Tippen auf „🚪 Tür öffnen“** (falls in den App-Einstellungen aktiviert):
  * Stoppt den Alarm lokal und sendet `HTTP GET` an `http://<IOBROKER_IP>:8095/open-door`.
  * ioBroker setzt den hinterlegten Datenpunkt `DOOR_OPEN_ID` auf `true` und sendet `doorbell_stop` an alle Geräte.

---

## 4. Lokal/VPN vs. Unterwegs ohne VPN (`ntfy.sh`)

Die App unterstützt beide Betriebsarten über denselben SSE-Mechanismus:

| Modus | Wie die App sich verbindet | Vorteile | Einschränkungen |
| :--- | :--- | :--- | :--- |
| **🏠 Lokal / VPN** | Verbindet sich direkt mit `http://<ioBroker-IP>:8095/events` | 100 % lokal, < 50 ms Latenz, keine Cloud, unterstützt `/ack` & `/open-door` | Funktioniert nur im Heim-WLAN oder bei aktivem VPN (z. B. WireGuard / Fritz!Box-VPN) |
| **🌐 Überall (`ntfy.sh`)** | Verbindet sich mit `https://ntfy.sh/<topic>/sse`; ioBroker sendet bei Klingeln zusätzlich per HTTPS-POST an `ntfy.sh` | Funktioniert weltweit im 4G/5G-Netz **ohne VPN** und **ohne Portfreigabe** am Router | Im reinen ntfy-Modus sind Rückkanal `/ack` und `/open-door` in der App deaktiviert |

---

## 5. Diagnose & typische Stolpersteine

### 1. Nach Skript-Änderung in ioBroker klingelt es nicht sofort
* Beim Speichern eines Skripts in ioBroker wird die alte Instanz beendet und die neue gestartet.
* Deshalb enthält [`iobroker/klingel_sse_server.js`](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/iobroker/klingel_sse_server.js) ein sauberes `onStop((callback) => ...)`, das offene SSE-Sockets mit `res.destroy()` und `server.closeAllConnections()` schließt und auf `server.close()` wartet, bevor die neue Instanz Port `8095` übernimmt (inkl. automatischem Retry bei `EADDRINUSE`).
* Das Smartphone erkennt den Abbruch und baut die Verbindung innerhalb von **ca. 4 Sekunden** selbst wieder auf.

### 2. Im ioBroker-Log prüfen
Nach dem Start des Skripts sollten folgende Einträge sichtbar sein:
```text
[Klingel-SSE] Server gestartet auf Port 8095
[Klingel-SSE] Smartphone verbunden (1 aktiv).
```
Steht beim Klingeln im Log:
`[Klingel-SSE] Klingel betätigt, aber 0 Smartphones lokal verbunden!`
→ dann hat das Handy aktuell keine offene SSE-Leitung (z. B. anderes WLAN, IP/Port falsch oder Akku-Optimierung hat den Hintergrund-Socket gestoppt).

### 3. Schneller Browser-Test ohne auf die echte Klingel zu drücken
* **Status prüfen (im Handy-Browser):** `http://192.168.178.68:8095/status` → zeigt `{"ok":true,"clients":1}`
* **Live-Test auslösen (am PC oder Handy-Browser):** `http://192.168.178.68:8095/test` → schickt sofort ein `doorbell_ring`-Event an alle verbundenen Smartphones!

### 4. Android-Akku-Optimierung (wichtig für Langzeitbetrieb)
* Damit Android die SSE-Leitung nachts im Tiefschlaf (*Doze Mode*) nicht kappt, in der App unter **Android System-Optimierung** unbedingt auf **„🔋 Akku-Optimierung ausschalten“** tippen (*Nicht eingeschränkt*).
