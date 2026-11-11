# 01. Architektur & Technische Grundlagen (Android DND & Push)

## 1. Warum normale Messenger (Telegram, WhatsApp) versagen
Wenn eine smarte Türklingel (z. B. Zigbee, Shelly, ESPHome) ausgelöst wird, nutzen viele Anwender Telegram- oder Pushover-Standardnachrichten. Dies führt in der Praxis zu folgenden Problemen:

1. **Einmaliger Ton ("Ping"):** Dauert 1–2 Sekunden. Wenn man sich im Nachbarraum befindet, Kopfhörer aufhat oder schläft, geht der Ton unter.
2. **"Nicht stören"-Modus (Do Not Disturb - DND):** Standard-Nachrichten werden stummgeschaltet und wecken das Smartphone nicht auf.
3. **Keine Rückmeldung:** Das System weiß nicht, ob die Nachricht registriert wurde.

---

## 2. Wie Android Audio-Streams aufgebaut sind

Android trennt die Lautstärkeregelung in separate Streams:
* `STREAM_RING` (Telefonklingeln)
* `STREAM_NOTIFICATION` (Benachrichtigungstöne von Apps)
* `STREAM_MUSIC` (Medienwiedergabe)
* `STREAM_ALARM` (Wecker und Notfallalarme)

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Android Audio Subsystem                       │
├───────────────────┬───────────────────┬────────────────────────────────┤
│    STREAM_RING    │STREAM_NOTIFICATION│          STREAM_ALARM          │
│   (Anrufe)        │(WhatsApp/Telegram)│     (Wecker / Notfall-Push)    │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ Bei DND oft stumm │ Bei DND IMMER     │ Bricht DND standardmäßig       │
│                   │ stumm             │ IMMER durch!                   │
└───────────────────┴───────────────────┴────────────────────────────────┘
```

> **Wichtigste Erkenntnis:**
> Um den "Nicht stören"-Modus zuverlässig zu durchbrechen, muss der Klingelton über die Audio-Attribute `USAGE_ALARM` bzw. den `STREAM_ALARM` abgespielt werden. In den Android-Systemeinstellungen ist der Wecker-Stream per Default so konfiguriert, dass er DND ignoriert.

---

## 3. Benachrichtigungskanäle (Notification Channels ab Android 8)

Seit Android 8.0 muss jede Benachrichtigung einem `NotificationChannel` zugeordnet sein. Ein Notfall-Kanal für eine Türklingel erfordert:

* **Wichtigkeit:** `NotificationManager.IMPORTANCE_HIGH` oder `IMPORTANCE_MAX` (führt dazu, dass die Benachrichtigung als "Heads-Up"-Banner aufpoppt und Ton abspielt).
* **DND-Bypass:** `channel.setBypassDnd(true)` (erlaubt dem Kanal, Nicht-Stören-Filter zu überschreiben).
* **Audio-Attributierung:**
  ```java
  AudioAttributes audioAttributes = new AudioAttributes.Builder()
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .setUsage(AudioAttributes.USAGE_ALARM)
      .build();
  channel.setSound(alarmSoundUri, audioAttributes);
  ```

---

## 4. Vollbild-Anzeige bei gesperrtem Bildschirm (`USE_FULL_SCREEN_INTENT`)

Damit sich der Klingelton wie ein echter **Telefonanruf** anfühlt, reicht ein normales Benachrichtigungsbanner oft nicht aus.

Android bietet dafür den Mechanismus des **Full-Screen Intents**:
1. Wenn das Gerät **gesperrt** ist (`Keyguard` aktiv), startet Android anstelle des Banners sofort die verknüpfte Activity im Vollbild.
2. Der Bildschirm schaltet sich automatisch an (`FLAG_TURN_SCREEN_ON`, `FLAG_KEEP_SCREEN_ON`).
3. Der Nutzer sieht sofort:
   * Großer Text: "Es klingelt an der Haustür!"
   * Einen Schieberegler oder Button zum Quittieren/Stummschalten.
   * Optional: Direkt das Live-Bild einer Überwachungskamera.

---

## 5. Das Wiederholungs- & Quittierungskonzept (Retry & Ack)

Ein Klingelton sollte nicht nach 5 Sekunden aufhören, wenn niemand reagiert. Es gibt zwei Umsetzungsarten:

### Methode A: Lokale Schleife (App-seitig)
Die App startet beim Eintreffen des Signals einen `MediaPlayer` mit `setLooping(true)`. Der Ton läuft ununterbrochen, bis der Nutzer die App öffnet und auf "Stumm" drückt.

### Methode B: Wiederholungsschleife in ioBroker (Kostenloses ntfy-Prinzip)
1. ioBroker startet bei Tastendruck einen Wiederholungs-Timer (`setInterval`, z. B. alle 20 Sekunden) und sendet hochpriore ntfy-Alarme (`Priority: urgent`).
2. Das Smartphone klingelt laut mit dem Aktions-Button `[🔇 Stumm / Quittieren]`.
3. Tippt der Nutzer am Handy auf den Button, sendet das Handy im Hintergrund einen Webhook an ioBroker zurück (`simple-api`).
4. ioBroker stoppt den Timer und beendet das Klingeln.
5. **Vorteil:** Funktioniert komplett ohne teure Drittanbieter-Abonnements direkt aus ioBroker heraus.

