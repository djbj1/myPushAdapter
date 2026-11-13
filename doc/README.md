# Dokumentation: Notfall-Push & Türklingel-Alarmierung für ioBroker & Android (100 % Kostenlos)

Dieses Verzeichnis enthält das vollständige technische und praktische Konzept zur Realisierung einer **kostenlosen Notfall-Push-Alarmierung** für eine Türklingel über **ioBroker** auf **Android-Smartphones**.

---

## 🎯 Ziel des Systems
Im Gegensatz zu Standard-Messengern (WhatsApp, Telegram):
1. **Bricht den "Nicht stören"-Modus (DND)** und Lautlos-Profile von Android.
2. **Spielt einen dauerhaften Alarmton im Loop ab**, bis der Nutzer aktiv am Handy quittiert.
3. **Verhält sich bei gesperrtem Bildschirm wie ein Wecker oder Anruf**, sodass die Türklingel garantiert nicht überhört wird.
4. **100 % Kostenlos:** Keine Abos, keine Einmalkäufe, keine Cloud-Kosten.

---

## 📚 Verzeichnisstruktur

| Dokument | Beschreibung |
| :--- | :--- |
| **[01_architektur_und_grundlagen.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/01_architektur_und_grundlagen.md)** | Technische Hintergründe: Wie Android DND umgeht, Audio-Streams (`USAGE_ALARM`) und Vollbild-Trigger funktionieren. |
| **[02_iobroker_skripte.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/02_iobroker_skripte.md)** | **Direkt einsatzbereite, kostenlose ioBroker JavaScripts** für ntfy.sh (Dauerschleife mit Quittierungs-Button) und Fritz!Box-Anruf. |
| **[03_android_einrichtung.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/03_android_einrichtung.md)** | Smartphone-Konfiguration: ntfy DND-Ausnahmen, Akku-Optimierung ausschalten (Samsung, Xiaomi, Pixel) gegen Push-Verzögerungen. |
| **[04_konzept_eigene_app_und_adapter.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/04_konzept_eigene_app_und_adapter.md)** | Entwickler-Konzept für eine komplett eigenständige Android-App (Kotlin) + ioBroker-Adapter. |
| **[05_capacitor_architektur_und_umsetzung.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/05_capacitor_architektur_und_umsetzung.md)** | **Capacitor + Node.js Leitfaden:** Eigene Web-App (HTML/CSS/JS) als Android-App mit Vollbild-Aufwecken, Dauerton & Türöffner-Button (0 € via FCM). |
| **[06_smartphone_anbindung_sse.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/06_smartphone_anbindung_sse.md)** | **Detaillierte Erklärung der Smartphone-Anbindung:** Wie SSE, 25s-Heartbeat, Reconnect, DND-Alarm, `/ack` & `/open-door` zwischen App und ioBroker zusammenspielen. |

---

## 🚀 Schnellempfehlung zur Umsetzung

* **Sofortige Lösung in 5 Minuten (Empfohlen):** Nutze **ntfy.sh** zusammen mit dem Skript aus **[02_iobroker_skripte.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/02_iobroker_skripte.md)**. Es sendet alle 20 Sekunden einen lauten Alarm mit dem Button `[🔇 Stumm / Quittieren]`, bis du reagierst.
* **Für echten Telefonanruf:** Nutze das Fritz!Box-Skript (erfordert eine Fritz!Box und die kostenlose App *Fritz!App Fon*).
* **Für eigene Vollbild-App mit Live-Kamera & Türöffner:** Baue die Capacitor-App nach Anleitung **[05_capacitor_architektur_und_umsetzung.md](file:///c:/Programmierung/CurrentProjects/node.js/myPushAdapter/doc/05_capacitor_architektur_und_umsetzung.md)**.
