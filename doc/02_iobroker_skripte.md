# 02. ioBroker JavaScript Integrationen (100 % Kostenlos)

Diese Skripte können direkt im ioBroker-Adapter **JavaScript (Script Engine)** unter `javascript.0` als neues JavaScript angelegt werden. Es fallen **keine Kosten, keine Abos und keine App-Gebühren** an!

---

## 🛡️ Wichtiger Schutz: Entprellung (Debounce)
Wenn jemand mehrmals schnell hintereinander auf die Klingel drückt, verhindert der integrierte Cooldown (z. B. 30 Sekunden) doppelte Alarmauslösungen.

---

## Skript 1: ntfy.sh mit Dauerschleife & Quittierung (Empfohlen)

Dieses Skript bildet die Funktionsweise von teuren Alarm-Diensten **zu 100 % kostenlos** nach:
* **Bricht "Nicht stören" (DND)** auf Android über die Priorität `urgent`.
* **Klingelt alle 20 Sekunden erneut**, bis du reagierst.
* Sendet einen **Aktions-Button [🔇 Stumm / Quittieren]** direkt auf das Benachrichtigungsbanner.
* Sobald du darauf tippst, sendet das Handy im Hintergrund einen Webhook an ioBroker und stoppt das Klingeln sofort.

### Vorbereitung in ioBroker:
Stelle sicher, dass der Adapter **`simple-api`** (oder `web`) in ioBroker installiert und aktiv ist (Standard-Port: `8087`), damit dein Smartphone den Quittierungs-Befehl empfangen kann.

### Das Skript:
```javascript
// =========================================================================
// ioBroker JavaScript: Kostenlose Notfall-Dauerklingel via ntfy.sh
// =========================================================================
const https = require("https");

// 1. KONFIGURATION
const SENSOR_ID       = "deconz.0.Sensors.12.button"; // <- HIER deinen Klingel-Datenpunkt eintragen
const NTFY_TOPIC      = "klingel_privat_98a7bc6d5e";   // <- Wähle dein geheimes Topic!
const IOBROKER_IP     = "192.168.1.10";               // <- IP deines ioBroker-Servers
const SIMPLE_API_PORT = 8087;                         // Port des simple-api Adapters
const COOLDOWN_SEK    = 30;                           // Sperrzeit gegen Mehrfachklingeln
const REPEAT_SEK      = 20;                           // Alle 20 Sek. erneut klingeln
const MAX_DAUER_MIN   = 3;                            // Not-Aus nach 3 Minuten

// Optional: Link zu einem aktuellen Kamerabild (Snapshot)
const KAMERA_BILD_URL = ""; // z.B. "http://192.168.1.50/snapshot.jpg"

// 2. DATENPUNKT ZUM QUITTIEREN ANLEGEN
const ACK_DATENPUNKT  = "javascript.0.klingel.quittiert";
createState(ACK_DATENPUNKT, false, { type: "boolean", name: "Klingel quittiert" });

let repeatTimer = null;
let maxTimeoutTimer = null;
let lastTrigger = 0;

// Hilfsfunktion: ntfy POST-Request via nativem Node.js HTTPS
function sendNtfyMessage(topic, message, headers) {
    return new Promise((resolve, reject) => {
        const payload = Buffer.from(message, "utf-8");

        const reqHeaders = Object.assign({
            "Content-Type": "text/plain; charset=utf-8",
            "Content-Length": payload.length
        }, headers);

        const options = {
            hostname: "ntfy.sh",
            port: 443,
            path: "/" + encodeURIComponent(topic),
            method: "POST",
            headers: reqHeaders
        };

        const req = https.request(options, (res) => {
            let data = "";
            res.on("data", (chunk) => data += chunk);
            res.on("end", () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve(data);
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                }
            });
        });

        req.on("error", (err) => reject(err));
        req.write(payload);
        req.end();
    });
}

// 3. TRIGGER-ÜBERWACHUNG
on({ id: SENSOR_ID, change: "any", val: true }, function () {
    const now = Date.now();
    if (now - lastTrigger < COOLDOWN_SEK * 1000) {
        log(`[Klingel] Ignoriert: Cooldown aktiv (${COOLDOWN_SEK}s).`, "warn");
        return;
    }
    lastTrigger = now;

    log("[Klingel] Klingel betätigt! Starte Daueralarm via ntfy...", "info");

    // Status zurücksetzen & alte Timer stoppen
    setState(ACK_DATENPUNKT, false);
    if (repeatTimer) clearInterval(repeatTimer);
    if (maxTimeoutTimer) clearTimeout(maxTimeoutTimer);

    // Funktion zum Senden des Notfall-Pushes
    async function sendAlarmPush() {
        // Wurde zwischenzeitlich quittiert?
        if (getState(ACK_DATENPUNKT).val === true) {
            clearInterval(repeatTimer);
            return;
        }

        const headers = {
            "Title": "HAUSTUERKLINGEL",
            "Priority": "urgent",             // 5 / urgent = bricht DND auf Android
            "Tags": "rotating_light,bell",
            // Button sendet GET-Request direkt an ioBroker zum Quittieren:
            "Actions": `http, Stumm / Quittieren, http://${IOBROKER_IP}:${SIMPLE_API_PORT}/set/${ACK_DATENPUNKT}?value=true, clear=true`
        };

        if (KAMERA_BILD_URL) {
            headers["Attach"] = KAMERA_BILD_URL;
        }

        try {
            await sendNtfyMessage(NTFY_TOPIC, "Jemand steht an der Haustuer!", headers);
            log("[Klingel] ntfy Notfall-Alarm erfolgreich gesendet.", "info");
        } catch (err) {
            log("[Klingel] Fehler beim Senden an ntfy: " + err.message, "error");
        }
    }

    // 1. Sofort ersten Alarm senden
    sendAlarmPush();

    // 2. Alle X Sekunden wiederholen (Dauerklingeln)
    repeatTimer = setInterval(sendAlarmPush, REPEAT_SEK * 1000);

    // 3. Nach maximal X Minuten automatisch beenden
    maxTimeoutTimer = setTimeout(function () {
        clearInterval(repeatTimer);
        log(`[Klingel] Alarm nach ${MAX_DAUER_MIN} Minuten automatisch beendet.`, "info");
    }, MAX_DAUER_MIN * 60 * 1000);
});

// 4. QUITTIERUNG PER HANDY-BUTTON ABFANGEN
on({ id: ACK_DATENPUNKT, val: true }, function () {
    log("[Klingel] Am Smartphone stummgeschaltet/quittiert! Stoppe Wiederholung.", "info");
    if (repeatTimer) clearInterval(repeatTimer);
});
```