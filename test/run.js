// Тесты логики на jsdom: разбор недели (с ДЗ), загрузка дневника через выдуманный сайт, переходы между четвертями,
// ДЗ на завтра, новые отметки с прошлого захода, звонки, статистика за год. Запуск: npm test
'use strict';
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');
const { createSite, HOST, PID } = require('./fake-site');

const TODAY = '2026-10-20'; // вторник, первая четверть; следующая неделя — каникулы

function makeWindow(site) {
  const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: `${HOST}/__rs/index.html`, runScripts: 'outside-only' });
  const w = dom.window;
  // fetch с переходами по редиректам, как в браузере
  w.fetch = async (url) => {
    let u = new URL(url, HOST).href;
    for (let i = 0; i < 5; i++) {
      const r = site.handle(u);
      if (r.status >= 300 && r.status < 400) { u = new URL(r.headers.location, u).href; continue; }
      return { url: u, ok: r.status < 400, status: r.status, text: async () => r.body };
    }
    throw new Error('redirect loop');
  };
  for (const f of ['utils.js', 'parser.js', 'diary.js', 'logic.js', 'crawler.js', 'stats.js', 'charts.js']) {
    w.eval(fs.readFileSync(path.join(__dirname, '..', 'web', 'lib', f), 'utf8'));
  }
  w.RS.today = () => site.today;
  return w;
}

let failed = 0, passed = 0;
async function test(name, fn) {
  try { await fn(); passed++; console.log(`  ✓ ${name}`); } catch (e) { failed++; console.log(`  ✗ ${name}\n    ${e.stack}`); }
}
function eq(a, b, msg = '') {
  const sa = JSON.stringify(a), sb = JSON.stringify(b);
  if (sa !== sb) throw new Error(`${msg}\n    ожидалось: ${sb}\n    получено:  ${sa}`);
}
function ok(v, msg) { if (!v) throw new Error(msg || 'ожидалось true'); }

const pupil = { id: PID, origin: HOST, name: 'Тестов Тест' };

