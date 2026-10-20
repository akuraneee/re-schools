// Выдуманный schools.by для тестов и демо: та же разметка, что у настоящего сайта (см. docs/schools-by.md в schools++),
// включая скрытый блок .db_days с заниженными на 2 отметками и переходы между четвертями через каникулы.
// Ученик «Тестов Тест», ID 1234567, хост demo.schools.by. Никаких настоящих данных.
'use strict';

const PID = '1234567';
const HOST = 'https://demo.schools.by';

const pad = (n) => String(n).padStart(2, '0');
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parse = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (s, n) => { const d = parse(s); d.setDate(d.getDate() + n); return iso(d); };
const mondayOf = (s) => { const d = parse(s); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return iso(d); };

const SCHEDULE = [
  ['Матем.', 'Рус. яз.', 'Англ. яз.', 'Физика', 'Ист. Бел. в кон. всем. ист.', 'Физ. к. и зд.', 'Информ. час'],
  ['Бел. яз.', 'Матем.', 'Матем.', 'Химия', 'Биология', 'Англ. яз.', ''],
  ['Рус. лит.', 'Физика', 'Информ.', 'География', 'Обществов.', 'Физ. к. и зд.', ''],
  ['Англ. яз.', 'Матем.', 'Бел. лит.', 'Химия', 'Черчение', 'ДП/МП', ''],
  ['Рус. яз.', 'Физика', 'Матем.', 'Биология', 'Бел. яз.', '', ''],
  ['', '', '', '', '', '', ''],
];

const HW = {
  'Матем.': (n) => `§ ${n % 20 + 1}, № ${n % 40 + 10}.${n % 9 + 1}, ${n % 40 + 11}.${n % 7 + 2}`,
  'Рус. яз.': (n) => `Упр. ${n % 300 + 20}, выучить правило на с. ${n % 150 + 30}`,
  'Англ. яз.': (n) => `WB p. ${n % 60 + 5} ex. ${n % 6 + 1}\nУчить слова Unit ${n % 8 + 1}`,
  'Физика': (n) => `§ ${n % 25 + 1}, упр. ${n % 12 + 1} (1–3)`,
  'Химия': (n) => `§ ${n % 30 + 1}, задачи ${n % 5 + 1}–${n % 5 + 4}`,
  'Биология': (n) => `§ ${n % 30 + 1}, вопросы после параграфа`,
  'Рус. лит.': () => 'Читать «Отцы и дети», гл. 10–14\nhttps://example.org/reading-list',
  'Бел. яз.': (n) => `Практыкаванне ${n % 200 + 10}`,
};

function hash(str) {
  let h = 2166136261;
  for (const c of str) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); }
  h ^= h >>> 16; h = Math.imul(h, 0x85ebca6b); h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

