// Самопроверка в настоящем Electron на выдуманном demo.schools.by: вход → приложение → главная, дневник,
// статистика, новые отметки после следующего захода. Скриншоты — в RS_SHOT_DIR (или во временную папку).
// Запуск: npm run selftest
const fs = require('fs');
const os = require('os');
const path = require('path');

module.exports = async function selftest({ ses, site, createWindow }) {
  const shotDir = process.env.RS_SHOT_DIR || os.tmpdir();
  const checks = {};
  const check = (name, cond) => { checks[name] = !!cond; };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  const win = createWindow(ses);
  const js = (code) => win.webContents.executeJavaScript(code);
  const waitFor = async (code, ms = 15000) => {
    const end = Date.now() + ms;
    while (Date.now() < end) {
      const v = await js(code).catch(() => null);
      if (v) return v;
      await sleep(150);
    }
    return null;
  };
  const shot = async (name) => {
    win.webContents.invalidate();
    await sleep(700);
    const img = await win.webContents.capturePage(undefined, { stayHidden: true });
    const file = path.join(shotDir, `rs-${name}.png`);
    fs.writeFileSync(file, img.toPNG());
    return file;
  };
  const ready = `document.querySelector('#title') && !document.querySelector('.skel') && !document.querySelector('#refresh.spin')`;

  try {
    check('страница входа', await waitFor(`location.pathname === '/login' && !!document.getElementById('go')`));
    await js(`document.getElementById('go').click()`);
    check('после входа открылось приложение', await waitFor(`location.pathname === '/__rs/index.html' && !!window.RS?.app`));
    check('ученик найден', await waitFor(`document.getElementById('who').textContent === 'Тестов Тест'`));
    check('главная загрузилась', await waitFor(ready));
    const home = await js(`[...document.querySelectorAll('.card-h h2')].map((e) => e.textContent)`);
    check('блоки главной', JSON.stringify(home.slice(0, 2)) === JSON.stringify(['Новые отметки', 'Сегодня']) && /^ДЗ /.test(home[2]));
    check('первый заход: подсказка вместо новых', await js(`document.querySelector('.card .empty').textContent.includes('после этого захода')`));
    check('запросы недели с X-Requested-With', site.requests.some((p) => p.includes('/dnevnik/quarter/')));
    await shot('home-first');

    await js(`document.querySelector('.tabs button[data-tab=diary]').click()`);
    check('дневник', await waitFor(`${ready} && document.querySelectorAll('.day-h').length >= 5`));
    await shot('diary');
    await js(`document.querySelector('[data-act=week][data-d="-7"]').click()`);
    check('прошлая неделя', await waitFor(`${ready} && document.querySelector('.range small').textContent === 'прошлая неделя' && document.querySelectorAll('.day-h').length >= 5`));
    await js(`document.querySelector('[data-act=week][data-d="7"]').click()`);
    await js(`document.querySelector('[data-act=week][data-d="7"]').click()`);
    check('каникулы', await waitFor(`${ready} && document.querySelector('#view').textContent.includes('каникулы')`));

    await js(`document.querySelector('.tabs button[data-tab=stats]').click()`);
    check('статистика: обзор', await waitFor(`!RS.app.state.statsLoading && document.querySelectorAll('.scard').length > 10 && document.querySelectorAll('.kpi').length === 5`, 60000));
    check('статистика: все четверти', await js(`RS.app.state.stats.quarters.length === 4 && document.querySelector('.sc-qs').children.length === 4`));
    await shot('stats-overview');
    await js(`document.querySelector('.scard[data-key]').click()`);
    check('статистика: предмет', await waitFor(`document.querySelector('.subj-pick') && document.querySelectorAll('.chart .dot').length > 3`));
    await js(`(() => { const i = document.querySelector('[data-act=whatif]'); i.value = '10 10'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    check('статистика: а что если', await js(`/среднее \\d/.test(document.querySelector('.whatif-out').textContent)`));
    await shot('stats-subject');
    await js(`document.querySelector('[data-act=stab][data-key=dynamics]').click()`);
    check('статистика: динамика', await waitFor(`document.querySelectorAll('.tbl').length === 2 && document.querySelector('.hbars')`));
    await shot('stats-dynamics');
    check('назад из раздела статистики — в обзор', await js(`RS.app.back() && RS.app.state.tab === 'stats' && RS.app.state.statsTab === 'overview'`));

    // Следующий заход через пару часов: учитель поставил и исправил отметки.
    site.bump();
    // state.visit = null — чтобы при перезагрузке страница не записала «был активен только что».
    await js(`(() => { RS.app.state.visit = null; const k = 'rs:1234567:visit'; const v = JSON.parse(localStorage.getItem(k)); v.lastActive -= 3 * 3600e3; v.seen.at -= 3 * 3600e3; localStorage.setItem(k, JSON.stringify(v)); location.reload(); })()`);
    await sleep(500);
    check('новые отметки после следующего захода', await waitFor(`${ready} && document.querySelector('.card .count')?.textContent === '2'`));
    await shot('home-new');
    check('новая отметка попала в статистику', await js(`(() => {
      const t = RS.today(), fresh = RS.newMarks(RS.app.state.diary.allLessons(), RS.app.state.visit.baseline, t);
      return fresh.every((f) => RS.app.state.statsData.lessons.some((l) => RS.lessonKey(l) === RS.lessonKey(f) && RS.markStr(l) === RS.markStr(f)));
    })()`));

    await js(`document.getElementById('settings-btn').click()`);
    check('настройки', await waitFor(`document.getElementById('title').textContent === 'Настройки'`));
    await shot('settings');
    check('назад из настроек', await js(`RS.app.back() && RS.app.state.tab === 'home'`));
  } catch (e) {
    checks[`исключение: ${e.message}`] = false;
  }

  const ok = Object.values(checks).every(Boolean);
  for (const [k, v] of Object.entries(checks)) console.log(`${v ? '✓' : '✗'} ${k}`);
  console.log(ok ? 'SELFTEST OK' : 'SELFTEST FAILED', '— скриншоты в', shotDir);
  return ok;
};
