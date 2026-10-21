// Интерфейс re:schools: «Главная» (новые отметки, отметки за сегодня, ДЗ на завтра), «Дневник» (по неделям),
// «Статистика» (средние, четвертные и годовые — из schools++), настройки.
(function (RS) {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const esc = RS.esc;
  const LOGIN_URL = 'https://schools.by/login';
  // Статистика за год перекачивается не чаще; свежие недели в неё подставляет дневник (RS.mergeWeeks).
  const STATS_TTL = 6 * 3600 * 1000;
  const STATS_TABS = [['overview', 'Обзор'], ['subject', 'Предмет'], ['dynamics', 'Динамика']];

  const WEEKDAYS_FULL = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  const WEEKDAYS_ACC = ['воскресенье', 'понедельник', 'вторник', 'среду', 'четверг', 'пятницу', 'субботу'];
  const WEEKDAYS_SHORT = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
  const MONTHS_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];

  const S = {
    tab: 'home', // home | diary | stats | settings
    prevTab: 'home',
    pupils: RS.store.get('pupils', []),
    pupil: null,
    diary: null,
    visit: null,
    settings: { hwMode: 'assigned', threshold: 0.5, bells: RS.DEFAULT_BELLS, ...RS.store.get('settings', {}) },
    loading: false,
    progress: null,
    error: null, // { kind: 'auth' | 'offline' | 'other', text }
    week: RS.mondayOf(RS.today()),
    weekLoading: false,
    needLogin: false,
    statsTab: 'overview', // overview | subject | dynamics
    subject: null,
    sort: { key: 'name', dir: 1 },
    statsData: null, // результат RS.crawl, хранится в <id>:stats
    stats: null, // RS.computeStats(statsData)
    periods: {}, // предмет → 'q' (за четверть) | 'h' (за полугодие); нет — по умолчанию (RS.defaultPeriod), хранится в <id>:periods
    statsLoading: false,
    statsPages: 0,
  };
  if (!Array.isArray(S.settings.bells) || !S.settings.bells.length) S.settings.bells = RS.DEFAULT_BELLS;

  // ---------- форматирование ----------

  const dayName = (d) => WEEKDAYS_FULL[RS.parseISO(d).getDay()];
  const dateLong = (d) => { const x = RS.parseISO(d); return `${x.getDate()} ${MONTHS_GEN[x.getMonth()]}`; };
  const dateShort = (d) => `${WEEKDAYS_SHORT[RS.parseISO(d).getDay()]}, ${RS.fmtDate(d)}`;
  const timeOf = (ts) => { const d = new Date(ts); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`; };

  function when(ts) {
    const d = RS.iso(new Date(ts)), t = RS.today();
    if (d === t) return `сегодня в ${timeOf(ts)}`;
    if (d === RS.addDays(t, -1)) return `вчера в ${timeOf(ts)}`;
    return `${dateLong(d)} в ${timeOf(ts)}`;
  }

  function relDay(target, today) {
    const n = RS.daysBetween(today, target);
    if (n === 1) return 'на завтра';
    if (n === 2) return 'на послезавтра';
    return `на ${WEEKDAYS_ACC[RS.parseISO(target).getDay()]}`;
  }

  const markBadge = (v, sm) => `<span class="mark${sm ? ' sm' : ''} l${RS.markLevel(v)}">${v}</span>`;
  function marksHtml(l, sm) {
    const parts = l.marks.map((v) => markBadge(v, sm));
    if (l.absent) parts.push(`<span class="mark${sm ? ' sm' : ''} l0">н</span>`);
    return parts.length ? `<div class="marks">${parts.join('')}</div>` : '';
  }

  function linkify(text) {
    return esc(text).replace(/\bhttps?:\/\/[^\s<]+[^\s<.,;:!?)»"']/g, (u) => `<a href="${u}" target="_blank" rel="noopener">${u}</a>`);
  }

  const subj = (s) => esc(RS.subjectTitle(s));

  // ---------- данные ----------

  const lessons = () => (S.diary ? S.diary.allLessons() : []);
  const hasData = () => !!(S.diary && Object.keys(S.diary.weeks).length);

  function saveVisit() {
    if (S.pupil) RS.store.set(`${S.pupil.id}:visit`, S.visit);
  }

  function selectPupil(p) {
    S.pupil = p;
    RS.store.set('pupil', p.id);
    S.diary = new RS.Diary(p);
    S.visit = RS.startVisit(RS.store.get(`${p.id}:visit`));
    saveVisit();
    S.statsData = RS.store.get(`${p.id}:stats`);
    S.periods = RS.store.get(`${p.id}:periods`, {});
    S.subject = null;
    recompute();
    S.week = RS.mondayOf(RS.today());
    render();
    refresh();
  }

  async function refresh() {
    if (!S.diary || S.loading) return;
    S.loading = true;
    S.error = null;
    S.progress = null;
    render();
    try {
      await S.diary.refresh(RS.today(), S.visit.baseline?.at, (done, total) => { S.progress = [done, total]; renderStatus(); });
      S.visit.seen = RS.snapshot(S.diary.weeks);
      S.visit.lastActive = Date.now();
      S.lastSync = Date.now();
      RS.store.set(`${S.pupil.id}:lastSync`, S.lastSync);
      saveVisit();
      mergeDiary();
    } catch (e) {
      S.error = errorOf(e);
    } finally {
      S.loading = false;
      S.progress = null;
      render();
    }
  }

  function errorOf(e) {
    if (e instanceof RS.AuthError) return { kind: 'auth', text: 'Вход на schools.by истёк.' };
    if (e instanceof RS.OfflineError || !navigator.onLine) return { kind: 'offline', text: 'Нет связи с schools.by — показаны сохранённые данные.' };
    console.error(e);
    return { kind: 'other', text: `Не удалось загрузить дневник: ${e.message || e}` };
  }

  async function openWeek(monday) {
    S.week = monday;
    render();
    if (!S.diary || S.diary.expanded.has(monday)) return;
    S.weekLoading = true;
    render();
    try {
      await S.diary.loadWeek(monday);
      S.visit.seen = RS.snapshot(S.diary.weeks, S.visit.seen?.at || Date.now());
      saveVisit();
      mergeDiary();
    } catch (e) {
      S.error = errorOf(e);
    } finally {
      S.weekLoading = false;
      if (S.week === monday) render();
    }
  }

  function recompute() {
    S.stats = S.statsData ? RS.computeStats(S.statsData, { threshold: S.settings.threshold, today: RS.today(), periods: S.periods }) : null;
  }

  function setStatsData(d) {
    S.statsData = d;
    if (S.pupil) RS.store.set(`${S.pupil.id}:stats`, d);
    recompute();
  }

  // Недели, перекачанные дневником в этой сессии, — в статистику.
  function mergeDiary() {
    if (!S.statsData || !S.diary) return;
    const d = RS.mergeWeeks(S.statsData, S.diary.weeks, S.diary.links, [...S.diary.expanded]);
    if (d !== S.statsData) setStatsData(d);
  }

  const statsStale = () => !S.statsData || Date.now() - S.statsData.fetchedAt > STATS_TTL;

  // full — заново весь год; иначе только текущая и следующие четверти (RS.quartersToRefresh).
  async function refreshStats(full) {
    if (!S.pupil || S.statsLoading) return;
    const pupil = S.pupil;
    S.statsLoading = true;
    S.statsPages = 0;
    S.error = null;
    render();
    try {
      const prev = full ? null : S.statsData;
      const res = await RS.crawl(pupil, {
        previous: prev,
        onlyQuarters: prev ? RS.quartersToRefresh(prev) : null,
        onProgress: (ctx) => {
          S.statsPages = ctx.pages;
          renderStatus();
          const el = document.getElementById('stats-progress');
          if (el) el.textContent = statsProgressText();
        },
      });
      if (S.pupil === pupil) setStatsData(res);
    } catch (e) {
      S.error = errorOf(e);
    } finally {
      S.statsLoading = false;
      render();
    }
  }

  // Оболочка (Android/Electron) после входа открывает приложение с ?from=/pupil/<id> — оттуда узнаём ученика.
  async function pupilsFromPage(path) {
    const page = await RS.fetchPage(new URL(path, location.origin).href);
    const pupils = RS.findPupils(page.doc, new URL(page.url));
    if (pupils.length) {
      S.pupils = pupils;
      RS.store.set('pupils', pupils);
    }
    return pupils;
  }

  // ---------- отрисовка ----------

  function render() {
    nowKey = nowKeyOf();
    const app = $('#app');
    app.dataset.tab = S.tab;
    const titles = { home: 'Главная', diary: 'Дневник', stats: 'Статистика', settings: 'Настройки' };
    $('#title').textContent = S.needLogin ? 're:schools' : titles[S.tab];
    $('#who').textContent = S.pupil ? S.pupil.name : '';
    $('#back').classList.toggle('hidden', S.tab !== 'settings');
    $('#tabs').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#refresh').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#settings-btn').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#refresh').classList.toggle('spin', S.loading || S.weekLoading || (S.tab === 'stats' && S.statsLoading));
    for (const b of document.querySelectorAll('.tabs button')) b.classList.toggle('on', b.dataset.tab === S.tab);
    renderStatus();

    const view = $('#view');
    if (S.needLogin) view.innerHTML = loginView();
    else if (S.tab === 'home') view.innerHTML = homeView();
    else if (S.tab === 'diary') view.innerHTML = diaryView();
    else if (S.tab === 'stats') view.innerHTML = statsView();
    else view.innerHTML = settingsView();
  }

  function renderStatus() {
    const el = $('#status');
    let html = '', err = false;
    if (S.error) {
      err = S.error.kind !== 'offline';
      html = esc(S.error.text) + (S.error.kind === 'auth'
        ? '<button data-act="login">Войти</button>'
        : '<button data-act="refresh">Повторить</button>');
    } else if (S.loading && S.progress && hasData()) {
      html = `Обновляю дневник… ${S.progress[0]} из ${S.progress[1]}`;
    } else if (S.statsLoading && S.tab === 'stats' && S.stats) {
      html = `Обновляю статистику… страниц: ${S.statsPages}`;
    }
    el.innerHTML = html;
    el.classList.toggle('err', err);
    el.classList.toggle('hidden', !html || S.needLogin);
  }

  const skeleton = (n = 3) => `<div class="card">${'<div class="skel"></div>'.repeat(n)}<div class="skel" style="width:60%"></div></div>`;

  function loginView() {
    return `<div class="hero">
      <div class="logo"><svg viewBox="0 0 24 24"><path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H20v16H5.5A1.5 1.5 0 0 0 4 20.5z"/><path d="M4 20.5A1.5 1.5 0 0 0 5.5 22H20v-3"/><path d="M8 8h8M8 12h6"/></svg></div>
      <h2>re:schools</h2>
      <p>Дневник schools.by в удобном виде: отметки за сегодня, ДЗ на завтра и новые отметки с прошлого раза.</p>
      <button class="btn" data-act="login">Войти через schools.by</button>
      <p class="note" style="margin-top:20px">Вход — на самом сайте schools.by. Приложение не видит пароль и никуда не отправляет ваши данные.</p>
    </div>`;
  }

  function homeView() {
    if (!hasData()) return S.loading || !S.error ? skeleton(2) + skeleton(4) + skeleton(3) : '<div class="empty">Дневник ещё не загружен.</div>';
    const all = lessons();
    const today = RS.today();
    return nowCards(all, today) + newMarksCard(all, today) + todayCard(all, today) + homeworkCard(all, today) + syncNote();
  }

  // ---------- что сейчас: урок, перемена, неурочное время ----------

  const nowMin = () => { const d = RS.now(); return d.getHours() * 60 + d.getMinutes() + d.getSeconds() / 60; };
  const nowStatus = (all, today) => RS.nowStatus(all, today, nowMin(), S.settings.bells);
  const span = (x) => `${RS.fromMin(x.start).replace(/^0/, '')}–${RS.fromMin(x.end).replace(/^0/, '')}`;
  const at = (m) => RS.fromMin(m).replace(/^0/, '');
  const subjOf = (x) => x.lessons.map((l) => subj(l.subject)).join(' / ');
  // Сколько осталось — обновляется раз в 15 секунд без перерисовки (tick).
  const left = (end) => `<span class="left" data-end="${end}">${leftText(end)}</span>`;
  function leftText(end) {
    const n = Math.max(1, Math.ceil(end - nowMin()));
    return n >= 60 ? `ещё ${Math.floor(n / 60)} ч ${n % 60} мин` : `ещё ${n} мин`;
  }
  const progress = (x) => `<div class="now-bar"><i data-start="${x.start}" data-end="${x.end}" style="width:${pct(x)}%"></i></div>`;
  const pct = (x) => Math.max(0, Math.min(100, ((nowMin() - x.start) / (x.end - x.start)) * 100));

  function nextLessonCard(all, today, x, title) {
    const hw = RS.homeworkFor(all, today, S.settings.hwMode).find((it) => it.nums.includes(x.num));
    return `<section class="card next">
      <div class="card-h"><h2>${title}</h2><span class="sub">${x.num} урок · ${span(x)}</span></div>
      <div class="name">${subjOf(x)}</div>
      ${hw && hw.hw ? `<div class="hw">${linkify(hw.hw)}</div>` : ''}</section>`;
  }

  function nowCards(all, today) {
    const st = nowStatus(all, today);
    if (st.kind === 'lesson') {
      const x = st.cur;
      return `<section class="card now">
        <div class="now-h"><span class="pill">идёт</span><span class="sub">${x.num} урок · ${span(x)}</span>${left(x.end)}</div>
        <div class="name">${subjOf(x)}</div>${progress(x)}
        ${st.next ? `<div class="meta">Дальше в ${at(st.next.start)}: ${subjOf(st.next)}</div>` : '<div class="meta">Последний урок сегодня</div>'}
      </section>`;
    }
    if (st.kind === 'break') {
      return `<section class="card mini"><b>Перемена</b><span class="sub">до ${at(st.end)}</span>${left(st.end)}</section>` +
        nextLessonCard(all, today, st.next, 'Следующий урок');
    }
    const text = st.reason === 'before' ? `уроки с ${at(st.next.start)}`
      : st.reason === 'after' ? `уроки закончились в ${at(st.last.end)}` : 'сегодня уроков нет';
    return `<section class="card mini"><b>Неурочное время</b><span class="sub">${text}</span>${st.reason === 'before' ? left(st.next.start) : ''}</section>` +
      (st.reason === 'before' ? nextLessonCard(all, today, st.next, 'Первый урок') : '');
  }

  // Перерисовываем, когда урок сменился переменой и т. п.; иначе только «осталось» и полоски.
  let nowKey = ''; // состояние на момент последней отрисовки (render)
  function nowKeyOf() {
    if (!hasData()) return '';
    const st = nowStatus(lessons(), RS.today());
    return `${RS.today()}|${st.kind}|${st.reason || ''}|${(st.cur || st.next || {}).num || ''}`;
  }
  function tick() {
    if (document.visibilityState !== 'visible' || S.needLogin || !['home', 'diary'].includes(S.tab)) return;
    if (nowKeyOf() !== nowKey) return render();
    for (const el of document.querySelectorAll('.left[data-end]')) el.textContent = leftText(+el.dataset.end);
    for (const el of document.querySelectorAll('.now-bar i')) el.style.width = `${pct({ start: +el.dataset.start, end: +el.dataset.end })}%`;
  }
  setInterval(tick, 15000);

  function syncNote() {
    const at = S.lastSync || (S.pupil && RS.store.get(`${S.pupil.id}:lastSync`));
    return at ? `<p class="note" style="text-align:center">Обновлено ${when(at)}</p>` : '';
  }

  function newMarksCard(all, today) {
    const base = S.visit && S.visit.baseline;
    const list = RS.newMarks(all, base, today);
    let body;
    if (!list) {
      body = '<div class="empty">Здесь появятся отметки, которые выставят после этого захода (кроме сегодняшних — они ниже).</div>';
    } else if (!list.length) {
      body = `<div class="empty">С прошлого захода (${when(base.at)}) новых отметок нет.</div>`;
    } else {
      body = list.map((l) => `<div class="row">
          ${marksHtml({ marks: l.marks, absent: false })}
          <div class="main"><div class="name">${subj(l.subject)}</div><div class="meta">${dateShort(l.date)} · ${l.num} урок${l.was ? ` · исправлена, было ${esc(l.was)}` : ''}</div></div>
        </div>`).join('');
    }
    const n = list ? list.length : 0;
    return `<section class="card${n ? ' accent' : ''}">
      <div class="card-h"><h2>Новые отметки</h2>${n ? `<span class="count">${n}</span>` : ''}</div>
      ${n ? `<div class="meta since">с прошлого захода, ${when(base.at)}</div>` : ''}
      ${body}</section>`;
  }

  function todayCard(all, today) {
    const dayLessons = all.filter((l) => l.date === today);
    const marked = RS.todayMarks(all, today);
    let body;
    if (marked.length) {
      body = marked.map((l) => `<div class="row">
          ${marksHtml(l)}
          <div class="main"><div class="name">${subj(l.subject)}</div><div class="meta">${l.num} урок</div></div>
        </div>`).join('');
    } else if (!dayLessons.length) {
      body = `<div class="empty">${RS.parseISO(today).getDay() === 0 ? 'Сегодня выходной.' : 'Сегодня уроков нет.'}</div>`;
    } else {
      body = '<div class="empty">Отметок пока нет.</div>';
    }
    return `<section class="card">
      <div class="card-h"><h2>Сегодня</h2><span class="sub">${dayName(today).toLowerCase()}, ${dateLong(today)}</span></div>
      ${body}</section>`;
  }

  function homeworkCard(all, today) {
    const target = RS.nextSchoolDay(all, today);
    const items = RS.homeworkFor(all, target, S.settings.hwMode);
    let body;
    if (!items.length) {
      body = `<div class="empty">Расписания на ${dateLong(target)} в дневнике пока нет.</div>`;
    } else {
      body = items.map((it) => {
        const nums = it.nums.length > 1 ? `${it.nums[0]}–${it.nums[it.nums.length - 1]}` : it.nums[0];
        const hw = it.hw ? `<div class="hw">${linkify(it.hw)}</div>` : '<div class="hw none">не задано</div>';
        const from = it.hw && S.settings.hwMode === 'assigned' && it.from ? `<div class="meta">задано ${dateShort(it.from)}</div>` : '';
        return `<div class="row"><div class="num">${nums}</div>
          <div class="main"><div class="name">${subj(it.subject)}</div>${hw}${from}</div></div>`;
      }).join('');
    }
    return `<section class="card">
      <div class="card-h"><h2>ДЗ ${relDay(target, today)}</h2><span class="sub">${dateLong(target)}</span></div>
      ${body}</section>`;
  }

  function diaryView() {
    const monday = S.week;
    const sat = RS.addDays(monday, 5);
    const cur = RS.mondayOf(RS.today());
    const m1 = RS.parseISO(monday), m2 = RS.parseISO(sat);
    const range = m1.getMonth() === m2.getMonth()
      ? `${m1.getDate()} – ${m2.getDate()} ${MONTHS_GEN[m2.getMonth()]}`
      : `${dateLong(monday)} – ${dateLong(sat)}`;
    const rel = monday === cur ? 'эта неделя' : monday === RS.addDays(cur, 7) ? 'следующая неделя'
      : monday === RS.addDays(cur, -7) ? 'прошлая неделя' : '';
    const bar = `<div class="weekbar">
      <button class="icon-btn" data-act="week" data-d="-7" aria-label="Предыдущая неделя"><svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg></button>
      <div class="range">${range}<small>${rel || '&nbsp;'}</small></div>
      ${monday !== cur ? '<button class="chip" data-act="thisweek">Сегодня</button>' : ''}
      <button class="icon-btn" data-act="week" data-d="7" aria-label="Следующая неделя"><svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg></button>
    </div>`;

    const week = S.diary && S.diary.cached(monday);
    if (!week) return bar + (S.weekLoading || S.loading ? skeleton(4) + skeleton(4) + skeleton(4) : '<div class="empty">Нет данных за эту неделю.</div>');
    if (week.none || !week.lessons.length) {
      return bar + `<div class="card"><div class="empty">${week.none ? 'Этой недели нет в дневнике — похоже, каникулы.' : 'На этой неделе уроков нет.'}</div></div>`;
    }

    const today = RS.today();
    const dates = new Set(week.dates || []);
    for (const l of week.lessons) dates.add(l.date);
    const days = [...dates].sort().map((d) => {
      const ls = week.lessons.filter((l) => l.date === d).sort((a, b) => a.num - b.num);
      if (!ls.length && RS.parseISO(d).getDay() === 6) return '';
      const vals = ls.flatMap((l) => l.marks);
      const avg = vals.length ? `<span class="day-avg">ср. ${RS.fmt(RS.mean(vals))}</span>` : '';
      // Сегодня: идущий урок помечен «идёт», перемена — отдельной строкой перед следующим уроком.
      const st = d === today ? nowStatus(ls, today) : null;
      const rows = ls.length ? ls.map((l) => {
        const live = st && st.kind === 'lesson' && st.cur.num === l.num;
        const brk = st && st.kind === 'break' && st.next.num === l.num && st.next.lessons[0] === l
          ? `<div class="row brk"><div class="num"></div><div class="main"><div class="name">Перемена</div>
              <div class="meta">${at(st.start)}–${at(st.end)} · ${left(st.end)}</div></div></div>` : '';
        return `${brk}<div class="row${live ? ' live' : ''}">
          <div class="num">${l.num}</div>
          <div class="main"><div class="name">${subj(l.subject)}${live ? ' <span class="pill">идёт</span>' : ''}</div>
            ${live ? `<div class="meta">${span(st.cur)} · ${left(st.cur.end)}</div>` : ''}${l.hw ? `<div class="hw">${linkify(l.hw)}</div>` : ''}</div>
          ${marksHtml(l, true)}
        </div>`;
      }).join('') : '<div class="empty">Уроков нет.</div>';
      return `<section class="card${d === today ? ' today' : ''}" ${d === today ? 'id="today"' : ''}>
        <div class="day-h"><h3>${dayName(d)}</h3><span class="sub">${dateLong(d)}</span>${d === today ? '<span class="pill">сегодня</span>' : ''}${avg}</div>
        ${rows}</section>`;
    }).join('');
    return bar + days;
  }

  // ---------- статистика (из schools++, src/ui.js) ----------

  const fmt = RS.fmt, fmtSigned = RS.fmtSigned;

  const mark = (v, title) => (v == null ? '<span class="muted">—</span>'
    : `<span class="mark xs l${RS.markLevel(v)}"${title ? ` title="${esc(title)}"` : ''}>${v}</span>`);

  const qv = (s) => (!s || !s.n ? '<span class="muted">—</span>'
    : `<span class="qv" title="${s.n} ${RS.plural(s.n, 'отметка', 'отметки', 'отметок')}, среднее ${fmt(s.avg, 3)}">${mark(s.rounded)}<span class="ex">${fmt(s.avg)}</span></span>`);

  const trend = (t) => {
    if (t == null) return '<span class="flat">—</span>';
    const txt = `${fmtSigned(t, 1)}/мес`;
    if (t > 0.25) return `<span class="up" title="Отметки растут">▲ ${txt}</span>`;
    if (t < -0.25) return `<span class="down" title="Отметки снижаются">▼ ${txt}</span>`;
    return `<span class="flat" title="Без заметных изменений">● ${txt}</span>`;
  };

  const delta = (d) => (d == null ? '<span class="flat">—</span>'
    : `<span class="${d > 0.05 ? 'up' : d < -0.05 ? 'down' : 'flat'}">${fmtSigned(d)}</span>`);

  const qShort = (q) => {
    const m = q.name.match(/\d+/);
    return m ? `${m[0]} четв.` : q.name;
  };

  const kpi = (t, v, s, small) =>
    `<div class="kpi"><div class="t">${t}</div><div class="v">${v}${small ? `<small>${small}</small>` : ''}</div>${s ? `<div class="s">${s}</div>` : ''}</div>`;

  const bands = (st) => st.quarters.filter((q) => st.ranges[q.id])
    .map((q) => ({ start: st.ranges[q.id].start, end: st.ranges[q.id].end, label: qShort(q) }));

  // Период отметки предмета: четверть или полугодие (у предметов раз в неделю).
  const tShort = (t) => t.short || qShort(t);
  const isHalf = (s) => s.period === 'h';
  const periodGen = (s) => (isHalf(s) ? 'полугодия' : 'четверти');
  const periodPrep = (s) => (isHalf(s) ? 'в этом полугодии' : 'в этой четверти');

  const statsProgressText = () => `Загружаю дневник за учебный год…${S.statsPages ? ` страниц: ${S.statsPages}` : ''}`;

  function statsView() {
    const seg = `<div class="segbar"><div class="seg">${STATS_TABS.map(([k, t]) =>
      `<button data-act="stab" data-key="${k}" class="${S.statsTab === k ? 'on' : ''}">${t}</button>`).join('')}</div></div>`;
    const st = S.stats;
    if (!st) {
      if (S.statsLoading) {
        return seg + `<div class="card"><div class="empty" id="stats-progress">${statsProgressText()}</div>
          <p class="note">В первый раз загружаются все недели с начала года — это может занять до минуты.</p></div>` + skeleton(4) + skeleton(3);
      }
      return seg + `<div class="card"><div class="empty">Статистика ещё не загружена.</div>
        <div class="actions" style="margin-top:12px"><button class="btn block" data-act="stats-load">Загрузить</button></div></div>`;
    }
    if (!st.subjects.length) return seg + '<div class="card"><div class="empty">Отметок за этот учебный год пока нет.</div></div>';
    RS.charts.defaultWidth = Math.max(280, Math.min(920, $('#view').clientWidth - 64));
    const body = S.statsTab === 'subject' ? subjectView(st) : S.statsTab === 'dynamics' ? dynamicsView(st) : overviewView(st);
    return seg + `<div class="stats">${body}</div>`;
  }

  function overviewView(st) {
    const o = st.overall;
    const curQ = st.quarters.find((q) => q.id === st.current);
    const pct = o.lessons ? (o.absences / o.lessons) * 100 : 0;
    let h = '<div class="kpis">';
    h += kpi(`Средний балл${curQ ? ' · ' + esc(qShort(curQ)) : ''}`, fmt(o.currentExact), 'среднее по предметам', `по округл. ${fmt(o.currentRounded)}`);
    h += kpi('Годовой средний', fmt(o.yearExact), 'по четвертным отметкам', `по округл. ${fmt(o.yearRounded)}`);
    h += kpi('Все отметки', fmt(o.avgAll), `${o.marks} ${RS.plural(o.marks, 'отметка', 'отметки', 'отметок')}`);
    h += kpi('Последние 30 дней', fmt(o.recent30), o.before30 != null ? `к прошлым 30 дням: ${delta(o.recent30 - o.before30)}` : `${o.recent30n} отметок`);
    h += kpi('Пропуски', String(o.absences), `из ${o.lessons} уроков · ${fmt(pct, 1)}%`);
    h += '</div>';

    h += '<h2 class="sec">Предметы</h2>';
    h += subjectCards(st, sortSubjects(st));
    h += '<p class="note">Крупная цифра — округлённая отметка, мелкая — точное среднее. Годовая считается как среднее округлённых четвертных ' +
      '(у предметов раз в неделю — полугодовых; выбирается в настройках). ' +
      `Порог округления: ${fmt(st.threshold)} (меняется в настройках).</p>`;
    if (st.noMarks.length) h += `<p class="note">Без отметок: ${st.noMarks.map((s) => esc(s.name)).join(', ')}.</p>`;

    // Близко к повышению / на грани понижения — по текущей четверти.
    const near = [], risk = [];
    for (const s of st.subjects) {
      const c = s.current;
      if (!c || !c.n) continue;
      const toUp = c.rounded < 10 ? c.rounded + st.threshold - c.avg : Infinity;
      const toDown = c.avg - (c.rounded - 1 + st.threshold);
      if (toUp <= 0.2) near.push({ s, v: toUp });
      if (c.rounded > 1 && toDown < 0.15) risk.push({ s, v: toDown });
    }
    near.sort((a, b) => a.v - b.v);
    risk.sort((a, b) => a.v - b.v);
    h += `<section class="card"><div class="card-h"><h2>Почти следующая отметка</h2>${curQ ? `<span class="sub">${esc(qShort(curQ))}</span>` : ''}</div>` +
      (near.length ? near.map(({ s }) => {
        const f = s.forecast.up[0];
        return `<p>${esc(s.name)}: ${fmt(s.current.avg)} → ${mark(s.current.rounded + 1)} нужно ${f.count}×${f.mark}</p>`;
      }).join('') : '<div class="empty">Нет предметов рядом с границей</div>') + '</section>';
    h += '<section class="card"><div class="card-h"><h2>На грани понижения</h2></div>' +
      (risk.length ? risk.map(({ s }) => `<p>${esc(s.name)}: ${fmt(s.current.avg)}. Отметка ниже ${s.forecast.down.minSafe} опустит до ${mark(s.forecast.down.dropTo)}</p>`).join('')
        : '<div class="empty">Таких предметов нет</div>') + '</section>';

    h += '<section class="card"><div class="card-h"><h2>Распределение отметок</h2></div>' + RS.charts.distribution(st.distribution) + '</section>';
    return h;
  }

  const yearCell = (s) => (s.year.terms
    ? `<span class="qv">${mark(s.year.rounded)}<span class="ex">${fmt(s.year.exact)}</span></span>` +
      (s.year.terms < s.year.of ? `<span class="tag">${s.year.terms}/${s.year.of}</span>` : '')
    : '<span class="muted">—</span>');

  function subjectCards(st, subs) {
    const sorts = [['name', 'по названию'], ['year', 'по годовой'], ...(st.current ? [[`q:${st.current}`, 'по текущей четверти']] : []), ['trend', 'по тренду'], ['abs', 'по пропускам']];
    let h = `<select class="input msort" data-act="msort">${sorts.map(([k, t]) => `<option value="${k}"${S.sort.key === k ? ' selected' : ''}>Сортировка ${t}</option>`).join('')}</select>`;
    h += '<div class="scards">';
    for (const s of subs) {
      h += `<div class="scard" data-act="subject" data-key="${esc(s.key)}">` +
        `<div class="sc-top"><span class="sc-name">${esc(s.name)}</span><span class="sc-year"><span class="muted small">год</span> ${yearCell(s)}</span></div>` +
        '<div class="sc-qs">' + s.terms.map((t) =>
          `<div class="sc-q${t.qids.includes(st.current) ? ' cur' : ''}"${t.qids.length > 1 ? ` style="grid-column: span ${t.qids.length}"` : ''}>` +
          `<span class="sc-ql">${esc(tShort(t))}</span>${qv(t.sum)}</div>`).join('') + '</div>' +
        `<div class="sc-foot"><span>${s.all.n} отм. · ср. ${fmt(s.all.avg)}${s.absences ? ` · пропуски ${s.absences}` : ''}</span>${trend(s.trend)}${RS.charts.sparkline(s.running.slice(-15).map((r) => r.avg))}</div>` +
        '</div>';
    }
    h += '<div class="scard total"><div class="sc-top"><span class="sc-name">Средний балл</span>' +
      `<span class="sc-year"><span class="muted small">год</span> ${fmt(st.overall.yearExact)}</span></div><div class="sc-qs">` +
      st.quarters.map((q) => {
        const t = st.quarterTotals[q.id];
        return `<div class="sc-q${q.id === st.current ? ' cur' : ''}"><span class="sc-ql">${esc(qShort(q))}</span>${t.subjects ? `<b>${fmt(t.exact)}</b>` : '—'}</div>`;
      }).join('') + '</div></div>';
    return h + '</div>';
  }

  function sortSubjects(st) {
    const { key, dir } = S.sort;
    const val = (s) => {
      if (key === 'name') return s.name;
      if (key === 'year') return s.year.exact;
      if (key === 'all') return s.all.avg;
      if (key === 'n') return s.all.n;
      if (key === 'abs') return s.absences;
      if (key === 'trend') return s.trend;
      if (key.startsWith('q:')) return s.byQ[key.slice(2)]?.avg;
      return 0;
    };
    return [...st.subjects].sort((a, b) => {
      const x = val(a), y = val(b);
      if (typeof x === 'string') return dir * x.localeCompare(y, 'ru');
      if (x == null && y == null) return 0;
      if (x == null) return 1;
      if (y == null) return -1;
      return dir * (x - y);
    });
  }

  function subjectView(st) {
    const s = st.subjects.find((x) => x.key === S.subject) || st.subjects[0];
    S.subject = s.key;
    const curT = s.currentTerm;
    const c = s.current;

    let h = '<select class="input subj-pick" data-act="pick-subject">' +
      st.subjects.map((x) => `<option value="${esc(x.key)}"${x.key === s.key ? ' selected' : ''}>${esc(x.name)}</option>`).join('') +
      '</select>';

    h += '<div class="kpis">';
    const curLabel = esc(curT ? tShort(curT) : isHalf(s) ? 'Полугодие' : 'Четверть');
    if (c && c.n) h += kpi(curLabel, mark(c.rounded), `${c.n} отметок`, fmt(c.avg, 3));
    else h += kpi(curLabel, '—', 'отметок пока нет');
    h += kpi('Годовая', s.year.terms ? mark(s.year.rounded) : '—', `по ${s.year.terms} из ${s.year.of} ${isHalf(s) ? 'полуг.' : 'четв.'}`, fmt(s.year.exact, 3));
    h += kpi('За год (все отметки)', fmt(s.all.avg), `${s.all.n} отметок`);
    h += kpi('Медиана · мода', `${fmt(s.all.median, 1)} · ${s.all.mode}`, `мин ${s.all.min}, макс ${s.all.max}`);
    h += kpi('Разброс', fmt(s.all.std), 'стандартное отклонение');
    h += kpi('Тренд', trend(s.trend), s.prev5 != null ? `последние 5: ${fmt(s.last5)} (были ${fmt(s.prev5)})` : '');
    h += kpi('Пропуски', String(s.absences), `из ${s.lessons} уроков`);
    h += '</div>';

    // Прогноз на четверть (полугодие)
    const f = s.forecast;
    h += `<section class="card"><div class="card-h"><h2>Что нужно ${periodPrep(s)}</h2></div>`;
    if (!f) h += `<div class="empty">Отметок ${periodPrep(s)} пока нет</div>`;
    else {
      if (f.target && f.up.length) {
        h += `<p>Чтобы получить ${mark(f.target)}: ` + f.up.map((u) => `<b>${u.count}</b>×${u.mark}`).join(' или ') + '</p>';
      } else if (!f.target) h += '<p>Уже максимальная отметка 🎉</p>';
      if (f.down) {
        h += f.down.anySafe
          ? `<p>Одна любая отметка не опустит ниже ${mark(c.rounded)}</p>`
          : `<p>Одна отметка ниже <b>${f.down.minSafe}</b> опустит до ${mark(f.down.dropTo)}</p>`;
      }
    }
    h += '</section>';
    h += '<section class="card"><div class="card-h"><h2>А что если…</h2></div><div class="whatif">' +
      '<input class="input" data-act="whatif" inputmode="numeric" placeholder="добавить отметки, напр. 9 10 8">' +
      '<span class="whatif-out muted">введите отметки</span></div>' +
      `<p class="note">Считается для текущего ${periodGen(s)}.</p></section>`;

    // График: отметки + накопленное среднее внутри четверти (полугодия)
    let prevQ = null;
    const avgPts = s.running.map((r) => {
      const p = { date: r.date, y: r.avg, break: r.q !== prevQ };
      prevQ = r.q;
      return p;
    });
    const dots = s.marks.map((m) => ({ date: m.date, y: m.v, cls: `lv${RS.markLevel(m.v)}`, title: `${RS.fmtDate(m.date)}: ${m.v}` }));
    h += '<section class="card"><div class="card-h"><h2>Динамика</h2></div>' + RS.charts.line({
      bands: bands(st),
      series: [
        { points: dots, line: false, dots: true, r: 4 },
        { points: avgPts, cls: '', step: false },
      ],
    });
    h += `<div class="legend"><span><i class="dot"></i>отметка</span><span><i></i>среднее ${isHalf(s) ? 'в полугодии' : 'в четверти'} на эту дату</span></div></section>`;

    // По четвертям (полугодиям)
    h += `<section class="card"><div class="card-h"><h2>Отметки по ${isHalf(s) ? 'полугодиям' : 'четвертям'}</h2></div>`;
    if (isHalf(s)) {
      h += `<p class="note">${s.period === s.periodAuto ? 'Урок раз в неделю — отметка' : 'Отметка'} выставляется за полугодие. Меняется в настройках.</p>`;
    }
    for (const t of s.terms) {
      const ms = s.marks.filter((m) => t.qids.includes(m.q));
      const sq = t.sum;
      h += `<div class="qblock"><div class="qt">${esc(t.name)} ${sq.n ? `${mark(sq.rounded)} <span class="muted">${fmt(sq.avg, 3)} · ${sq.n} отм. · мин ${sq.min}, макс ${sq.max}</span>` : '<span class="muted">нет отметок</span>'}</div>`;
      if (ms.length) h += '<div class="chips">' + ms.map((m) => mark(m.v, RS.fmtDate(m.date))).join('') + '</div>';
      h += '</div>';
    }
    return h + '</section>';
  }

  function dynamicsView(st) {
    let h = '<section class="card"><div class="card-h"><h2>Средний балл по неделям</h2></div>';
    h += RS.charts.line({
      bands: bands(st),
      yMin: Math.min(4, Math.floor(Math.min(10, ...st.weekly.map((w) => w.avg)))), yMax: 10,
      series: [
        { points: st.weekly.map((w) => ({ date: w.week, y: w.avg, title: `Неделя с ${RS.fmtDate(w.week)}: ${fmt(w.avg)} (${w.n} отм.)` })), cls: 'soft', dots: true, r: 3 },
        { points: st.weekly.map((w) => ({ date: w.week, y: w.cumulative })), cls: 'alt' },
      ],
    });
    h += '<div class="legend"><span><i class="dot"></i>среднее за неделю</span><span><i class="alt"></i>накопленное среднее с начала года</span></div></section>';

    h += '<section class="card"><div class="card-h"><h2>Недели</h2></div>' +
      (st.bestWeek ? `<p>Лучшая: с ${RS.fmtDate(st.bestWeek.week)}, ${fmt(st.bestWeek.avg)} (${st.bestWeek.n} отм.)</p>` : '') +
      (st.worstWeek ? `<p>Худшая: с ${RS.fmtDate(st.worstWeek.week)}, ${fmt(st.worstWeek.avg)} (${st.worstWeek.n} отм.)</p>` : '') +
      (st.weekly.length ? `<p class="muted">Недель с отметками: ${st.weekly.length}, в среднем ${fmt(st.overall.marks / st.weekly.length, 1)} отм. в неделю</p>` : '<div class="empty">Нет данных</div>') +
      '</section>';
    h += '<section class="card"><div class="card-h"><h2>По дням недели</h2></div>' +
      RS.charts.hbars(st.byWeekday.filter((d) => d.n).map((d) => ({ label: d.day, value: d.avg, note: `${d.n} отм.` }))) + '</section>';

    const rows = st.subjects.map((s) => ({ s, d: s.recent30 != null && s.before30 != null ? s.recent30 - s.before30 : null }))
      .sort((a, b) => (b.d ?? -99) - (a.d ?? -99));
    h += '<section class="card"><div class="card-h"><h2>Изменения по предметам</h2></div>';
    h += '<div class="tscroll"><table class="tbl"><thead><tr><th>Предмет</th><th class="dk" title="С 60-го по 30-й день назад">30–60 дней назад</th><th>Последние 30 дней</th><th>Изменение</th>' +
      '<th class="dk">Предыдущие 5</th><th class="dk">Последние 5</th><th>Тренд</th></tr></thead><tbody>';
    for (const { s, d } of rows) {
      h += `<tr class="link" data-act="subject" data-key="${esc(s.key)}"><td class="subj">${esc(s.name)}</td><td class="dk">${fmt(s.before30)}</td><td>${fmt(s.recent30)}</td>` +
        `<td>${delta(d)}</td><td class="dk">${fmt(s.prev5)}</td><td class="dk">${fmt(s.last5)}</td><td>${trend(s.trend)}</td></tr>`;
    }
    h += '</tbody></table></div></section>';

    h += '<section class="card"><div class="card-h"><h2>По четвертям</h2></div><div class="tscroll"><table class="tbl"><thead><tr><th>Четверть</th><th>Средний балл (точно)</th><th>По округлённым</th><th class="dk">Предметов</th><th>Отметок</th></tr></thead><tbody>';
    for (const q of st.quarters) {
      const t = st.quarterTotals[q.id];
      h += `<tr><td>${esc(q.name)}${st.inProgress[q.id] ? ' <span class="tag">идёт</span>' : ''}</td><td>${fmt(t.exact, 3)}</td><td>${fmt(t.rounded, 3)}</td><td class="dk">${t.subjects}</td><td>${t.marks}</td></tr>`;
    }
    return h + '</tbody></table></div></section>';
  }

  function onWhatIf(input) {
    const out = document.querySelector('.whatif-out');
    const s = S.stats?.subjects.find((x) => x.key === S.subject);
    const extra = RS.parseMarkText(input.value).marks;
    if (!s || !extra.length) { out.className = 'whatif-out muted'; out.textContent = 'введите отметки'; return; }
    const c = s.current && s.current.n ? s.current : { n: 0, sum: 0 };
    const n = c.n + extra.length, sum = c.sum + extra.reduce((a, b) => a + b, 0);
    const avg = sum / n;
    out.className = 'whatif-out';
    out.innerHTML = `среднее ${fmt(avg, 3)} → ${mark(RS.roundMark(avg, S.settings.threshold))}` +
      (c.n ? ` <span class="muted">(сейчас ${fmt(c.avg, 3)})</span>` : '');
  }

  // На телефоне нет наведения: подсказку точки графика показываем по нажатию.
  let tipTimer = null;
  function showTip(text) {
    const tip = $('#tip');
    tip.textContent = text;
    tip.classList.remove('hidden');
    clearTimeout(tipTimer);
    tipTimer = setTimeout(() => tip.classList.add('hidden'), 2500);
  }

  function settingsView() {
    const pupils = S.pupils.length > 1 ? `<section class="card"><div class="card-h"><h2>Ученик</h2></div>
      ${S.pupils.map((p) => `<label class="opt"><input type="radio" name="pupil" value="${esc(p.id)}" ${S.pupil && S.pupil.id === p.id ? 'checked' : ''}>
        <div>${esc(p.name)}</div></label>`).join('')}</section>` : '';
    const mode = S.settings.hwMode;
    const d = S.statsData;
    const statsInfo = d ? `<div class="meta">Загружено ${when(d.fetchedAt)} · страниц: ${d.pages} · уроков: ${d.lessons.length} · ` +
      `отметок: ${d.lessons.reduce((a, l) => a + l.marks.length, 0)}</div>` +
      `<div class="meta">Четверти: ${d.quarters.map((q) => `${esc(q.name)} (id ${esc(q.id)})`).join(', ')}</div>` +
      (d.errors?.length ? `<div class="meta err">Ошибки при загрузке (${d.errors.length}): ${d.errors.slice(0, 5).map(esc).join('; ')}</div>` : '') : '';
    const bells = S.settings.bells.map((b, i) => `<div class="bell"><span class="num">${i + 1}</span>
        <input class="input" type="time" name="bell" data-i="${i}" data-k="0" value="${esc(b[0])}"><span>–</span>
        <input class="input" type="time" name="bell" data-i="${i}" data-k="1" value="${esc(b[1])}"></div>`).join('');
    return pupils + `<section class="card">
        <div class="card-h"><h2>Расписание звонков</h2></div>
        <div class="meta" style="margin-bottom:8px">По нему на главной и в дневнике видно, идёт ли урок или перемена.</div>
        ${bells}
        <div class="bell-acts">
          <button class="chip" data-act="bell-add">+ урок</button>
          ${S.settings.bells.length > 1 ? '<button class="chip" data-act="bell-del">− урок</button>' : ''}
          <button class="chip" data-act="bell-reset">По умолчанию</button>
        </div>
      </section>
      <section class="card">
        <div class="card-h"><h2>Где schools.by пишет ДЗ</h2></div>
        <label class="opt"><input type="radio" name="hw" value="assigned" ${mode === 'assigned' ? 'checked' : ''}>
          <div><div>На уроке, где его задали</div><div class="meta">ДЗ на завтра берётся с предыдущего урока по тому же предмету. Так устроен дневник schools.by.</div></div></label>
        <label class="opt"><input type="radio" name="hw" value="due" ${mode === 'due' ? 'checked' : ''}>
          <div><div>На уроке, к которому его сделать</div><div class="meta">Выберите, если на главной ДЗ оказывается от прошлого урока.</div></div></label>
      </section>
      <section class="card">
        <div class="card-h"><h2>Статистика</h2></div>
        <label class="field"><span>Округлять вверх, если дробная часть ≥</span>
          <input class="input" type="number" name="threshold" min="0.05" max="0.95" step="0.05" inputmode="decimal" value="${S.settings.threshold}"></label>
        <div class="meta">0,5: 7,50 → 8, 7,49 → 7</div>
        ${statsInfo}
        <div class="actions" style="margin-top:12px"><button class="btn ghost block" data-act="stats-full"${S.statsLoading ? ' disabled' : ''}>Загрузить статистику заново</button></div>
      </section>
      ${periodsCard()}
      <section class="card"><div class="actions">
        <button class="btn ghost block" data-act="site">Открыть сайт schools.by</button>
        <button class="btn ghost block" data-act="login">Войти в другой аккаунт</button>
        <button class="btn ghost block" data-act="clear">Удалить сохранённый дневник</button>
      </div></section>
      <p class="note" style="text-align:center">re:schools · данные хранятся только на этом устройстве</p>`;
  }

  function perWeekText(v) {
    if (v == null) return '';
    const n = Math.round(v * 10) / 10;
    return Number.isInteger(n) ? `${n} ${RS.plural(n, 'урок', 'урока', 'уроков')} в неделю` : `${fmt(n, 1)} урока в неделю`;
  }

  // За какой период выставляется отметка по каждому предмету.
  function periodsCard() {
    const st = S.stats;
    const head = '<div class="card-h"><h2>Четвертные отметки</h2></div>' +
      '<div class="meta" style="margin-bottom:4px">За четверть или за полугодие. По умолчанию предметы, которые идут раз в неделю, ' +
      'оцениваются за полугодие. По этим отметкам считаются годовые в статистике.</div>';
    if (!st) return `<section class="card">${head}<div class="empty">Предметы появятся после загрузки статистики.</div></section>`;
    const list = [...st.subjects, ...st.noMarks].sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    const name = { q: 'четверть', h: 'полугодие' };
    const rows = list.map((s) => {
      const own = S.periods[s.key];
      const opt = (v, t) => `<option value="${v}"${(own || '') === v ? ' selected' : ''}>${t}</option>`;
      return `<div class="period"><div><div>${esc(s.name)}</div>` +
        `<div class="meta">${perWeekText(s.perWeek)}</div></div>` +
        `<select class="input" name="period" data-key="${esc(s.key)}">` +
        opt('', `авто: ${name[s.periodAuto]}`) + opt('q', name.q) + opt('h', name.h) + '</select></div>';
    }).join('');
    return `<section class="card">${head}${rows}</section>`;
  }

  // ---------- действия ----------

  function setTab(tab) {
    if (tab === S.tab) {
      if (tab === 'diary') openWeek(RS.mondayOf(RS.today()));
      $('#view').scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    if (tab === 'settings') S.prevTab = S.tab;
    S.tab = tab;
    render();
    $('#view').scrollTop = 0;
    if (tab === 'diary') {
      openWeek(S.week);
      requestAnimationFrame(() => document.getElementById('today')?.scrollIntoView({ block: 'center' }));
    }
    if (tab === 'stats' && statsStale()) refreshStats(false);
  }

  function setStatsTab(key) {
    S.statsTab = key;
    render();
    $('#view').scrollTop = 0;
  }

  document.addEventListener('click', (e) => {
    const tabBtn = e.target.closest('.tabs button');
    if (tabBtn) return setTab(tabBtn.dataset.tab);
    const titled = e.target.closest('circle, rect');
    if (titled && titled.querySelector('title')) return showTip(titled.querySelector('title').textContent);
    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.dataset.act;
    if (act === 'login') location.href = LOGIN_URL;
    else if (act === 'refresh') (S.tab === 'stats' ? refreshStats(false) : refresh());
    else if (act === 'stab') setStatsTab(el.dataset.key);
    else if (act === 'subject') { S.subject = el.dataset.key; setStatsTab('subject'); }
    else if (act === 'stats-load') refreshStats(false);
    else if (act === 'stats-full') refreshStats(true);
    else if (act === 'week') openWeek(RS.addDays(S.week, +el.dataset.d));
    else if (act === 'thisweek') openWeek(RS.mondayOf(RS.today()));
    else if (act === 'site') location.href = S.pupil ? `${S.pupil.origin}/pupil/${S.pupil.id}` : 'https://schools.by/';
    else if (act === 'bell-add') {
      const last = S.settings.bells[S.settings.bells.length - 1];
      const [a, b] = last ? last.map(RS.toMin) : [420, 465];
      setBells([...S.settings.bells, [RS.fromMin(a + 60), RS.fromMin(b + 60)]]);
    } else if (act === 'bell-del') setBells(S.settings.bells.slice(0, -1));
    else if (act === 'bell-reset') setBells(RS.DEFAULT_BELLS);
    else if (act === 'clear') {
      if (!confirm('Удалить сохранённый дневник? Он заново загрузится с сайта.')) return;
      for (const k of ['links', 'weeks', 'visit', 'lastSync', 'stats']) RS.store.remove(`${S.pupil.id}:${k}`);
      S.tab = 'home';
      selectPupil(S.pupil);
    }
  });

  document.addEventListener('change', (e) => {
    if (e.target.name === 'bell') {
      if (!/^\d\d?:\d\d$/.test(e.target.value)) return;
      const bells = S.settings.bells.map((b) => [...b]);
      bells[+e.target.dataset.i][+e.target.dataset.k] = e.target.value;
      S.settings.bells = bells;
      RS.store.set('settings', S.settings);
    } else if (e.target.name === 'period') {
      const periods = { ...S.periods };
      if (e.target.value) periods[e.target.dataset.key] = e.target.value;
      else delete periods[e.target.dataset.key];
      S.periods = periods;
      RS.store.set(`${S.pupil.id}:periods`, periods);
      recompute();
    } else if (e.target.name === 'hw') {
      S.settings.hwMode = e.target.value;
      RS.store.set('settings', S.settings);
    } else if (e.target.name === 'pupil') {
      const p = S.pupils.find((x) => x.id === e.target.value);
      if (p) { S.tab = 'home'; selectPupil(p); }
    } else if (e.target.name === 'threshold') {
      const v = parseFloat(String(e.target.value).replace(',', '.'));
      if (v > 0 && v < 1) {
        S.settings.threshold = v;
        RS.store.set('settings', S.settings);
        recompute();
      }
    } else if (e.target.dataset.act === 'pick-subject') {
      S.subject = e.target.value;
      render();
    } else if (e.target.dataset.act === 'msort') {
      const key = e.target.value;
      S.sort = { key, dir: key === 'name' ? 1 : -1 };
      render();
    }
  });

  document.addEventListener('input', (e) => {
    if (e.target.dataset.act === 'whatif') onWhatIf(e.target);
  });

  function setBells(bells) {
    S.settings.bells = bells.map((b) => [...b]);
    RS.store.set('settings', S.settings);
    const top = $('#view').scrollTop;
    render();
    $('#view').scrollTop = top;
  }

  $('#refresh').addEventListener('click', () => {
    if (S.tab === 'diary' && S.diary) reloadWeek();
    else if (S.tab === 'stats') refreshStats(false);
    else refresh();
  });
  $('#settings-btn').addEventListener('click', () => setTab('settings'));
  $('#back').addEventListener('click', () => setTab(S.prevTab));

  async function reloadWeek() {
    S.diary.expanded.delete(S.week);
    await openWeek(S.week);
  }

  // Системная кнопка «Назад» на Android (оболочка вызывает RS.app.back()): из настроек, дневника и статистики —
  // на главную (из раздела статистики — сначала в «Обзор»). false — обрабатывать нечего, оболочка сворачивает приложение.
  function back() {
    if (S.needLogin || S.tab === 'home') return false;
    if (S.tab === 'stats' && S.statsTab !== 'overview') { setStatsTab('overview'); return true; }
    setTab(S.tab === 'settings' ? S.prevTab : 'home');
    return true;
  }

  // Свайп по дневнику — соседняя неделя.
  let touch = null;
  const view = $('#view');
  view.addEventListener('touchstart', (e) => {
    const t = e.touches[0];
    touch = { x: t.clientX, y: t.clientY, top: view.scrollTop };
  }, { passive: true });
  view.addEventListener('touchmove', (e) => {
    if (!touch || S.tab !== 'home' || touch.top > 0) return;
    const dy = e.touches[0].clientY - touch.y;
    const ptr = $('#ptr');
    ptr.classList.add('pulling');
    ptr.style.height = `${Math.max(0, Math.min(56, dy / 2))}px`;
  }, { passive: true });
  view.addEventListener('touchend', (e) => {
    const ptr = $('#ptr');
    ptr.classList.remove('pulling');
    ptr.style.height = '';
    if (!touch) return;
    const t = e.changedTouches[0];
    const dx = t.clientX - touch.x, dy = t.clientY - touch.y;
    if (S.tab === 'diary' && Math.abs(dx) > 70 && Math.abs(dy) < 50) openWeek(RS.addDays(S.week, dx < 0 ? 7 : -7));
    if (S.tab === 'home' && touch.top <= 0 && dy > 110 && Math.abs(dx) < 60) refresh();
    touch = null;
  });

  // Вернулись в приложение после перерыва — это новый заход: новые отметки считаются от прошлого.
  document.addEventListener('visibilitychange', () => {
    if (!S.visit) return;
    if (document.visibilityState === 'hidden') {
      S.visit.lastActive = Date.now();
      saveVisit();
    } else {
      const isNew = !S.visit.lastActive || Date.now() - S.visit.lastActive >= RS.VISIT_GAP;
      S.visit = RS.startVisit(S.visit);
      saveVisit();
      if (isNew) { render(); refresh(); } else tick();
    }
  });

  // ---------- запуск ----------

  async function init() {
    const params = new URLSearchParams(location.search);
    const from = params.get('from');
    if (from) {
      history.replaceState(null, '', location.pathname);
      try {
        const found = await pupilsFromPage(from);
        if (found.length) RS.store.set('pupil', found[0].id);
      } catch (e) {
        if (e instanceof RS.AuthError) S.pupils = [];
        else S.error = errorOf(e);
      }
    }
    const id = RS.store.get('pupil');
    const p = S.pupils.find((x) => x.id === id) || S.pupils[0];
    if (!p) { S.needLogin = true; render(); return; }
    selectPupil(p);
  }

  RS.app = { state: S, refresh, refreshStats, openWeek, setTab, back, tick };
  init();
})(globalThis.RS);