function createSite({ today = iso(new Date()), loggedIn = true } = {}) {
  const t = parse(today);
  const Y = t.getMonth() >= 7 ? t.getFullYear() : t.getFullYear() - 1;
  const q1s = mondayOf(`${Y}-09-01`), q1e = mondayOf(`${Y}-10-24`);
  const quarters = [
    { id: '92', from: q1s, to: q1e },
    { id: '93', from: addDays(q1e, 14), to: mondayOf(`${Y}-12-24`) },
    { id: '94', from: mondayOf(`${Y + 1}-01-12`), to: mondayOf(`${Y + 1}-03-21`) },
    { id: '95', from: mondayOf(`${Y + 1}-03-30`), to: mondayOf(`${Y + 1}-05-30`) },
  ];
  const site = { today, version: 1, loggedIn, requests: [], quarters };

  const quarterOf = (monday) => quarters.find((q) => monday >= q.from && monday <= q.to) || null;
  const neighbor = (monday, dir) => {
    for (let d = addDays(monday, dir * 7), i = 0; i < 6; d = addDays(d, dir * 7), i++) if (quarterOf(d)) return d;
    return null;
  };

  // Отметка урока: null, 'н' или число. В версии 2 «учитель» добавляет и исправляет отметки задним числом.
  function lessonMark(date, num, subject) {
    if (!subject || date > site.today) return null;
    const r = hash(`${date}|${num}`);
    let mark = r < 0.04 ? 'н' : r < 0.42 ? 4 + Math.floor(hash(`${date}#${num}`) * 7) : null;
    if (site.version >= 2 && date < site.today && site.changed[`${date}|${num}`] !== undefined) mark = site.changed[`${date}|${num}`];
    return mark;
  }

  // Что поменяется во второй версии: новая отметка на прошлом учебном дне, исправленная — неделей раньше.
  site.changed = {};
  site.bump = function () {
    site.version = 2;
    const days = [];
    for (let d = addDays(site.today, -1); days.length < 6; d = addDays(d, -1)) {
      const wd = (parse(d).getDay() + 6) % 7;
      if (wd < 5 && quarterOf(mondayOf(d))) days.push(d);
    }
    const prev = days[0];
    // на прошлом учебном дне — новая 10 на первом уроке без отметки
    for (let n = 1; n <= 7; n++) {
      const s = SCHEDULE[(parse(prev).getDay() + 6) % 7][n - 1];
      if (s && lessonMark(prev, n, s) == null) { site.changed[`${prev}|${n}`] = 10; break; }
    }
    // около недели назад — исправленная отметка (первая числовая)
    let older = null;
    for (const day of days.slice(3)) {
      for (let n = 1; n <= 7 && !older; n++) {
        const m = lessonMark(day, n, SCHEDULE[(parse(day).getDay() + 6) % 7][n - 1]);
        if (typeof m === 'number') { site.changed[`${day}|${n}`] = m >= 10 ? 9 : m + 1; older = day; }
      }
      if (older) break;
    }
    return { prev, older };
  };

  function dayTable(date, hidden) {
    const wd = (parse(date).getDay() + 6) % 7;
    const [y, m, d] = date.split('-');
    const names = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
    const rows = SCHEDULE[wd].map((subject, i) => {
      const num = i + 1;
      const mark = lessonMark(date, num, subject);
      const shown = typeof mark === 'number' && hidden ? Math.max(1, mark - 2) : mark;
      const hwFn = HW[subject];
      const hw = subject && hwFn && date <= site.today && hash(`${date}%${num}`) < 0.8 ? hwFn(Math.floor(hash(date + subject) * 1000)) : '';
      return `<tr ${i % 2 ? '' : 'class="even"'}>
        <td class="lesson "><span>${num}.${subject ? `\n            ${subject}\n        ` : ''}</span></td>
        <td class="ht">
          <div class="ht-aside"><div class="ht-box"><span class="icon icon-text-width"></span><span class="hint"><p><b>Тема урока:</b></p><p>недоступно</p></span></div>
          <div class="ht-box"><span class="icon icon-info-sign"></span><div class="hint" style="width: 220px;"><p>Создано: недоступно 12</p></div></div></div>
          ${hw ? `<div class="ht-text-wrapper"><div class="ht-text">\n      ${hw.split('\n').join('\n      ')}\n  </div></div>` : ''}
        </td>
        <td class="mark"><div class="mark_box ">${shown != null ? `<strong>${shown}</strong>` : ''}</div></td>
      </tr>`;
    }).join('\n');
    return `<div class="db_day"><table id="db_table_${+d}.${+m}.${y.slice(2)}" class="db_table ">
      <thead><tr><th class="lesson">${names[wd]}, ${+d}</th><th class="ht">Домашнее задание</th><th class="mark">Отметка</th></tr></thead>
      <tbody>${rows}</tbody></table><script>AttachmentsDropdown.initAll("db_table_${+d}.${+m}.${y.slice(2)}");</script></div>`;
  }

  function weekHtml(monday, q) {
    const prev = neighbor(monday, -1), next = neighbor(monday, 1);
    const link = (w, cls) => (w ? `<a id="db_period_${cls}_link_${PID}_${q.id}_${monday}" class="${cls}" href="#" ${cls}_week_id="${w}" send_to="/pupil/${PID}/dnevnik/quarter/${quarterOf(w).id}/week/${w}">${cls === 'prev' ? '←' : '→'}</a>` : '');
    const days = (hidden, n) => [0, 1, 2, 3, 4, 5].slice(0, n).map((i) => dayTable(addDays(monday, i), hidden));
    const block = (hidden) => {
      const d = days(hidden, hidden ? 6 : 5);
      return `<div class="db_days clearfix"${hidden ? ' style="display: none;"' : ''}>
        <div class="db_days_column left">${d.slice(0, 3).join('')}</div>
        <div class="db_days_column right">${d.slice(3).join('')}</div></div>`;
    };
    return `<div class="db_under clearfix"><div class="db_absences"><p>Пропущенных уроков: <b>1</b></p></div></div>
      <div class="db_period"><p>${link(prev, 'prev')} ${monday} ${link(next, 'next')}<img class="db_period_loader"></p></div>
      ${block(true)}
      ${block(false)}
      <div class="db_notes"><div class="title_blue">Замечания на этой неделе</div><div class="list"><div class="mess_empty">нет</div></div></div>
      <script>var quarter_id = '${q.id}', weekID = '${monday}', weekUrl = '/pupil/${PID}/dnevnik/quarter/${q.id}/week/${monday}', pupil_id = '${PID}';</script>`;
  }

  const PROFILE = `<!doctype html><html><head><meta charset="utf-8"><title>Тестов Тест. Обучающийся 10 "А". Тестовая школа</title></head>
    <body style="font:14px Arial;margin:0"><div style="background:#2a6ebb;color:#fff;padding:14px 20px">schools.by (выдуманный)</div>
    <div style="padding:20px"><div class="title_box"><h1>Тестов Тест, <a href="/class/1">10 "А"</a> класс, ID ${PID}</h1></div>
    <ul><li><a href="#dnevnik" src="/pupil/${PID}/dnevnik">Дневник</a></li></ul></div></body></html>`;

  const LOGIN = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Вход</title></head>
    <body style="font:16px Arial;padding:24px"><h1>schools.by — вход (выдуманный)</h1>
    <form action="${HOST}/do-login" method="get"><input name="u" value="test"><input type="password" name="p" value="x">
    <button id="go">Войти</button></form></body></html>`;

  const res = (status, body, headers = {}) => ({ status, body, headers: { 'content-type': 'text/html; charset=utf-8', ...headers } });

  site.handle = function (url) {
    const u = new URL(url);
    site.requests.push(u.pathname);
    if (u.pathname === '/login') return res(200, LOGIN);
    if (u.pathname === '/do-login') { site.loggedIn = true; return res(302, '', { location: `${HOST}/pupil/${PID}` }); }
    if (!site.loggedIn) return res(302, '', { location: `${HOST}/login` });
    if (u.pathname === `/pupil/${PID}`) return res(200, PROFILE);
    if (u.pathname === `/pupil/${PID}/dnevnik`) {
      const cur = mondayOf(site.today);
      const w = quarterOf(cur) ? cur : neighbor(cur, -1) || neighbor(cur, 1);
      return res(200, weekHtml(w, quarterOf(w)));
    }
    const m = u.pathname.match(new RegExp(`^/pupil/${PID}/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})$`));
    if (m) {
      const q = quarterOf(m[2]);
      if (!q || q.id !== m[1]) return res(404, 'not found');
      return res(200, weekHtml(m[2], q));
    }
    return res(404, 'not found');
  };

  return site;
}

module.exports = { createSite, PID, HOST, mondayOf, addDays };
