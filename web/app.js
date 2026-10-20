// Интерфейс re:schools: «Главная» (новые отметки, отметки за сегодня, ДЗ на завтра), «Дневник» (по неделям), настройки.
(function (RS) {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const esc = RS.esc;
  const LOGIN_URL = 'https://schools.by/login';

  const WEEKDAYS_FULL = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  const WEEKDAYS_ACC = ['воскресенье', 'понедельник', 'вторник', 'среду', 'четверг', 'пятницу', 'субботу'];
  const WEEKDAYS_SHORT = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
  const MONTHS_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];

  const S = {
    tab: 'home', // home | diary | settings
    prevTab: 'home',
    pupils: RS.store.get('pupils', []),
    pupil: null,
    diary: null,
    visit: null,
    settings: { hwMode: 'assigned', ...RS.store.get('settings', {}) },
    loading: false,
    progress: null,
    error: null, // { kind: 'auth' | 'offline' | 'other', text }
    week: RS.mondayOf(RS.today()),
    weekLoading: false,
    needLogin: false,
  };

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
    } catch (e) {
      S.error = errorOf(e);
    } finally {
      S.weekLoading = false;
      if (S.week === monday) render();
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
    const app = $('#app');
    app.dataset.tab = S.tab;
    const titles = { home: 'Главная', diary: 'Дневник', settings: 'Настройки' };
    $('#title').textContent = S.needLogin ? 're:schools' : titles[S.tab];
    $('#who').textContent = S.pupil ? S.pupil.name : '';
    $('#back').classList.toggle('hidden', S.tab !== 'settings');
    $('#tabs').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#refresh').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#settings-btn').classList.toggle('hidden', S.needLogin || S.tab === 'settings');
    $('#refresh').classList.toggle('spin', S.loading || S.weekLoading);
    for (const b of document.querySelectorAll('.tabs button')) b.classList.toggle('on', b.dataset.tab === S.tab);
    renderStatus();

    const view = $('#view');
    if (S.needLogin) view.innerHTML = loginView();
    else if (S.tab === 'home') view.innerHTML = homeView();
    else if (S.tab === 'diary') view.innerHTML = diaryView();
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
    return newMarksCard(all, today) + todayCard(all, today) + homeworkCard(all, today) + syncNote();
  }

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
      const rows = ls.length ? ls.map((l) => `<div class="row">
          <div class="num">${l.num}</div>
          <div class="main"><div class="name">${subj(l.subject)}</div>${l.hw ? `<div class="hw">${linkify(l.hw)}</div>` : ''}</div>
          ${marksHtml(l, true)}
        </div>`).join('') : '<div class="empty">Уроков нет.</div>';
      return `<section class="card${d === today ? ' today' : ''}" ${d === today ? 'id="today"' : ''}>
        <div class="day-h"><h3>${dayName(d)}</h3><span class="sub">${dateLong(d)}</span>${d === today ? '<span class="pill">сегодня</span>' : ''}${avg}</div>
        ${rows}</section>`;
    }).join('');
    return bar + days;
  }

  function settingsView() {
    const pupils = S.pupils.length > 1 ? `<section class="card"><div class="card-h"><h2>Ученик</h2></div>
      ${S.pupils.map((p) => `<label class="opt"><input type="radio" name="pupil" value="${esc(p.id)}" ${S.pupil && S.pupil.id === p.id ? 'checked' : ''}>
        <div>${esc(p.name)}</div></label>`).join('')}</section>` : '';
    const mode = S.settings.hwMode;
    return pupils + `<section class="card">
        <div class="card-h"><h2>Где schools.by пишет ДЗ</h2></div>
        <label class="opt"><input type="radio" name="hw" value="assigned" ${mode === 'assigned' ? 'checked' : ''}>
          <div><div>На уроке, где его задали</div><div class="meta">ДЗ на завтра берётся с предыдущего урока по тому же предмету. Так устроен дневник schools.by.</div></div></label>
        <label class="opt"><input type="radio" name="hw" value="due" ${mode === 'due' ? 'checked' : ''}>
          <div><div>На уроке, к которому его сделать</div><div class="meta">Выберите, если на главной ДЗ оказывается от прошлого урока.</div></div></label>
      </section>
      <section class="card"><div class="actions">
        <button class="btn ghost block" data-act="site">Открыть сайт schools.by</button>
        <button class="btn ghost block" data-act="login">Войти в другой аккаунт</button>
        <button class="btn ghost block" data-act="clear">Удалить сохранённый дневник</button>
      </div></section>
      <p class="note" style="text-align:center">re:schools · данные хранятся только на этом устройстве</p>`;
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
  }

  document.addEventListener('click', (e) => {
    const tabBtn = e.target.closest('.tabs button');
    if (tabBtn) return setTab(tabBtn.dataset.tab);
    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.dataset.act;
    if (act === 'login') location.href = LOGIN_URL;
    else if (act === 'refresh') refresh();
    else if (act === 'week') openWeek(RS.addDays(S.week, +el.dataset.d));
    else if (act === 'thisweek') openWeek(RS.mondayOf(RS.today()));
    else if (act === 'site') location.href = S.pupil ? `${S.pupil.origin}/pupil/${S.pupil.id}` : 'https://schools.by/';
    else if (act === 'clear') {
      if (!confirm('Удалить сохранённый дневник? Он заново загрузится с сайта.')) return;
      for (const k of ['links', 'weeks', 'visit', 'lastSync']) RS.store.remove(`${S.pupil.id}:${k}`);
      S.tab = 'home';
      selectPupil(S.pupil);
    }
  });

  document.addEventListener('change', (e) => {
    if (e.target.name === 'hw') {
      S.settings.hwMode = e.target.value;
      RS.store.set('settings', S.settings);
    } else if (e.target.name === 'pupil') {
      const p = S.pupils.find((x) => x.id === e.target.value);
      if (p) { S.tab = 'home'; selectPupil(p); }
    }
  });

  $('#refresh').addEventListener('click', () => (S.tab === 'diary' && S.diary ? reloadWeek() : refresh()));
  $('#settings-btn').addEventListener('click', () => setTab('settings'));
  $('#back').addEventListener('click', () => setTab(S.prevTab));

  async function reloadWeek() {
    S.diary.expanded.delete(S.week);
    await openWeek(S.week);
  }

  // Системная кнопка «Назад» на Android (оболочка вызывает RS.app.back()): из настроек и дневника — на главную.
  // false — обрабатывать нечего, оболочка сворачивает приложение.
  function back() {
    if (S.needLogin || S.tab === 'home') return false;
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
      if (isNew) { render(); refresh(); }
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

  RS.app = { state: S, refresh, openWeek, setTab, back };
  init();
})(globalThis.RS);
