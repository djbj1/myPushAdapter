import { Capacitor, registerPlugin } from '@capacitor/core';

const DoorbellPlugin = registerPlugin('DoorbellPlugin');

const els = {
  callView: document.getElementById('call-view'),
  dashboardView: document.getElementById('dashboard-view'),
  callTitle: document.getElementById('call-title'),
  callMessage: document.getElementById('call-message'),
  callTime: document.getElementById('call-time'),
  callCameraBox: document.getElementById('call-camera-box'),
  callCameraImg: document.getElementById('call-camera-img'),
  btnOpenDoor: document.getElementById('btn-open-door'),
  btnAckAlarm: document.getElementById('btn-ack-alarm'),

  connectionPill: document.getElementById('connection-pill'),
  connectionText: document.getElementById('connection-text'),
  toggleService: document.getElementById('toggle-service'),
  btnTestNow: document.getElementById('btn-test-now'),
  btnTestDelayed: document.getElementById('btn-test-delayed'),
  testCountdown: document.getElementById('test-countdown'),

  tabLocal: document.getElementById('tab-local'),
  tabNtfy: document.getElementById('tab-ntfy'),
  panelLocal: document.getElementById('panel-local'),
  panelNtfy: document.getElementById('panel-ntfy'),

  inputHost: document.getElementById('input-host'),
  inputPort: document.getElementById('input-port'),
  checkOpenDoor: document.getElementById('check-open-door'),
  inputNtfyTopic: document.getElementById('input-ntfy-topic'),
  inputCameraUrl: document.getElementById('input-camera-url'),

  btnSaveConfig: document.getElementById('btn-save-config'),
  btnCheckConnection: document.getElementById('btn-check-connection'),
  saveFeedback: document.getElementById('save-feedback'),

  btnPermNotif: document.getElementById('btn-perm-notif'),
  btnPermBattery: document.getElementById('btn-perm-battery'),
  btnPermDnd: document.getElementById('btn-perm-dnd'),

  eventLog: document.getElementById('event-log'),
  btnClearLog: document.getElementById('btn-clear-log')
};

const STORAGE_KEY = 'doorbell_app_config_v1';
let currentMode = 'local';
let cameraRefreshInterval = null;
let delayedTestInterval = null;

function getDefaultConfig() {
  return {
    mode: 'local',
    host: '192.168.178.10',
    port: 8095,
    enableOpenDoor: false,
    ntfyTopic: '',
    cameraUrl: '',
    serviceEnabled: true
  };
}

function loadLocalConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? { ...getDefaultConfig(), ...JSON.parse(raw) } : getDefaultConfig();
  } catch {
    return getDefaultConfig();
  }
}

function saveLocalConfig(cfg) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(cfg));
}

function buildUrls(cfg) {
  if (cfg.mode === 'ntfy') {
    const topic = (cfg.ntfyTopic || '').trim();
    return {
      sseUrl: topic ? `https://ntfy.sh/${encodeURIComponent(topic)}/sse` : '',
      ackUrl: '',
      openDoorUrl: ''
    };
  }
  const host = (cfg.host || '').trim();
  const port = Number(cfg.port) || 8095;
  const base = host ? `http://${host}:${port}` : '';
  return {
    sseUrl: base ? `${base}/events` : '',
    ackUrl: base ? `${base}/ack` : '',
    openDoorUrl: base && cfg.enableOpenDoor ? `${base}/open-door` : ''
  };
}

function addLog(text) {
  const empty = els.eventLog.querySelector('.log-empty');
  if (empty) empty.remove();

  const li = document.createElement('li');
  const now = new Date().toLocaleTimeString('de-DE');
  li.innerHTML = `<span>${escapeHtml(text)}</span><span class="time">${now}</span>`;
  els.eventLog.prepend(li);

  while (els.eventLog.children.length > 25) {
    els.eventLog.lastElementChild.remove();
  }
}

