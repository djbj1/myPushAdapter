// =========================================================================
// ioBroker JavaScript: Echtzeit-SSE-Sender für deine Türklingel-App
// (Kein Adapter, keine Kosten, nutzt nur integriertes Node.js http/https)
// =========================================================================
const http = require("http");
const https = require("https");

// 1. KONFIGURATION
const SENSOR_ID = "shelly.0.SHSW-1#E8DB84D72A0E#1.Relay0.Input";  // "0_userdata.0.Klingel.Taste"; // <- DEIN Klingel-Datenpunkt
const DOOR_OPEN_ID = "";                           // Optional: z.B. "hm-rpc.0.XYZ.STATE" für Türöffner
const SERVER_PORT = 8095;                         // Port für deine Türklingel-App
const COOLDOWN_SEK = 15;                           // Schutz gegen Mehrfachdrücken

// Optional für Empfang unterwegs (4G/5G ohne VPN):
// Trage hier dasselbe geheime Topic wie in der App unter "Überall (ntfy.sh)" ein.
const NTFY_TOPIC = ""; // z.B. "klingel_privat_98a7bc6d5e"

// Optional: Kamera Snapshot URL (wird an App übermittelt)
const KAMERA_BILD_URL = ""; // z.B. "http://192.168.178.50/snapshot.jpg"

// =========================================================================
// INTERNE LOGIK (ab hier nichts mehr ändern)
// =========================================================================
const clients = new Set();
let lastTrigger = 0;

function broadcastEvent(eventType, data) {
    const payload = `event: ${eventType}\ndata: ${JSON.stringify({
        timestamp: Math.floor(Date.now() / 1000),
        ...data
    })}\n\n`;

    for (const res of clients) {
        try {
            res.write(payload);
        } catch (e) {
            clients.delete(res);
        }
    }
}

function sendNtfyOptional(title, message) {
    if (!NTFY_TOPIC) return;
    const headers = {
        "Title": title,
        "Priority": "urgent",
        "Content-Type": "text/plain; charset=utf-8",
        "Content-Length": Buffer.byteLength(message, "utf-8")
    };
    if (KAMERA_BILD_URL) {
        headers["Attach"] = KAMERA_BILD_URL;
    }

    const req = https.request({
        hostname: "ntfy.sh",
        port: 443,
        path: "/" + encodeURIComponent(NTFY_TOPIC),
        method: "POST",
        headers: headers
    });
    req.on("error", (err) => log(`[Klingel-SSE] ntfy Fehler: ${err.message}`, "warn"));
    req.write(message, "utf-8");
    req.end();
}

const server = http.createServer((req, res) => {
    // CORS erlauben (für App-Anfragen aus dem WebView)
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (req.method === "OPTIONS") {
        res.writeHead(204);
        res.end();
        return;
    }

    const reqPath = (req.url || "/").split("?")[0];

    if (reqPath === "/events") {
        res.writeHead(200, {
            "Content-Type": "text/event-stream; charset=utf-8",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive"
        });
        res.write(`event: connected\ndata: {"ok":true}\n\n`);
        clients.add(res);
        log(`[Klingel-SSE] Smartphone verbunden (${clients.size} aktiv).`, "info");

        req.on("close", () => {
            clients.delete(res);
            log(`[Klingel-SSE] Smartphone getrennt (${clients.size} aktiv).`, "info");
        });
        return;
    }

    if (reqPath === "/ack") {
        log("[Klingel-SSE] Alarm am Smartphone quittiert! Stoppe alle Geräte.", "info");
        broadcastEvent("doorbell_stop", { reason: "acknowledged" });
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true }));
        return;
    }

    if (reqPath === "/open-door") {
        log("[Klingel-SSE] Türöffner über Smartphone ausgelöst!", "info");
        if (DOOR_OPEN_ID) {
            setState(DOOR_OPEN_ID, true);
        }
        broadcastEvent("doorbell_stop", { reason: "door_opened" });
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true }));
        return;
    }

    if (reqPath === "/test") {
        log("[Klingel-SSE] Test-Alarm ausgelöst.", "info");
        broadcastEvent("doorbell_ring", {
            title: "Test-Klingel",
            message: "Testsignal von ioBroker!",
            cameraUrl: KAMERA_BILD_URL
        });
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true, clients: clients.size }));
        return;
    }

    if (reqPath === "/status") {
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true, clients: clients.size }));
        return;
    }

    res.writeHead(404);
    res.end("Not Found");
});

// Keepalive Heartbeat alle 25 Sekunden gegen Router-Timeouts
const pingInterval = setInterval(() => {
    for (const res of clients) {
        try {
            res.write(": ping\n\n");
        } catch (e) {
            clients.delete(res);
        }
    }
}, 25000);

let isStopping = false;

server.on("error", (err) => {
    if (err.code === "EADDRINUSE" && !isStopping) {
        log(`[Klingel-SSE] Port ${SERVER_PORT} noch kurz belegt, neuer Versuch in 1,5s...`, "warn");
        setTimeout(() => {
            if (!isStopping) {
                server.listen(SERVER_PORT, "0.0.0.0");
            }
        }, 1500);
    } else {
        log(`[Klingel-SSE] Server-Fehler: ${err.message}`, "error");
    }
});

server.listen(SERVER_PORT, "0.0.0.0", () => {
    log(`[Klingel-SSE] Server gestartet auf Port ${SERVER_PORT}`, "info");
});

// Auf Klingelsignal reagieren
on({ id: SENSOR_ID, change: "any", val: true }, () => {
    const now = Date.now();
    if (now - lastTrigger < COOLDOWN_SEK * 1000) {
        log(`[Klingel-SSE] Cooldown aktiv (${COOLDOWN_SEK}s), ignoriert.`, "warn");
        return;
    }
    lastTrigger = now;

    if (clients.size === 0) {
        log("[Klingel-SSE] Klingel betätigt, aber 0 Smartphones lokal verbunden!", "warn");
    } else {
        log(`[Klingel-SSE] Klingel betätigt! Sende Alarm an ${clients.size} Smartphone(s)...`, "info");
    }

    broadcastEvent("doorbell_ring", {
        title: "Haustuerklingel",
        message: "Es klingelt an der Haustuer!",
        cameraUrl: KAMERA_BILD_URL
    });
    sendNtfyOptional("Haustuerklingel", "Es klingelt an der Haustuer!");
});

    // Sauber beenden beim Skript-Stopp / Speichern in ioBroker
    onStop((callback) => {
        isStopping = true;
        clearInterval(pingInterval);
        for (const res of clients) {
            try {
                res.destroy();
            } catch (e) { }
        }
    clients.clear();

    if (typeof server.closeAllConnections === "function") {
        server.closeAllConnections();
    }

    server.close(() => {
        if (typeof callback === "function") callback();
    });
}, 2000);
