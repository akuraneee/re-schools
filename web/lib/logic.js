// Что показывать на главной: отметки за сегодня, ДЗ на следующий учебный день, новые отметки с прошлого захода,
// что идёт сейчас по расписанию звонков.
(function (RS) {
  'use strict';

  RS.lessonKey = (l) => `${l.date}|${l.num}|${RS.subjectKey(l.subject)}`;
  RS.markStr = (l) => l.marks.join('/');

  RS.todayMarks = (lessons, today) => lessons.filter((l) => l.date === today && (l.marks.length || l.absent));

  // Ближайший день после today, в который есть уроки (обычно завтра; в пятницу/субботу — понедельник).
  RS.nextSchoolDay = function (lessons, today) {
    const limit = RS.addDays(today, 10);
    let best = null;
    for (const l of lessons) if (l.date > today && l.date <= limit && (!best || l.date < best)) best = l.date;
    if (best) return best;
    let d = RS.addDays(today, 1);
    while ([0, 6].includes(RS.parseISO(d).getDay())) d = RS.addDays(d, 1);
    return d;
  };

  // ДЗ к урокам дня target.
  // mode 'assigned' — на schools.by ДЗ стоит в строке урока, на котором его задали (docs/schools-by.md из schools++),
  //   значит к уроку target относится ДЗ из предыдущего урока того же предмета;
  // mode 'due' — ДЗ записано прямо в строке урока, к которому его надо сделать.
  // Сдвоенные уроки одного предмета объединяются.
  RS.homeworkFor = function (lessons, target, mode = 'assigned') {
    const day = lessons.filter((l) => l.date === target).sort((a, b) => a.num - b.num);
    const items = [];
    for (const l of day) {
      const key = RS.subjectKey(l.subject);
      const last = items[items.length - 1];
      if (last && last.key === key && last.nums[last.nums.length - 1] === l.num - 1) {
        last.nums.push(l.num);
        if (mode === 'due' && l.hw && !last.hw.includes(l.hw)) last.hw = [last.hw, l.hw].filter(Boolean).join('\n');
        continue;
      }
      const item = { key, subject: l.subject, nums: [l.num], hw: '', from: null };
      if (mode === 'due') {
        item.hw = l.hw || '';
        item.from = target;
      } else {
        const prev = lessons.filter((x) => x.date < target && RS.subjectKey(x.subject) === key);
        const lastDate = prev.reduce((m, x) => (x.date > m ? x.date : m), '');
        if (lastDate) {
          const texts = [...new Set(prev.filter((x) => x.date === lastDate && x.hw).map((x) => x.hw))];
          item.hw = texts.join('\n');
          item.from = lastDate;
        }
      }
      items.push(item);
    }
    return items;
  };

  // Снимок известных отметок: какие недели загружены и что в них стояло.
  RS.snapshot = function (weeks, at = Date.now()) {
    const marks = {};
    for (const w of Object.values(weeks)) for (const l of w.lessons) if (l.marks.length) marks[RS.lessonKey(l)] = RS.markStr(l);
    return { at, weeks: Object.keys(weeks).filter((k) => !weeks[k].none), marks };
  };

  // Новые отметки по сравнению со снимком baseline (сделанным в конце прошлого захода).
  // Сегодняшние не считаем — они в отдельном блоке. Неделя, которой не было в снимке и которая целиком
  // раньше прошлого захода, — не «новая», а просто впервые загруженная (например, листали дневник).
  RS.newMarks = function (lessons, baseline, today) {
    if (!baseline) return null;
    const known = new Set(baseline.weeks);
    const visitDay = RS.iso(new Date(baseline.at));
    const out = [];
    for (const l of lessons) {
      if (!l.marks.length || l.date >= today) continue;
      const was = baseline.marks[RS.lessonKey(l)];
      if (was === RS.markStr(l)) continue;
      if (!known.has(RS.mondayOf(l.date)) && l.date < visitDay) continue;
      out.push({ ...l, was: was || null });
    }
    return out.sort((a, b) => (a.date > b.date ? -1 : a.date < b.date ? 1 : a.num - b.num));
  };

  // «Заход» — открытие приложения после перерыва. Короткие перерывы (свернул на минуту) заходом не считаем,
  // иначе новые отметки исчезали бы при каждом переключении приложений.
  RS.VISIT_GAP = 15 * 60 * 1000;

  // state: { baseline, seen, lastActive } для одного ученика. Возвращает новый state.
  RS.startVisit = function (state, now = Date.now()) {
    const s = state || {};
    if (s.lastActive && now - s.lastActive < RS.VISIT_GAP) return { ...s, lastActive: now };
    return { baseline: s.seen || null, seen: s.seen || null, lastActive: now };
  };

  // ---------- расписание звонков и «что сейчас»: урок, перемена или неурочное время ----------

  const pad = (n) => String(n).padStart(2, '0');
  RS.toMin = (hhmm) => { const [h, m] = String(hhmm).split(':').map(Number); return h * 60 + m; };
  RS.fromMin = (m) => `${pad(Math.floor(m / 60) % 24)}:${pad(m % 60)}`;
  RS.now = () => new Date(); // в самопроверке подменяется

  // По умолчанию: 1-й урок 8:00–8:45, 2-й 9:00–9:45 и так далее (уроки по 45 минут, перемены по 15).
  RS.DEFAULT_BELLS = Array.from({ length: 8 }, (_, i) => [RS.fromMin(480 + i * 60), RS.fromMin(525 + i * 60)]);

  // Звонки из настроек; испорченные (пустые, конец раньше начала) — как нет.
  RS.validBells = (bells) => (Array.isArray(bells) && bells.length ? bells : RS.DEFAULT_BELLS)
    .map((b) => (Array.isArray(b) && /^\d\d?:\d\d$/.test(b[0]) && /^\d\d?:\d\d$/.test(b[1]) && RS.toMin(b[0]) < RS.toMin(b[1]) ? b : null));

  // Уроки дня по номерам со временем звонков. Несколько уроков с одним номером (группы) — в одном пункте.
  RS.daySchedule = function (lessons, date, bells) {
    const valid = RS.validBells(bells);
    const byNum = new Map();
    for (const l of lessons) {
      if (l.date !== date) continue;
      const b = valid[l.num - 1];
      if (!b) continue;
      if (!byNum.has(l.num)) byNum.set(l.num, { num: l.num, lessons: [], start: RS.toMin(b[0]), end: RS.toMin(b[1]) });
      byNum.get(l.num).lessons.push(l);
    }
    return [...byNum.values()].sort((a, b) => a.start - b.start);
  };

  // now — минуты от полуночи (можно дробные). Возвращает
  //   { kind: 'lesson', cur, next } — идёт урок cur;
  //   { kind: 'break', prev, next, start, end } — перемена между уроками;
  //   { kind: 'free', reason: 'before' | 'after' | 'none', next?, last? } — неурочное время.
  RS.nowStatus = function (lessons, today, now, bells) {
    const day = RS.daySchedule(lessons, today, bells);
    if (!day.length) return { kind: 'free', reason: 'none' };
    for (let i = 0; i < day.length; i++) {
      const x = day[i];
      if (now >= x.start && now < x.end) return { kind: 'lesson', cur: x, next: day[i + 1] || null };
      if (now < x.start) {
        return i === 0 ? { kind: 'free', reason: 'before', next: x }
          : { kind: 'break', prev: day[i - 1], next: x, start: day[i - 1].end, end: x.start };
      }
    }
    return { kind: 'free', reason: 'after', last: day[day.length - 1] };
  };
})(globalThis.RS = globalThis.RS || {});
