// Подсчёт статистики по урокам за год. Из schools++ (src/stats.js), пространство имён SPP → RS.
(function (RS) {
  'use strict';

  function summary(vals, thr) {
    const n = vals.length;
    if (!n) return { n: 0, sum: 0, avg: null, rounded: null };
    const sum = vals.reduce((a, b) => a + b, 0);
    const avg = sum / n;
    const sorted = [...vals].sort((a, b) => a - b);
    const median = n % 2 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    const counts = {};
    let mode = null;
    for (const v of vals) {
      counts[v] = (counts[v] || 0) + 1;
      if (mode === null || counts[v] > counts[mode] || (counts[v] === counts[mode] && v > mode)) mode = v;
    }
    const std = Math.sqrt(vals.reduce((a, v) => a + (v - avg) ** 2, 0) / n);
    return { n, sum, avg, rounded: RS.roundMark(avg, thr), median, mode, min: sorted[0], max: sorted[n - 1], std };
  }
  RS.summary = summary;

  // Сколько отметок нужно, чтобы округлённая отметка выросла, и какая отметка её уронит.
  function forecast(s, thr) {
    if (!s || !s.n) return null;
    const r = s.rounded;
    const up = [];
    if (r < 10) {
      const target = r + thr;
      for (const m of [10, 9, 8, 7, 6, 5]) {
        if (m <= target) break;
        const k = Math.max(1, Math.ceil((target * s.n - s.sum) / (m - target) - 1e-9));
        up.push({ mark: m, count: k });
        if (up.length >= 3) break;
      }
    }
    let down = null;
    if (r > 1) {
      const floorAvg = r - 1 + thr;
      const x = Math.ceil(floorAvg * (s.n + 1) - s.sum - 1e-9);
      down = { minSafe: Math.max(1, x), anySafe: x <= 1, dropTo: r - 1 };
    }
    return { target: r < 10 ? r + 1 : null, up, down };
  }
  RS.forecast = forecast;

  // Наклон линейной регрессии «отметка от дня», в баллах за 30 дней.
  function trendPerMonth(marks) {
    if (marks.length < 4) return null;
    const x0 = marks[0].date;
    const xs = marks.map((m) => RS.daysBetween(x0, m.date));
    const ys = marks.map((m) => m.v);
    const mx = RS.mean(xs), my = RS.mean(ys);
    let num = 0, den = 0;
    for (let i = 0; i < xs.length; i++) {
      num += (xs[i] - mx) * (ys[i] - my);
      den += (xs[i] - mx) ** 2;
    }
    return den ? (num / den) * 30 : 0;
  }

  function quarterRanges(data) {
    const r = {};
    for (const l of data.lessons) {
      const q = (r[l.q] = r[l.q] || { start: l.date, end: l.date });
      if (l.date < q.start) q.start = l.date;
      if (l.date > q.end) q.end = l.date;
    }
    return r;
  }

  // Сколько уроков предмета в неделю: в среднем по учебным неделям (с уроками) от его первой до последней недели.
  RS.lessonsPerWeek = function (data) {
    const weeks = [...new Set(data.lessons.map((l) => RS.mondayOf(l.date)))].sort();
    const by = new Map();
    for (const l of data.lessons) {
      const key = RS.subjectKey(l.subject);
      const s = by.get(key) || { n: 0, first: l.date, last: l.date };
      s.n++;
      if (l.date < s.first) s.first = l.date;
      if (l.date > s.last) s.last = l.date;
      by.set(key, s);
    }
    const r = {};
    for (const [key, s] of by) {
      const a = RS.mondayOf(s.first), b = RS.mondayOf(s.last);
      r[key] = s.n / weeks.filter((w) => w >= a && w <= b).length;
    }
    return r;
  };

  // Предметы, которые идут раз в неделю (и реже), по умолчанию оцениваются за полугодие.
  RS.HALF_YEAR_BELOW = 1.5;
  RS.defaultPeriod = (perWeek) => (perWeek < RS.HALF_YEAR_BELOW ? 'h' : 'q');

  // Полугодия: 1–2 четверти и 3–4. Номер четверти — из названия («2 четверть»), иначе по порядку.
  function halves(quarters) {
    const res = [];
    for (const q of quarters) {
      const n = +(q.name.match(/\d/) || [])[0] || q.order + 1;
      const i = n <= 2 ? 0 : 1;
      if (!res[i]) res[i] = { id: `h${i + 1}`, name: `${i + 1} полугодие`, short: `${i + 1} полуг.`, qids: [] };
      res[i].qids.push(q.id);
    }
    return res.filter(Boolean);
  }

  RS.computeStats = function (data, opts = {}) {
    const thr = opts.threshold ?? 0.5;
    const today = opts.today || RS.today();
    const quarters = [...data.quarters].sort((a, b) => a.order - b.order);
    const ranges = quarterRanges(data);

    // Текущая четверть — последняя, в которой уже есть прошедшие уроки.
    let current = null;
    for (const q of quarters) {
      if (data.lessons.some((l) => l.q === q.id && l.date <= today)) current = q.id;
    }
    const inProgress = {};
    for (const q of quarters) {
      const rg = ranges[q.id];
      inProgress[q.id] = !!rg && rg.start <= today && rg.end >= today;
    }

    const subjMap = new Map();
    const allMarks = [];
    let lessonsPast = 0, absences = 0;
    for (const l of data.lessons) {
      const key = RS.subjectKey(l.subject);
      let s = subjMap.get(key);
      if (!s) subjMap.set(key, (s = { key, name: RS.subjectTitle(l.subject), marks: [], lessons: 0, absences: 0 }));
      if (l.date <= today) {
        s.lessons++; lessonsPast++;
        if (l.absent) { s.absences++; absences++; }
      }
      for (const v of l.marks) {
        const m = { v, date: l.date, q: l.q, subject: s.name };
        s.marks.push(m);
        allMarks.push(m);
      }
    }

    // Отметка за период: за четверть или (opts.periods[ключ] === 'h', по умолчанию — у предметов раз в неделю)
    // за полугодие. Годовая — среднее округлённых отметок за периоды.
    const perWeek = RS.lessonsPerWeek(data);
    const quarterTerms = quarters.map((q) => ({ id: q.id, name: q.name, short: null, qids: [q.id] }));
    const halfTerms = halves(quarters);
    const periods = opts.periods || {};

    const subjects = [];
    const noMarks = [];
    for (const s of subjMap.values()) {
      s.perWeek = perWeek[s.key];
      s.periodAuto = RS.defaultPeriod(s.perWeek);
      s.period = periods[s.key] === 'q' || periods[s.key] === 'h' ? periods[s.key] : s.periodAuto;
      if (!s.marks.length) { noMarks.push(s); continue; }
      const terms = (s.period === 'h' ? halfTerms : quarterTerms).map((t) => ({
        ...t, sum: summary(s.marks.filter((m) => t.qids.includes(m.q)).map((m) => m.v), thr),
      }));
      // byQ — отметка того периода, в который входит четверть (у полугодовых — одна на две четверти).
      const termOf = {};
      const byQ = {};
      for (const t of terms) for (const id of t.qids) { termOf[id] = t; byQ[id] = t.sum; }
      const done = terms.filter((t) => t.sum.n);
      const yearExact = RS.mean(done.map((t) => t.sum.rounded));
      const all = summary(s.marks.map((m) => m.v), thr);
      const cur = current ? termOf[current] : null;

      // Скользящее среднее внутри каждого периода — как «копится» четвертная (полугодовая) отметка.
      const running = [];
      const acc = {};
      for (const m of s.marks) {
        const t = termOf[m.q]?.id ?? m.q;
        const a = (acc[t] = acc[t] || { sum: 0, n: 0 });
        a.sum += m.v; a.n++;
        running.push({ date: m.date, v: m.v, q: t, avg: a.sum / a.n });
      }

      const last5 = s.marks.slice(-5).map((m) => m.v);
      const prev5 = s.marks.slice(-10, -5).map((m) => m.v);
      const d30 = RS.addDays(today, -30), d60 = RS.addDays(today, -60);
      const recent = s.marks.filter((m) => m.date > d30 && m.date <= today).map((m) => m.v);
      const before = s.marks.filter((m) => m.date > d60 && m.date <= d30).map((m) => m.v);

      subjects.push({
        name: s.name,
        key: s.key,
        marks: s.marks,
        lessons: s.lessons,
        absences: s.absences,
        perWeek: s.perWeek,
        period: s.period,
        periodAuto: s.periodAuto,
        terms,
        currentTerm: cur,
        byQ,
        all,
        year: { terms: done.length, of: s.period === 'h' ? 2 : Math.max(4, quarters.length), exact: yearExact, rounded: RS.roundMark(yearExact, thr) },
        current: cur ? cur.sum : null,
        forecast: cur ? forecast(cur.sum, thr) : null,
        trend: trendPerMonth(s.marks),
        last5: RS.mean(last5),
        prev5: prev5.length ? RS.mean(prev5) : null,
        recent30: RS.mean(recent),
        before30: RS.mean(before),
        running,
      });
    }
    subjects.sort((a, b) => a.name.localeCompare(b.name, 'ru'));

    // Итоги по четвертям: средний балл = среднее по предметам (у полугодовых — их отметка за полугодие).
    const quarterTotals = {};
    for (const q of quarters) {
      const list = subjects.map((s) => s.byQ[q.id]).filter((x) => x.n);
      quarterTotals[q.id] = {
        subjects: list.length,
        exact: RS.mean(list.map((x) => x.avg)),
        rounded: RS.mean(list.map((x) => x.rounded)),
        marks: allMarks.filter((m) => m.q === q.id).length,
      };
    }

    const distribution = Array(11).fill(0);
    for (const m of allMarks) distribution[m.v]++;

    // Понедельная динамика.
    const weekMap = new Map();
    for (const m of [...allMarks].sort((a, b) => (a.date < b.date ? -1 : 1))) {
      const w = RS.mondayOf(m.date);
      if (!weekMap.has(w)) weekMap.set(w, []);
      weekMap.get(w).push(m.v);
    }
    let cs = 0, cn = 0;
    const weekly = [...weekMap.entries()].sort().map(([week, vals]) => {
      cs += vals.reduce((a, b) => a + b, 0); cn += vals.length;
      return { week, avg: RS.mean(vals), n: vals.length, cumulative: cs / cn };
    });
    const solidWeeks = weekly.filter((w) => w.n >= 5);
    const bestWeek = solidWeeks.reduce((a, w) => (!a || w.avg > a.avg ? w : a), null);
    const worstWeek = solidWeeks.reduce((a, w) => (!a || w.avg < a.avg ? w : a), null);

    const byWeekday = Array.from({ length: 7 }, () => []);
    for (const m of allMarks) byWeekday[(RS.parseISO(m.date).getDay() + 6) % 7].push(m.v);

    const d30 = RS.addDays(today, -30), d60 = RS.addDays(today, -60);
    const recent30 = allMarks.filter((m) => m.date > d30 && m.date <= today).map((m) => m.v);
    const before30 = allMarks.filter((m) => m.date > d60 && m.date <= d30).map((m) => m.v);

    const withYear = subjects.filter((s) => s.year.terms);
    const withCurrent = subjects.filter((s) => s.current && s.current.n);

    return {
      threshold: thr,
      today,
      quarters,
      halves: halfTerms,
      ranges,
      current,
      inProgress,
      subjects,
      noMarks,
      quarterTotals,
      overall: {
        marks: allMarks.length,
        avgAll: RS.mean(allMarks.map((m) => m.v)),
        currentExact: RS.mean(withCurrent.map((s) => s.current.avg)),
        currentRounded: RS.mean(withCurrent.map((s) => s.current.rounded)),
        yearExact: RS.mean(withYear.map((s) => s.year.exact)),
        yearRounded: RS.mean(withYear.map((s) => s.year.rounded)),
        lessons: lessonsPast,
        absences,
        recent30: RS.mean(recent30),
        before30: RS.mean(before30),
        recent30n: recent30.length,
      },
      distribution,
      weekly,
      bestWeek,
      worstWeek,
      byWeekday: byWeekday.map((v, i) => ({ day: RS.WEEKDAYS[i], n: v.length, avg: RS.mean(v) })),
    };
  };
})(globalThis.RS = globalThis.RS || {});