function escapeHtml(str) {
  return String(str)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function setConnectionUi(state) {
  els.connectionPill.classList.remove('pill-online', 'pill-connecting', 'pill-offline');
  if (state === 'connected') {
    els.connectionPill.classList.add('pill-online');
    els.connectionText.textContent = 'Verbunden';
  } else if (state === 'connecting') {
    els.connectionPill.classList.add('pill-connecting');
    els.connectionText.textContent = 'Verbinde...';
  } else {
    els.connectionPill.classList.add('pill-offline');
    els.connectionText.textContent = 'Getrennt';
  }
}

function switchMode(mode) {
  currentMode = mode === 'ntfy' ? 'ntfy' : 'local';
  els.tabLocal.classList.toggle('active', currentMode === 'local');
  els.tabNtfy.classList.toggle('active', currentMode === 'ntfy');
  els.panelLocal.classList.toggle('hidden', currentMode !== 'local');
  els.panelNtfy.classList.toggle('hidden', currentMode !== 'ntfy');
}

function showCallScreen(data = {}) {
  const cfg = loadLocalConfig();
  const urls = buildUrls(cfg);

  els.callTitle.textContent = data.title || 'Haustürklingel';
  els.callMessage.textContent = data.message || 'Es klingelt an der Haustür!';
  els.callTime.textContent = new Date().toLocaleTimeString('de-DE');

  const camUrl = data.cameraUrl || cfg.cameraUrl;
  if (camUrl) {
    els.callCameraBox.classList.remove('hidden');
    const updateCam = () => {
      const sep = camUrl.includes('?') ? '&' : '?';
      els.callCameraImg.src = `${camUrl}${sep}_t=${Date.now()}`;
    };
    updateCam();
    clearInterval(cameraRefreshInterval);
    cameraRefreshInterval = setInterval(updateCam, 2000);
  } else {
    els.callCameraBox.classList.add('hidden');
  }

  els.btnOpenDoor.classList.toggle('hidden', !urls.openDoorUrl);
  els.callView.classList.remove('hidden');
}

function hideCallScreen() {
  clearInterval(cameraRefreshInterval);
  cameraRefreshInterval = null;
  els.callView.classList.add('hidden');
}

function readFormIntoConfig() {
  return {
    mode: currentMode,
    host: els.inputHost.value.trim(),
    port: Number(els.inputPort.value) || 8095,
    enableOpenDoor: els.checkOpenDoor.checked,
    ntfyTopic: els.inputNtfyTopic.value.trim(),
    cameraUrl: els.inputCameraUrl.value.trim(),
    serviceEnabled: els.toggleService.checked
  };
}

function populateForm(cfg) {
  switchMode(cfg.mode);
  els.inputHost.value = cfg.host || '';
  els.inputPort.value = cfg.port || 8095;
  els.checkOpenDoor.checked = Boolean(cfg.enableOpenDoor);
  els.inputNtfyTopic.value = cfg.ntfyTopic || '';
  els.inputCameraUrl.value = cfg.cameraUrl || '';
  els.toggleService.checked = cfg.serviceEnabled !== false;
}

async function syncNativeConfig(cfg) {
  const urls = buildUrls(cfg);
  if (!Capacitor.isNativePlatform()) {
    addLog(`Browser-Vorschau: SSE Ziel = ${urls.sseUrl || '(leer)'}`);
    return;
  }

  await DoorbellPlugin.saveConfig({
    sseUrl: urls.sseUrl,
    ackUrl: urls.ackUrl,
    openDoorUrl: urls.openDoorUrl,
    cameraUrl: cfg.cameraUrl || '',
    serviceEnabled: cfg.serviceEnabled
  });
}

async function refreshNativeStatus() {
  if (!Capacitor.isNativePlatform()) return;
  try {
    const status = await DoorbellPlugin.getStatus();
    setConnectionUi(status.connectionState || 'disconnected');
    if (status.isAlarmActive) {
      showCallScreen({
        title: status.alarmTitle,
        message: status.alarmMessage,
        cameraUrl: status.alarmCameraUrl
      });
    } else {
      hideCallScreen();
    }
  } catch (err) {
    console.warn('Status error:', err);
  }
}

async function initApp() {
  const cfg = loadLocalConfig();
  populateForm(cfg);

  els.tabLocal.addEventListener('click', () => switchMode('local'));
  els.tabNtfy.addEventListener('click', () => switchMode('ntfy'));

  els.btnSaveConfig.addEventListener('click', async () => {
    const nextCfg = readFormIntoConfig();
    saveLocalConfig(nextCfg);
    await syncNativeConfig(nextCfg);
    els.saveFeedback.textContent = '✅ Gespeichert und Wächter neu verbunden.';
    els.saveFeedback.classList.remove('hidden');
    setTimeout(() => els.saveFeedback.classList.add('hidden'), 3500);
    addLog('Konfiguration gespeichert.');
    setTimeout(refreshNativeStatus, 700);
  });

  els.toggleService.addEventListener('change', async () => {
    const nextCfg = readFormIntoConfig();
    saveLocalConfig(nextCfg);
    await syncNativeConfig(nextCfg);
    addLog(nextCfg.serviceEnabled ? 'Wächter-Dienst aktiviert.' : 'Wächter-Dienst gestoppt.');
    setTimeout(refreshNativeStatus, 500);
  });

  els.btnTestNow.addEventListener('click', async () => {
    addLog('Sofort-Testalarm ausgelöst.');
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.triggerTestAlarm();
    }
    showCallScreen({ title: 'Test-Klingel', message: 'Testalarm (Audio USAGE_ALARM)' });
  });

  els.btnTestDelayed.addEventListener('click', () => {
    clearInterval(delayedTestInterval);
    let sec = 5;
    els.testCountdown.textContent = `⏳ Alarm startet in ${sec} Sekunden — Bildschirm jetzt sperren!`;
    els.testCountdown.classList.remove('hidden');

    delayedTestInterval = setInterval(async () => {
      sec -= 1;
      if (sec <= 0) {
        clearInterval(delayedTestInterval);
        els.testCountdown.classList.add('hidden');
        addLog('Verzögerter Testalarm ausgelöst.');
        if (Capacitor.isNativePlatform()) {
          await DoorbellPlugin.triggerTestAlarm();
        }
        showCallScreen({ title: 'Test-Klingel', message: 'Aufweck-Test über Sperrbildschirm' });
      } else {
        els.testCountdown.textContent = `⏳ Alarm startet in ${sec} Sekunden — Bildschirm jetzt sperren!`;
      }
    }, 1000);
  });

  els.btnAckAlarm.addEventListener('click', async () => {
    hideCallScreen();
    addLog('Alarm quittiert / stummgeschaltet.');
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.stopActiveAlarm({ sendAck: true });
    } else {
      const urls = buildUrls(loadLocalConfig());
      if (urls.ackUrl) fetch(urls.ackUrl).catch(() => {});
    }
  });

  els.btnOpenDoor.addEventListener('click', async () => {
    hideCallScreen();
    addLog('Türöffner-Befehl gesendet.');
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.triggerOpenDoor();
    } else {
      const urls = buildUrls(loadLocalConfig());
      if (urls.openDoorUrl) fetch(urls.openDoorUrl).catch(() => {});
    }
  });

  els.btnCheckConnection.addEventListener('click', async () => {
    const urls = buildUrls(readFormIntoConfig());
    if (!urls.sseUrl) {
      addLog('Keine URL eingetragen.');
      return;
    }
    try {
      const checkUrl = currentMode === 'local'
        ? urls.sseUrl.replace(/\/events$/, '/status')
        : urls.sseUrl.replace(/\/sse$/, '/json?poll=1');
      const res = await fetch(checkUrl);
      addLog(res.ok ? `Verbindung OK (HTTP ${res.status})` : `Server antwortet mit HTTP ${res.status}`);
    } catch (e) {
      addLog(`Verbindung fehlgeschlagen: ${e.message}`);
    }
  });

  els.btnPermNotif.addEventListener('click', async () => {
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.requestPermissions();
      addLog('Benachrichtigungsrechte angefragt.');
    }
  });

  els.btnPermBattery.addEventListener('click', async () => {
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.openBatteryOptimizationSettings();
    }
  });

  els.btnPermDnd.addEventListener('click', async () => {
    if (Capacitor.isNativePlatform()) {
      await DoorbellPlugin.openNotificationPolicySettings();
    }
  });

  els.btnClearLog.addEventListener('click', () => {
    els.eventLog.innerHTML = '<li class="log-empty">Noch keine Ereignisse aufgezeichnet.</li>';
  });

  if (Capacitor.isNativePlatform()) {
    await syncNativeConfig(cfg);

    DoorbellPlugin.addListener('connectionStateChanged', (ev) => {
      setConnectionUi(ev.state);
      addLog(`Verbindungsstatus: ${ev.state}`);
    });

    DoorbellPlugin.addListener('alarmStateChanged', (ev) => {
      if (ev.active) {
        addLog(`Alarm aktiv: ${ev.title || 'Türklingel'}`);
        showCallScreen(ev);
      } else {
        hideCallScreen();
      }
    });

    await refreshNativeStatus();
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        refreshNativeStatus();
      }
    });
  } else {
    addLog('Web-Vorschau aktiv (Native Android-Dienste nur in APK).');
  }
}

initApp();

