# re:schools — заметки для Claude

Android-приложение к дневнику schools.by: «Главная» (новые отметки с прошлого захода, отметки за сегодня, ДЗ на завтра)
«Дневник» (по неделям) и «Статистика» (`core/…/Stats.kt` — порт `../schools++/src/stats.js`; четверть недели берётся из `Diary.links`, недели года подгружает `Diary.loadHistory`). Kotlin + Jetpack Compose, Material 3 Expressive. Устройство и команды — в README.md.
Пользователь общается по-русски; интерфейс, комментарии и README — на русском.

- Разбор сайта взят из `../schools++` (`src/utils.js`, `src/parser.js`) и портирован на Kotlin (`core/…/Parser.kt`, Jsoup).
  Описание ответов schools.by — `../schools++/docs/schools-by.md`. Ловушка: в ответе недели два `.db_days`,
  первый скрыт и с заниженными на 2 отметками — берём видимый.
- `../schools++/samples/` — настоящие страницы с личными данными: не копировать и не цитировать. В тестах только
  выдуманный ученик «Тестов Тест», ID 1234567, `demo.schools.by` (`core/src/testFixtures/…/FakeSite.kt`).
- Сайт schools.by недоступен из окружения Claude. Проверка — `gradle -PcoreOnly :core:test` и скриншот-тесты приложения.
- Имя каталога `re:schools` с двоеточием ломает компилятор Kotlin (двоеточие — разделитель classpath), а `./gradlew`
  не запускается. Локально собирать из копии без двоеточия в пути (`rsync` в другой каталог) и запускать Gradle оттуда.
- Android SDK локально нет (`dl.google.com` отдаёт 404 на загрузку SDK), но Maven Google и Central доступны. Чтобы всё же
  собирать и проверять приложение: «фальшивый» SDK (`sdk.dir` в `local.properties`) — `platforms/android-37/android.jar`
  из `org.robolectric:android-all` со стёртыми телами методов, `core-for-system-modules.jar` из `java.base.jmod`,
  заглушки в `build-tools/36.0.0`, JDK 21 с `jlink`. С ним работают `:app:assembleRelease` и скриншот-тесты, но сам такой SDK в репозиторий не входит
  (лежал во временном каталоге сессии); настоящий APK собирает GitHub Actions.
- compileSdk 37 обязателен: Material3 1.5.0-beta02 и OkHttp 5.5 требуют его (`checkAarMetadata`).
- Звонки — `Settings.bells` (`Bells`: начало, урок, перемена), «что сейчас» — `nowStatus` в `core/…/Now.kt`; часы в
  интерфейсе — `LocalNow` (`ui/components/Now.kt`), без них блоки не показываются (скриншот-тесты подставляют время сами).
- «Новый заход» — открытие после перерыва ≥ 15 минут (`VISIT_GAP_MS`); новые отметки сравниваются со снимком конца
  прошлого захода (`snapshot`), недели, впервые загруженные при листании, новыми не считаются.
- Стиль интерфейса — по скиллу `.claude/skills/m3e-designer`: цвета только из темы (литералы `Color(0x…)` — лишь в
  `ui/theme/Color.kt`), форма и движение только через `MaterialTheme.shapes/motionScheme`, иконки — Material Symbols
  Rounded (векторы в `res/drawable`), без `material-icons-*`, один акцент на экран.
