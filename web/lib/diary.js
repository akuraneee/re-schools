// Загрузка дневника с schools.by. Страница приложения открыта на домене школы (оболочка подставляет её
// по адресу https://<школа>.schools.by/__rs/…), поэтому запросы идут с куками входа, как у самого сайта.
//
// Неделя: GET /pupil/<id>/dnevnik/quarter/<qid>/week/<понедельник>. qid (четверть) заранее неизвестен —
// узнаём его из ссылок «предыдущая/следующая неделя» (send_to="…/quarter/<qid>/week/<дата>") в уже
// загруженных неделях и в ответе /pupil/<id>/dnevnik.
(function (RS) {
  'use strict';

  const REQUEST_DELAY = 120;

  class AuthError extends Error {
    constructor() { super('Нужно войти на schools.by'); this.name = 'AuthError'; }
  }
  RS.AuthError = AuthError;

  class OfflineError extends Error {
    constructor() { super('Нет связи с schools.by'); this.name = 'OfflineError'; }
  }
  RS.OfflineError = OfflineError;

  RS.fetchPage = async function (url) {
    let res;
    try {
      // Сайт сам грузит дневник через XHR — повторяем те же заголовки.
      res = await fetch(url, {
        credentials: 'include',
        headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'text/html, */*; q=0.01' },
      });
    } catch (e) {
      throw new OfflineError();
    }
    if (/\/login/.test(res.url)) throw new AuthError();
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
    const html = await res.text();
    const doc = new DOMParser().parseFromString(html, 'text/html');
    if (doc.querySelector('input[type="password"]') && !doc.querySelector(RS.SEL.day)) throw new AuthError();
    return { url: res.url, html, doc };
  };

  function nameFromTitle(title) {
    const part = RS.norm(String(title).split(/[.|—–-]/)[0]);
    return /[А-Яа-яЁёA-Za-z]/.test(part) && part.length < 60 ? part : '';
  }

  // Ученики, доступные с текущей страницы: сам ученик (/pupil/<id>) или дети на странице родителя.
  // (Из schools++, src/crawler.js.)
  RS.findPupils = function (doc, loc) {
    const found = new Map();
    const here = loc.pathname.match(/\/pupil\/(\d+)/);
    if (here) {
      const h1 = doc.querySelector('.title_box h1');
      const fromH1 = h1 ? RS.norm(h1.textContent.split(',')[0]) : '';
      found.set(here[1], { id: here[1], origin: loc.origin, name: (fromH1.length < 60 && fromH1) || nameFromTitle(doc.title) });
    }
    for (const a of doc.querySelectorAll('a[href*="/pupil/"]')) {
      let u;
      try { u = new URL(a.getAttribute('href'), loc.href); } catch { continue; }
      const m = u.pathname.match(/^\/pupil\/(\d+)\/?$/) || u.pathname.match(/^\/pupil\/(\d+)\//);
      if (!m) continue;
      const text = RS.norm(a.textContent);
      const prev = found.get(m[1]);
      const good = /[А-Яа-яЁё]{2,}/.test(text) && text.length < 60 && !/дневник|отметк|расписан/i.test(text);
      if (!prev) found.set(m[1], { id: m[1], origin: u.origin, name: good ? text : '' });
      else if (!prev.name && good) prev.name = text;
    }
    return [...found.values()].map((p) => ({ ...p, name: p.name || `Ученик #${p.id}` }));
  };

  // Хранилище: localStorage страницы приложения (домен школы). Любая ошибка — просто без кэша.
  RS.store = {
    get(key, def = null) {
      try { const v = localStorage.getItem('rs:' + key); return v == null ? def : JSON.parse(v); } catch { return def; }
    },
    set(key, value) {
      try { localStorage.setItem('rs:' + key, JSON.stringify(value)); } catch { /* переполнено или запрещено */ }
    },
    remove(key) {
      try { localStorage.removeItem('rs:' + key); } catch { /* ignore */ }
    },
  };

  class Diary {
    constructor(pupil, opts = {}) {
      this.pupil = pupil;
      this.fetchPage = opts.fetchPage || RS.fetchPage;
      this.delay = opts.delay ?? REQUEST_DELAY;
      this.prefix = `${pupil.id}:`;
      this.links = RS.store.get(this.prefix + 'links', {}); // понедельник -> qid
      this.weeks = RS.store.get(this.prefix + 'weeks', {}); // понедельник -> { lessons, dates, at }
      this.expanded = new Set(); // недели, загруженные в этой сессии (их соседи уже известны)
      this.bootstrapped = false;
      this.inflight = new Map();
    }

    save() {
      RS.store.set(this.prefix + 'links', this.links);
      RS.store.set(this.prefix + 'weeks', this.weeks);
    }

    weekRe() {
      return new RegExp(`/pupil/${this.pupil.id}/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})`, 'g');
    }

    collectLinks(text) {
      for (const m of text.matchAll(this.weekRe())) this.links[RS.mondayOf(m[2])] = m[1];
    }

    // Ответ вкладки «Дневник»: в нём ссылки на недели текущей четверти (и, возможно, сама текущая неделя).
    async bootstrap() {
      this.bootstrapped = true;
      const page = await this.fetchPage(`${this.pupil.origin}/pupil/${this.pupil.id}/dnevnik`);
      this.collectLinks(page.url + ' ' + page.html);
      const m = page.html.match(/weekUrl\s*=\s*['"][^'"]*\/week\/(\d{4}-\d{2}-\d{2})/);
      if (m && page.doc.querySelector(RS.SEL.day)) this.storeWeek(RS.mondayOf(m[1]), page);
      this.save();
    }

    storeWeek(monday, page) {
      const { lessons, dates } = RS.parseWeek(page.doc, monday);
      this.weeks[monday] = { lessons, dates, at: Date.now() };
      this.expanded.add(monday);
      return this.weeks[monday];
    }

    async fetchWeek(monday) {
      const qid = this.links[monday];
      const page = await this.fetchPage(`${this.pupil.origin}/pupil/${this.pupil.id}/dnevnik/quarter/${qid}/week/${monday}`);
      this.collectLinks(page.url + ' ' + page.html);
      const week = this.storeWeek(monday, page);
      this.save();
      if (this.delay) await RS.sleep(this.delay);
      return week;
    }

    // Ищем qid недели: идём по ссылкам соседних недель от ближайшей известной.
    // null — такой недели в дневнике нет (каникулы между четвертями, другой учебный год).
    async resolve(monday) {
      for (let step = 0; step < 14; step++) {
        if (this.links[monday]) return this.links[monday];
        const known = Object.keys(this.links).sort();
        if (!known.length || (!this.bootstrapped && step === 0)) {
          if (!this.bootstrapped) { await this.bootstrap(); continue; }
          return null;
        }
        const before = known.filter((d) => d < monday).pop();
        const after = known.find((d) => d > monday);
        const options = [before, after].filter((d) => d && !this.expanded.has(d));
        if (!options.length) return null;
        options.sort((a, b) => Math.abs(RS.daysBetween(a, monday)) - Math.abs(RS.daysBetween(b, monday)));
        await this.fetchWeek(options[0]);
      }
      return null;
    }

    // Неделя из сети (force) или из кэша, если она уже загружалась в этой сессии.
    async loadWeek(monday, { force = false } = {}) {
      monday = RS.mondayOf(monday);
      if (!force && this.expanded.has(monday) && this.weeks[monday]) return this.weeks[monday];
      const key = monday;
      if (this.inflight.has(key)) return this.inflight.get(key);
      const p = (async () => {
        const qid = await this.resolve(monday);
        if (!qid) {
          this.weeks[monday] = { lessons: [], dates: [], at: Date.now(), none: true };
          this.expanded.add(monday);
          return this.weeks[monday];
        }
        if (this.expanded.has(monday) && !force) return this.weeks[monday];
        return this.fetchWeek(monday);
      })();
      this.inflight.set(key, p);
      try { return await p; } finally { this.inflight.delete(key); }
    }

    cached(monday) {
      return this.weeks[RS.mondayOf(monday)] || null;
    }

    allLessons() {
      return Object.values(this.weeks).flatMap((w) => w.lessons)
        .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.num - b.num));
    }

    // Недели, которые обновляем при каждом открытии: с последнего захода (но не больше 6 недель назад,
    // и минимум две недели — учителя иногда ставят отметки задним числом) и следующая неделя.
    weeksToRefresh(today, lastVisitAt) {
      const cur = RS.mondayOf(today);
      let from = RS.addDays(cur, -7);
      if (lastVisitAt) {
        const lv = RS.mondayOf(RS.iso(new Date(lastVisitAt)));
        if (lv < from) from = lv;
      }
      const floor = RS.addDays(cur, -42);
      if (from < floor) from = floor;
      const list = [];
      for (let d = cur; d >= from; d = RS.addDays(d, -7)) list.push(d);
      list.splice(1, 0, RS.addDays(cur, 7)); // текущая, следующая, затем прошлые
      return list;
    }

    async refresh(today, lastVisitAt, onProgress) {
      const list = this.weeksToRefresh(today, lastVisitAt);
      let done = 0;
      this.expanded.clear(); // всё перекачиваем, но каждую неделю — один раз (часть грузится при поиске qid)
      for (const w of list) {
        await this.loadWeek(w);
        onProgress?.(++done, list.length);
      }
    }
  }
  RS.Diary = Diary;
})(globalThis.RS = globalThis.RS || {});
