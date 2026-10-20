// Оболочка для компьютера: то же, что делает Android-приложение.
//  - https://<школа>.schools.by/__rs/* отдаём из папки web/ — приложение работает на домене школы и читает
//    дневник с куками входа, как сам сайт;
//  - после входа на schools.by (страница /pupil/<id> или /parent/<id>) открываем приложение.
// Флаги: --demo — выдуманный сайт demo.schools.by вместо настоящего, --selftest — самопроверка со скриншотами.
const { app, BrowserWindow, net, session, shell } = require('electron');
const fs = require('fs');
const os = require('os');
const path = require('path');

const WEB = path.join(__dirname, '..', 'web');
const APP_PATH = '/__rs/';
const LOGIN_URL = 'https://schools.by/login';
const DEMO = process.argv.includes('--demo') || process.argv.includes('--selftest');
const SELFTEST = process.argv.includes('--selftest');

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.png': 'image/png' };

const isSchools = (u) => u.protocol === 'https:' && (u.hostname === 'schools.by' || u.hostname.endsWith('.schools.by'));
const isLanding = (u) => isSchools(u) && u.hostname !== 'schools.by' && /^\/(pupil|parent)\/\d+\/?$/.test(u.pathname);

function serveWeb(u) {
  const rel = decodeURIComponent(u.pathname.slice(APP_PATH.length)) || 'index.html';
  const file = path.normalize(path.join(WEB, rel));
  if (!file.startsWith(WEB + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    return new Response('not found', { status: 404 });
  }
  return new Response(fs.readFileSync(file), {
    headers: { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' },
  });
}

const settingsFile = () => path.join(app.getPath('userData'), 'settings.json');
const loadSettings = () => { try { return JSON.parse(fs.readFileSync(settingsFile(), 'utf8')); } catch { return {}; } };
const saveSettings = (s) => { try { fs.writeFileSync(settingsFile(), JSON.stringify(s)); } catch { /* ignore */ } };

// Запрос к настоящему сайту как есть, с куками сессии. Редиректы не проходим сами, а отдаём браузеру —
// иначе окно не узнает, куда сайт отправил после входа (net.fetch с redirect: 'manual' в Electron не работает).
const SKIP_HEADERS = new Set(['set-cookie', 'content-encoding', 'content-length', 'transfer-encoding', 'connection']);
async function passthrough(ses, req) {
  const body = req.body ? Buffer.from(await req.arrayBuffer()) : null;
  return new Promise((resolve) => {
    const r = net.request({
      method: req.method, url: req.url, session: ses, redirect: 'manual', useSessionCookies: true,
      bypassCustomProtocolHandlers: true,
    });
    for (const [k, v] of req.headers) { try { r.setHeader(k, v); } catch { /* запрещённый заголовок */ } }
    r.on('redirect', (status, method, url) => {
      r.abort();
      resolve(Response.redirect(url, [301, 302, 303, 307, 308].includes(status) ? status : 302));
    });
    r.on('response', (resp) => {
      const chunks = [];
      resp.on('data', (c) => chunks.push(c));
      resp.on('end', () => {
        const headers = new Headers();
        for (const [k, v] of Object.entries(resp.headers)) {
          if (!SKIP_HEADERS.has(k.toLowerCase())) for (const x of [].concat(v)) headers.append(k, x);
        }
        const empty = req.method === 'HEAD' || [204, 304].includes(resp.statusCode);
        resolve(new Response(empty ? null : Buffer.concat(chunks), { status: resp.statusCode, headers }));
      });
    });
    r.on('error', () => resolve(Response.error()));
    if (body) r.write(body);
    r.end();
  });
}

function setup(ses, site) {
  ses.setPermissionRequestHandler((wc, perm, cb) => cb(false));
  ses.protocol.handle('https', async (req) => {
    const u = new URL(req.url);
    if (isSchools(u) && u.hostname !== 'schools.by' && u.pathname.startsWith(APP_PATH)) return serveWeb(u);
    if (site) {
      // В демо всё schools.by — выдуманное (вход тоже), остальная сеть закрыта.
      if (u.hostname === 'schools.by' && u.pathname === '/login') return Response.redirect('https://demo.schools.by/login', 302);
      if (u.hostname !== 'demo.schools.by') return new Response('blocked in demo', { status: 403 });
      const r = site.handle(req.url);
      if (r.status >= 300 && r.status < 400) return Response.redirect(new URL(r.headers.location, req.url).href, r.status);
      return new Response(r.body, { status: r.status, headers: r.headers });
    }
    return passthrough(ses, req);
  });
}

function createWindow(ses, startUrl) {
  const settings = loadSettings();
  const win = new BrowserWindow({
    width: 430, height: 880, minWidth: 340, backgroundColor: '#14171d', title: 're:schools',
    autoHideMenuBar: true, show: !SELFTEST,
    webPreferences: { session: ses, contextIsolation: true, sandbox: true, backgroundThrottling: !SELFTEST, offscreen: SELFTEST },
  });
  let loginFlow = false;
  const onNav = (url) => {
    let u;
    try { u = new URL(url); } catch { return; }
    if (/\/login/.test(u.pathname)) loginFlow = true;
    if (isSchools(u) && u.pathname.startsWith(APP_PATH)) {
      settings.appUrl = `${u.origin}${APP_PATH}index.html`;
      saveSettings(settings);
    } else if (loginFlow && isLanding(u)) {
      // Вошли — открываем приложение на домене школы; оно само найдёт ученика по этой странице.
      loginFlow = false;
      win.loadURL(`${u.origin}${APP_PATH}index.html?from=${encodeURIComponent(u.pathname)}`);
    }
  };
  win.webContents.on('did-navigate', (e, url) => onNav(url));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    const u = new URL(url);
    if (!isSchools(u) && /^https?:$/.test(u.protocol)) { e.preventDefault(); shell.openExternal(url); }
  });
  const ua = win.webContents.getUserAgent().replace(/\s*Electron\/\S+/, '').replace(/\s*re-schools\/\S+/, '');
  win.webContents.setUserAgent(ua);
  win.loadURL(startUrl || (settings.appUrl && !DEMO ? settings.appUrl : LOGIN_URL));
  return win;
}

if (SELFTEST) {
  app.disableHardwareAcceleration();
  app.setPath('userData', fs.mkdtempSync(path.join(os.tmpdir(), 'rs-selftest-')));
}

app.whenReady().then(async () => {
  let site = null;
  if (DEMO) {
    const { createSite } = require('../test/fake-site');
    site = createSite({ loggedIn: false });
  }
  const ses = session.fromPartition(DEMO ? 'persist:re-schools-demo' : 'persist:re-schools');
  setup(ses, site);
  if (SELFTEST) {
    await ses.clearStorageData();
    const ok = await require('./selftest')({ ses, site, createWindow });
    app.exit(ok ? 0 : 1);
    return;
  }
  createWindow(ses);
});

app.on('window-all-closed', () => app.quit());
