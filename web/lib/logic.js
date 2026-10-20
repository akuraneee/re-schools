// Что показывать на главной: отметки за сегодня, ДЗ на следующий учебный день, новые отметки с прошлого захода.
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
})(globalThis.RS = globalThis.RS || {});
