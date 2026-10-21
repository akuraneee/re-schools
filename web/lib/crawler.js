// Обход дневника за весь учебный год для статистики: находит четверти и все недели каждой четверти.
// Из schools++ (src/crawler.js); запросы — через RS.fetchPage (diary.js), ДЗ в статистике не храним.
(function (RS) {
  'use strict';

  const MAX_PAGES_PER_QUARTER = 30;
  const REQUEST_DELAY = 150;

  function quarterLabel(text) {
    const t = RS.norm(text);
    if (!t || t.length > 30) return '';
    if (/четв|чвэрц|полуг|паўг|триместр|^[IV]+$|\d/i.test(t)) return t;
    return '';
  }

  async function discoverQuarters(origin, pid) {
    const page = await RS.fetchPage(`${origin}/pupil/${pid}/dnevnik`);
    const re = new RegExp(`/pupil/${pid}/dnevnik/quarter/(\\d+)`, 'g');
    const ids = new Set();
    for (const m of (page.url + ' ' + page.html).matchAll(re)) ids.add(m[1]);

    const names = {};
    for (const a of page.doc.querySelectorAll('a[href*="/dnevnik/quarter/"]')) {
      const m = (a.getAttribute('href') || '').match(/\/dnevnik\/quarter\/(\d+)/);
      if (!m) continue;
      const label = quarterLabel(a.textContent);
      if (label && !names[m[1]]) names[m[1]] = label;
    }
    // Недели, на которые уже есть ссылки (send_to="…/quarter/92/week/2026-10-05", weekUrl = '…').
    const seeds = {};
    const weekRe = new RegExp(`/pupil/${pid}/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})`, 'g');
    for (const m of (page.url + ' ' + page.html).matchAll(weekRe)) {
      ids.add(m[1]);
      (seeds[m[1]] = seeds[m[1]] || new Set()).add(m[2]);
    }
    return [...ids].sort((a, b) => a - b).map((id) => ({ id, name: names[id] || '', seeds: [...(seeds[id] || [])] }));
  }

  async function crawlQuarter(origin, pid, q, ctx) {
    const weekRe = new RegExp(`/pupil/${pid}/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})`, 'g');
    const weekUrl = (d) => `${origin}/pupil/${pid}/dnevnik/quarter/${q.id}/week/${d}`;
    const weeks = new Map(); // date -> lessons
    const seen = new Set();
    const queue = q.seeds && q.seeds.length
      ? q.seeds.map(weekUrl)
      : [`${origin}/pupil/${pid}/dnevnik/quarter/${q.id}`];
    let pages = 0;

    const load = async (url) => {
      pages++;
      const page = await RS.fetchPage(url);
      ctx.pages++;
      ctx.onProgress?.(ctx);
      const m = page.url.match(/\/week\/(\d{4}-\d{2}-\d{2})/) || url.match(/\/week\/(\d{4}-\d{2}-\d{2})/);
      const weekISO = m ? m[1] : null;
      const lessons = RS.parseWeek(page.doc, weekISO).lessons.map(({ hw, ...l }) => l);
      const key = weekISO || (lessons[0] && RS.mondayOf(lessons[0].date));
      if (key && key >= ctx.yearStart && lessons.length && !weeks.has(key)) weeks.set(key, lessons);
      if (ctx.delay) await RS.sleep(ctx.delay);
      return { page, key, lessons };
    };

    // 1) Обход по ссылкам «предыдущая/следующая неделя», которые отдаёт сам сайт.
    while (queue.length && pages < MAX_PAGES_PER_QUARTER) {
      const url = queue.shift();
      if (seen.has(url)) continue;
      seen.add(url);
      let res;
      try { res = await load(url); } catch (e) {
        if (e instanceof RS.AuthError || e instanceof RS.OfflineError) throw e;
        ctx.errors.push(String(e.message || e));
        continue;
      }
      for (const m of (res.page.url + ' ' + res.page.html).matchAll(weekRe)) {
        if (m[2] < ctx.yearStart) continue; // прошлый учебный год не трогаем
        if (m[1] !== q.id) {
          // Ссылка в соседнюю четверть (например, «предыдущая неделя» из первой недели четверти).
          (ctx.foreign[m[1]] = ctx.foreign[m[1]] || new Set()).add(m[2]);
          continue;
        }
        const u = weekUrl(m[2]);
        if (!seen.has(u) && !weeks.has(m[2])) queue.push(u);
      }
      seen.add(weekUrl(res.key));
    }

    // 2) Запасной вариант: если ссылок на соседние недели не нашлось — идём по датам ±7 дней.
    if (weeks.size === 1) {
      const start = [...weeks.keys()][0];
      for (const dir of [7, -7]) {
        let d = start;
        for (let i = 0; i < 15 && pages < MAX_PAGES_PER_QUARTER * 2; i++) {
          d = RS.addDays(d, dir);
          if (seen.has(weekUrl(d))) break; // уже загружали на шаге 1 — там уроков не было
          try {
            const { lessons } = await load(weekUrl(d));
            if (!lessons.length) break;
          } catch (e) {
            if (e instanceof RS.AuthError || e instanceof RS.OfflineError) throw e;
            break;
          }
        }
      }
    }

    const lessons = [];
    for (const list of weeks.values()) for (const l of list) lessons.push({ ...l, q: q.id });
    return lessons;
  }

  const byDateNum = (a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.num - b.num);

  // Если одна и та же дата попала в две четверти (при обходе по датам), оставляем её
  // в той четверти, чья «середина» ближе.
  function dedupe(lessons) {
    const byQ = new Map();
    for (const l of lessons) {
      if (!byQ.has(l.q)) byQ.set(l.q, []);
      byQ.get(l.q).push(l.date);
    }
    const center = {};
    for (const [q, dates] of byQ) {
      dates.sort();
      center[q] = dates[Math.floor(dates.length / 2)];
    }
    const best = new Map();
    for (const l of lessons) {
      const key = RS.lessonKey(l);
      const prev = best.get(key);
      if (!prev) { best.set(key, l); continue; }
      const dPrev = Math.abs(RS.daysBetween(center[prev.q], l.date));
      const dCur = Math.abs(RS.daysBetween(center[l.q], l.date));
      if (dCur < dPrev) best.set(key, l);
    }
    return [...best.values()].sort(byDateNum);
  }

  function nameQuarters(quarters, lessons) {
    const first = {};
    for (const l of lessons) if (!first[l.q] || l.date < first[l.q]) first[l.q] = l.date;
    const sorted = [...quarters].sort((a, b) => {
      const fa = first[a.id], fb = first[b.id];
      if (fa && fb) return fa < fb ? -1 : 1;
      if (fa || fb) return fa ? -1 : 1;
      return a.id - b.id;
    });
    return sorted.map(({ seeds, kept, ...q }, i) => ({ ...q, order: i, name: q.name || `${i + 1} четверть` }));
  }

  /**
   * Загружает дневник ученика за учебный год: { pupil, quarters, lessons: [{date, num, subject, marks, absent, q}], fetchedAt, pages, errors }.
   * opts.onlyQuarters — id четвертей, которые нужно перекачать (остальные берутся из opts.previous).
   */
  RS.crawl = async function (pupil, opts = {}) {
    const today = opts.today || RS.today();
    const t = RS.parseISO(today);
    const yearStart = `${t.getMonth() >= 7 ? t.getFullYear() : t.getFullYear() - 1}-08-01`;
    const ctx = { pages: 0, errors: [], onProgress: opts.onProgress, foreign: {}, yearStart, delay: opts.delay ?? REQUEST_DELAY };
    const quarters = await discoverQuarters(pupil.origin, pupil.id);
    for (const q of quarters) q.seeds = q.seeds.filter((d) => d >= yearStart);
    ctx.pages++;
    if (!quarters.length) throw new Error('Не нашёл четвертей в дневнике. Возможно, разметка сайта отличается.');

    const only = opts.onlyQuarters ? new Set(opts.onlyQuarters) : null;
    const kept = only && opts.previous ? opts.previous.lessons.filter((l) => !only.has(l.q)) : [];
    if (opts.previous) {
      // Четверти, на которые со страницы дневника нет ссылок (обычно — следующие): обходим с их первой
      // известной недели, а прошлые — оставляем как есть, чтобы не потерять их названия и порядок.
      for (const p of opts.previous.quarters) {
        if (quarters.some((q) => q.id === p.id)) continue;
        const dates = opts.previous.lessons.filter((l) => l.q === p.id).map((l) => l.date).sort();
        const refetch = only && only.has(p.id) && dates.length;
        quarters.push({ id: p.id, name: p.name, seeds: refetch ? [RS.mondayOf(dates[0])] : [], kept: !refetch });
      }
    }
    const todo = quarters.filter((q) => !q.kept && (!only || only.has(q.id) || !opts.previous?.quarters.some((p) => p.id === q.id)));

    const results = await Promise.all(todo.map((q) => crawlQuarter(pupil.origin, pupil.id, q, ctx)));

    // Четверти, о которых узнали только по ссылкам из недель — догружаем (при быстром обновлении не нужно).
    for (let round = 0; round < 6 && !only; round++) {
      const known = new Set(quarters.map((q) => q.id));
      const extra = Object.keys(ctx.foreign).filter((id) => !known.has(id))
        .map((id) => ({ id, name: '', seeds: [...ctx.foreign[id]] }));
      if (!extra.length) break;
      quarters.push(...extra);
      results.push(...(await Promise.all(extra.map((q) => crawlQuarter(pupil.origin, pupil.id, q, ctx)))));
    }

    const lessons = dedupe(kept.concat(...results));
    const withLessons = new Set(lessons.map((l) => l.q));
    return {
      pupil,
      quarters: nameQuarters(quarters.filter((q) => withLessons.has(q.id)), lessons),
      lessons,
      fetchedAt: Date.now(),
      pages: ctx.pages,
      errors: ctx.errors,
    };
  };

  // Какие четверти стоит обновлять при быстром обновлении: текущая и будущие.
  RS.quartersToRefresh = function (data, todayISO = RS.today()) {
    const last = {};
    for (const l of data.lessons) if (l.marks.length && (!last[l.q] || l.date > last[l.q])) last[l.q] = l.date;
    const recent = RS.addDays(todayISO, -14);
    return data.quarters.filter((q) => !last[q.id] || last[q.id] >= recent).map((q) => q.id);
  };

  // Недели, которые только что перекачал дневник (главная, листание), подставляем в статистику,
  // чтобы она не отставала от «Главной». links — понедельник → qid; недели неизвестных четвертей пропускаем
  // (их подтянет следующая загрузка статистики).
  RS.mergeWeeks = function (data, weeks, links, mondays) {
    const known = new Set(data.quarters.map((q) => q.id));
    let lessons = data.lessons, changed = false;
    for (const m of mondays) {
      const w = weeks[m], q = links[m];
      if (!w || w.none || !w.lessons.length || !known.has(q)) continue;
      const end = RS.addDays(m, 7);
      lessons = lessons.filter((l) => l.date < m || l.date >= end)
        .concat(w.lessons.map(({ hw, ...l }) => ({ ...l, q })));
      changed = true;
    }
    return changed ? { ...data, lessons: lessons.sort(byDateNum) } : data;
  };
})(globalThis.RS = globalThis.RS || {});
