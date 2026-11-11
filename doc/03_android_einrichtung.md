# 03. Android Konfigurations-Leitfaden (Kostenlose Einrichtung)

Damit Benachrichtigungen von **ntfy** oder deiner **Capacitor-App** auch nachts im Tiefschlaf und bei aktivem "Nicht stören"-Modus (DND) laut klingeln, müssen diese Einstellungen einmalig auf deinem Android-Handy gesetzt werden.

---

## 1. ntfy Android-App einrichten (100 % Kostenlos)

1. **App installieren:**
   * Aus dem [Google Play Store](https://play.google.com/store/apps/details?id=io.heckel.ntfy) oder werbefrei über [F-Droid](https://f-droid.org/packages/io.heckel.ntfy/).
2. **Kanal abonnieren:**
   * Tippe auf das `+`-Symbol und trage dein geheimes Topic ein (z. B. `klingel_privat_98a7bc6d5e`).
3. **"Nicht stören"-Modus umgehen (DND-Bypass):**
   * Tippe in ntfy oben rechts auf das **Zahnrad (Einstellungen)** -> **Benachrichtigungen**.
   * Aktiviere die Option **"Nicht stören umgehen"** bzw. **"Do Not Disturb Override"**.
   * *Hinweis:* Falls Android dich auffordert, ntfy Zugriff auf die "Nicht stören"-Richtlinie zu gewähren, bestätige dies mit **Zulassen**.
4. **Alarmton & Lautstärke:**
   * Wähle unter *Benachrichtigungston* einen lauten, durchdringenden Ton (z. B. Sirene oder Alarm).

---

## 2. Systemweite Android-Einstellungen (Wichtig gegen Verzögerungen!)

Android versucht im Standby-Modus Akku zu sparen und verzögert Push-Nachrichten gerne, wenn das Display länger aus ist (*Doze Mode*).

### A. Akku-Optimierung für die App ausschalten
1. Öffne die Android-**Einstellungen** -> **Apps** -> Wähle **ntfy** (oder deine eigene App).
2. Tippe auf **Akku** (oder Akkunutzung).
3. Wähle **"Nicht eingeschränkt"** bzw. **"Keine Beschränkungen"**.

### B. Spezifische Einstellungen je nach Handy-Hersteller:

#### Samsung (One UI):
* Einstellungen -> Akku -> **Grenzen der Hintergrundnutzung**:
  * ntfy zu **"Apps, die nie im Standby sind"** hinzufügen.
* Einstellungen -> Benachrichtigungen -> **Nicht stören**:
  * Unter *App-Benachrichtigungen zulassen* -> **ntfy** hinzufügen.

#### Xiaomi / Redmi / POCO (MIUI / HyperOS):
* Einstellungen -> Apps -> Rechte -> **Autostart**: Für ntfy **aktivieren**.
* In den App-Details von ntfy: **Energiesparmodus** auf **"Keine Beschränkungen"** stellen.

#### Google Pixel:
* Einstellungen -> Apps -> ntfy -> Benachrichtigungen -> **Alle Benachrichtigungen zulassen**.
* Im Pixel-Standard-DND sind Alarme und Apps mit DND-Freigabe automatisch aktiv.

---

## 3. Funktions-Test

1. Aktiviere auf deinem Handy den **"Nicht stören"-Modus** und schalte den Ton komplett stumm.
2. Schalte den **Bildschirm aus** und lege das Handy für 3 Minuten zur Seite.
3. Löse in ioBroker das Skript aus.
4. **Ergebnis:**
   * Das Display schaltet sich ein bzw. das Banner erscheint.
   * Der Alarmton ertönt trotz DND und Lautlos-Profil.
   * Der Alarm wiederholt sich alle 20 Sekunden, bis du auf **[🔇 Stumm / Quittieren]** tippst.