(async () => {
  console.log('Разбор недели');

  await test('видимый блок, ДЗ с переводами строк, подсказки не путаются с отметками', () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const html = site.handle(`${HOST}/pupil/${PID}/dnevnik/quarter/92/week/2026-10-12`).body;
    const doc = new w.DOMParser().parseFromString(html, 'text/html');
    const { lessons, dates } = w.RS.parseWeek(doc, '2026-10-12');
    eq(dates, ['2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16'], 'дни (субботы нет в видимом блоке)');
    // отметки в скрытом блоке на 2 меньше — сверяем с тем, что сайт «на самом деле» поставил
    const hidden = w.RS.parseWeek(new w.DOMParser().parseFromString(html.replace('style="display: none;"', ''), 'text/html'), '2026-10-12');
    ok(lessons.some((l) => l.marks.length), 'есть отметки');
    ok(lessons.every((l) => l.marks.every((v) => v >= 4)), 'отметки не из скрытого блока (там бывают < 4)');
    ok(hidden.lessons.length > lessons.length || hidden.lessons.some((l, i) => String(l.marks) !== String(lessons[i]?.marks)), 'блоки различаются');
    const eng = lessons.find((l) => l.subject === 'Англ. яз.' && l.hw);
    ok(eng, 'есть ДЗ по английскому');
    ok(/^WB p\. \d+ ex\. \d+\nУчить слова Unit \d$/.test(eng.hw), `ДЗ сохранило перевод строки: ${JSON.stringify(eng.hw)}`);
    ok(lessons.every((l) => !l.marks.includes(12)), 'цифры из .hint не стали отметками');
  });

  console.log('Загрузка дневника');

  await test('обновление: текущая, следующая (каникулы) и прошлая неделя; qid берётся из ссылок', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const d = new w.RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, null);
    // 02.11 — первая неделя второй четверти: её загрузили, чтобы убедиться, что 26.10 в дневнике нет
    eq(Object.keys(d.weeks).sort(), ['2026-10-12', '2026-10-19', '2026-10-26', '2026-11-02']);
    ok(d.weeks['2026-10-26'].none, 'неделя каникул помечена как отсутствующая');
    ok(d.weeks['2026-10-19'].lessons.length > 20, 'текущая неделя загружена');
    eq(site.requests.length, new Set(site.requests).size, 'каждая страница — один запрос');
    ok(!site.requests.some((p) => p.includes('/quarter/92/week/2026-10-26')), 'не угадываем адрес недели каникул');
  });

  await test('переход через каникулы в следующую четверть', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const d = new w.RS.Diary(pupil, { delay: 0 });
    const wk = await d.loadWeek('2026-11-04');
    eq(d.links['2026-11-02'], '93');
    ok(wk.lessons.length > 20 && wk.lessons[0].date === '2026-11-02', 'неделя второй четверти');
    ok(wk.lessons.every((l) => !l.marks.length), 'будущая неделя без отметок');
  });

  await test('дневник сохраняется и читается после перезапуска', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const d = new w.RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, null);
    const d2 = new w.RS.Diary(pupil, { delay: 0 });
    eq(d2.allLessons().length, d.allLessons().length);
    eq(d2.links['2026-10-19'], '92');
  });

  await test('без входа — AuthError', async () => {
    const site = createSite({ today: TODAY, loggedIn: false });
    const w = makeWindow(site);
    const d = new w.RS.Diary(pupil, { delay: 0 });
    let err = null;
    try { await d.refresh(TODAY, null); } catch (e) { err = e; }
    ok(err instanceof w.RS.AuthError, `ожидался AuthError, получено ${err}`);
  });

  console.log('Главная');

  await test('ДЗ на завтра берётся с прошлого урока того же предмета, сдвоенные уроки объединены', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const d = new w.RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, null);
    const all = d.allLessons();
    const target = w.RS.nextSchoolDay(all, TODAY);
    eq(target, '2026-10-21');
    const items = w.RS.homeworkFor(all, target, 'assigned');
    eq(items.map((i) => i.subject), ['Рус. лит.', 'Физика', 'Информ.', 'География', 'Обществов.', 'Физ. к. и зд.']);
    const phys = items.find((i) => i.subject === 'Физика');
    eq(phys.from, '2026-10-19', 'физика была в понедельник');
    const mon = all.find((l) => l.date === '2026-10-19' && l.subject === 'Физика');
    eq(phys.hw, mon.hw);
    // вторник: «Матем.» 2-м и 3-м уроком → один пункт
    const tue = w.RS.homeworkFor(all, '2026-10-20', 'assigned');
    eq(tue.find((i) => i.subject === 'Матем.').nums, [2, 3]);
    const due = w.RS.homeworkFor(all, '2026-10-19', 'due');
    eq(due.find((i) => i.subject === 'Физика').hw, mon.hw, 'режим «к уроку» берёт ДЗ из самого урока');
  });

  await test('следующий учебный день: пятница → понедельник; выходные пропускаются', () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const l = (date) => ({ date, num: 1, subject: 'Матем.', marks: [], absent: false, hw: '' });
    eq(w.RS.nextSchoolDay([l('2026-10-16'), l('2026-10-19')], '2026-10-16'), '2026-10-19');
    eq(w.RS.nextSchoolDay([], '2026-10-23'), '2026-10-26');
  });

  await test('новые отметки с прошлого захода: без сегодняшних, исправленные — с прежним значением', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const RS = w.RS;
    let d = new RS.Diary(pupil, { delay: 0 });
    let visit = RS.startVisit(null, 1000);
    eq(visit.baseline, null, 'первый заход — сравнивать не с чем');
    await d.refresh(TODAY, null);
    eq(RS.newMarks(d.allLessons(), visit.baseline, TODAY), null);
    visit.seen = RS.snapshot(d.weeks, new Date('2026-10-20T08:00:00').getTime());

    // через 10 минут — тот же заход
    eq(RS.startVisit(visit, visit.lastActive + 10 * 60000).baseline, null, 'короткий перерыв — не новый заход');

    const { prev, older } = site.bump();
    visit = RS.startVisit(visit, visit.lastActive + 3 * 3600000);
    ok(visit.baseline && visit.baseline.weeks.includes('2026-10-12'), 'новый заход сравнивает со снимком прошлого');
    d = new RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, visit.baseline.at);
    const fresh = RS.newMarks(d.allLessons(), visit.baseline, TODAY);
    eq(fresh.map((l) => l.date), [prev, older]);
    eq(fresh[0].marks, [10]);
    eq(fresh[0].was, null);
    ok(fresh[1].was && fresh[1].was !== String(fresh[1].marks[0]), 'исправленная отметка помнит прежнюю');
    ok(fresh.every((l) => l.date !== TODAY), 'сегодняшние не входят');
  });

  await test('неделя, впервые загруженная при листании дневника, не считается новой', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const RS = w.RS;
    const d = new RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, null);
    const base = RS.snapshot(d.weeks, new Date('2026-10-20T08:00:00').getTime());
    await d.loadWeek('2026-09-21');
    eq(RS.newMarks(d.allLessons(), base, TODAY), []);
  });

  console.log('Звонки');

  await test('урок, перемена и неурочное время по расписанию звонков', () => {
    const w = makeWindow(createSite({ today: TODAY }));
    const RS = w.RS;
    const B = RS.DEFAULT_BELLS;
    eq(B.slice(0, 2), [['08:00', '08:45'], ['09:00', '09:45']], 'по умолчанию 8:00–8:45, 9:00–9:45');
    const l = (num, subject) => ({ date: TODAY, num, subject, marks: [], absent: false, hw: '' });
    // 2-й урок у двух групп, 3-го нет
    const day = [l(1, 'Матем.'), l(2, 'Англ. яз.'), l(2, 'Нем. яз.'), l(4, 'Физика'), { ...l(1, 'Химия'), date: '2026-10-21' }];
    const st = (hm) => RS.nowStatus(day, TODAY, RS.toMin(hm), B);
    eq(st('07:30').kind + '/' + st('07:30').reason + '/' + st('07:30').next.num, 'free/before/1');
    eq([st('08:00').kind, st('08:00').cur.num, st('08:00').next.num], ['lesson', 1, 2]);
    eq([st('08:50').kind, st('08:50').prev.num, st('08:50').next.num, st('08:50').end], ['break', 1, 2, 540]);
    eq(st('09:10').cur.lessons.map((x) => x.subject), ['Англ. яз.', 'Нем. яз.'], 'группы — в одном уроке');
    eq([st('10:20').kind, st('10:20').prev.num, st('10:20').next.num], ['break', 2, 4], 'окно вместо 3-го урока — перемена до 4-го');
    eq(st('11:44').cur.num, 4);
    eq([st('11:45').kind, st('11:45').reason, st('11:45').last.num], ['free', 'after', 4]);
    eq(RS.nowStatus([], TODAY, 600, B), { kind: 'free', reason: 'none' });
    // урок без звонка и испорченные звонки не ломают расчёт
    const bad = [['08:00', '08:45'], ['', '09:45'], ['10:00', '09:00']];
    eq(RS.daySchedule(day, TODAY, bad).map((x) => x.num), [1]);
    eq(RS.daySchedule(day, TODAY, 'мусор').map((x) => x.num), [1, 2, 4], 'без настроек — по умолчанию');
  });

  console.log('Статистика');

  await test('год целиком: все четверти по ссылкам между неделями, без ДЗ, каждая страница — один запрос', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const data = await w.RS.crawl(pupil, { today: TODAY, delay: 0 });
    eq(data.quarters.map((q) => [q.id, q.name]), [['92', '1 четверть'], ['93', '2 четверть'], ['94', '3 четверть'], ['95', '4 четверть']]);
    const q1 = data.lessons.filter((l) => l.q === '92');
    eq(q1[0].date, site.quarters[0].from, 'с первой недели четверти');
    ok(q1.every((l) => l.date <= '2026-10-24'), 'первая четверть кончается до каникул');
    ok(data.lessons.every((l) => !('hw' in l)), 'ДЗ в статистике не храним');
    ok(data.lessons.filter((l) => l.date > TODAY).every((l) => !l.marks.length), 'в будущем отметок нет');
    eq(data.errors, []);
    const weekReqs = site.requests.filter((p) => p.includes('/week/'));
    eq(weekReqs.length, new Set(weekReqs).size, 'каждая неделя — один запрос');
  });

  await test('расчёты: средние, четвертная, годовая, прогноз', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const RS = w.RS;
    const data = await RS.crawl(pupil, { today: TODAY, delay: 0 });
    const st = RS.computeStats(data, { today: TODAY });
    eq(st.current, '92');
    ok(st.inProgress['92'] && !st.inProgress['93'], 'идёт первая четверть');
    const math = st.subjects.find((s) => s.name === 'Математика');
    const vals = data.lessons.filter((l) => l.subject === 'Матем.').flatMap((l) => l.marks);
    eq(math.all.n, vals.length);
    eq(+math.all.avg.toFixed(6), +(vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(6));
    eq(math.current.rounded, RS.roundMark(math.current.avg));
    eq(math.year.terms, 1);
    eq(math.year.rounded, math.current.rounded, 'годовая по одной четверти = четвертная');
    eq(st.distribution.reduce((a, b) => a + b, 0), st.overall.marks);
    eq(st.overall.absences, data.lessons.filter((l) => l.absent && l.date <= TODAY).length);
    // прогноз: столько десяток действительно поднимают отметку, а на одну меньше — нет
    const s = st.subjects.find((x) => x.forecast && x.forecast.up.length && x.forecast.up[0].mark === 10);
    const { n, sum, rounded } = s.current;
    const k = s.forecast.up[0].count;
    eq(RS.roundMark((sum + 10 * k) / (n + k)), rounded + 1);
    if (k > 1) eq(RS.roundMark((sum + 10 * (k - 1)) / (n + k - 1)), rounded);
    // графики рисуются
    ok(RS.charts.distribution(st.distribution).startsWith('<svg'));
    ok(RS.charts.line({ series: [{ points: st.weekly.map((x) => ({ date: x.week, y: x.avg })) }] }).includes('<path'));
  });

  await test('полугодовые отметки: по умолчанию у предметов раз в неделю, настройка переключает', async () => {
    const site = createSite({ today: '2027-01-20' });
    const w = makeWindow(site);
    const RS = w.RS;
    const data = await RS.crawl(pupil, { today: site.today, delay: 0 });
    const per = RS.lessonsPerWeek(data);
    ok(Math.abs(per['черчение'] - 1) < 0.15, `черчение ≈ 1 в неделю: ${per['черчение']}`);
    ok(per['матем'] > 4, `математика ≈ 5 в неделю: ${per['матем']}`);
    let st = RS.computeStats(data, { today: site.today });
    eq(st.current, '94');
    eq(st.halves.map((h) => h.qids), [['92', '93'], ['94', '95']]);
    const geo = st.subjects.find((s) => s.name === 'География');
    const math = st.subjects.find((s) => s.name === 'Математика');
    eq([geo.period, geo.periodAuto, math.period], ['h', 'h', 'q']);
    eq(geo.terms.map((t) => t.name), ['1 полугодие', '2 полугодие']);
    eq(math.terms.length, 4);
    const marks = (s, qs) => s.marks.filter((m) => qs.includes(m.q)).map((m) => m.v);
    const h1 = marks(geo, ['92', '93']);
    eq(geo.terms[0].sum.n, h1.length, 'первое полугодие — отметки обеих четвертей');
    eq(geo.byQ['92'], geo.byQ['93'], 'одна отметка на две четверти');
    eq(geo.currentTerm.id, 'h2');
    eq(geo.current.n, marks(geo, ['94']).length);
    eq(geo.year.of, 2);
    eq(geo.year.exact, RS.mean(geo.terms.filter((t) => t.sum.n).map((t) => t.sum.rounded)), 'годовая — по полугодовым');
    eq(math.year.of, 4);
    const total = st.quarterTotals['92'];
    eq(total.marks, data.lessons.filter((l) => l.q === '92').reduce((a, l) => a + l.marks.length, 0), 'отметки четверти не задвоены');
    // скользящее среднее копится по полугодию
    const r = geo.running.filter((x) => x.q === 'h1');
    eq(+r[r.length - 1].avg.toFixed(6), +RS.mean(h1).toFixed(6));
    // настройка: география — за четверть, математика — за полугодие
    st = RS.computeStats(data, { today: site.today, periods: { 'география': 'q', 'матем': 'h' } });
    const geo2 = st.subjects.find((s) => s.name === 'География');
    const math2 = st.subjects.find((s) => s.name === 'Математика');
    eq([geo2.period, geo2.periodAuto, math2.period, math2.periodAuto], ['q', 'h', 'h', 'q']);
    eq(geo2.terms.length, 4);
    eq(geo2.current.n, marks(geo, ['94']).length);
    eq(math2.terms.length, 2);
  });

  await test('быстрое обновление — только текущая и будущие четверти; прошлые берутся из сохранённого', async () => {
    const site = createSite({ today: '2027-01-20' });
    const w = makeWindow(site);
    const RS = w.RS;
    const full = await RS.crawl(pupil, { today: site.today, delay: 0 });
    eq(full.quarters.map((q) => q.id), ['92', '93', '94', '95']);
    eq(RS.quartersToRefresh(full, site.today), ['94', '95']);
    site.requests.length = 0;
    const quick = await RS.crawl(pupil, { today: site.today, delay: 0, previous: full, onlyQuarters: RS.quartersToRefresh(full, site.today) });
    ok(!site.requests.some((p) => /quarter\/9[23]\//.test(p)), 'прошлые четверти не перекачиваются');
    eq(quick.quarters.map((q) => q.id), ['92', '93', '94', '95'], 'четверти не потерялись');
    eq(quick.lessons.length, full.lessons.length);
  });

  await test('недели, перекачанные дневником, подставляются в статистику', async () => {
    const site = createSite({ today: TODAY });
    const w = makeWindow(site);
    const RS = w.RS;
    const data = await RS.crawl(pupil, { today: TODAY, delay: 0 });
    const { prev } = site.bump();
    const d = new RS.Diary(pupil, { delay: 0 });
    await d.refresh(TODAY, null);
    const merged = RS.mergeWeeks(data, d.weeks, d.links, [...d.expanded]);
    const num = +Object.keys(site.changed).find((k) => k.startsWith(prev + '|')).split('|')[1];
    const at = (x) => x.lessons.find((l) => l.date === prev && l.num === num);
    eq(at(data).marks, [], 'до обновления отметки не было');
    eq(at(merged).marks, [10], 'новая 10 появилась');
    eq(merged.lessons.length, data.lessons.length, 'уроки не задвоились');
    ok(merged.lessons.every((l) => l.q && !('hw' in l)));
    eq(RS.mergeWeeks(data, {}, {}, []), data, 'нечего подставлять — тот же объект');
  });

  console.log(`\n${passed} прошло, ${failed} упало`);
  process.exit(failed ? 1 : 0);
})();
