---
name: material3-expressive-compose
description: Use when building, refactoring, theming, or reviewing Android Jetpack Compose UI that should follow Google's Material 3 Expressive (M3E) guidelines — app themes, color/typography/shape/motion setup, buttons, button groups, split buttons, FABs and FAB menus, toolbars, app bars, navigation bars/rails, loading and progress indicators, carousels, lists, sliders, adaptive layouts (window size classes, list-detail, NavigationSuiteScaffold), edge-to-edge, predictive back, accessibility and icons. Trigger on any mention of Material 3, Material You, M3 Expressive, MaterialTheme, MaterialExpressiveTheme or Compose UI design work on Android phones, foldables or tablets (not Wear OS).
---

# Material 3 Expressive for Jetpack Compose — strict implementation rules

Implement Android UI exactly as Google specifies in Material 3 Expressive (M3E). Follow every MUST/NEVER rule.
If a rule conflicts with a user request, do what the user asks and state the deviation in your summary.
Snapshot date: 2026-10-22. APIs on the 1.5.0 beta line can still change — verify (Section 1.3).

---

## 0. Mindset

- M3E is research-backed (46 studies, 18,000+ participants) and uses **color, shape, size, motion and
  containment** for emotion AND usability. Every expressive choice must steer attention to the most
  important element on the screen.
- Pick **one hero moment** per screen (primary action, key content, success state) and spend emphasis
  there: larger button size, emphasized type, a MaterialShapes container, a morph or a bouncy spring.
  Keep everything else baseline.
- Be restrained in dense or utilitarian flows (settings, forms, data, enterprise). Consider
  `MotionScheme.standard()` for those apps or subtrees.
- Never trade legibility, contrast, target size or reduced motion for expression.

---

## 1. Dependencies and project setup

### 1.1 Required versions (snapshot 2026-10-22)

| Artifact | Version | Why |
|---|---|---|
| `androidx.compose.material3:material3` | **1.5.0-beta02** | 1.4.0 stable does NOT publicly expose M3E APIs. |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | **1.5.0-beta02** | NavigationSuiteScaffold, versioned with material3. |
| `androidx.compose.material3.adaptive:adaptive`, `adaptive-layout`, `adaptive-navigation` | **1.3.0** | Window size classes, ListDetailPaneScaffold, SupportingPaneScaffold. |
| `androidx.compose:compose-bom` | latest stable (≥ **2026.09.00**) | Aligns Compose UI (1.12.x). Maps material3 to 1.4.0 → **override material3**. |
| `androidx.graphics:graphics-shapes` | latest (≥ 1.0.1) | `RoundedPolygon`, `Morph` for custom shapes. |
| `androidx.activity:activity-compose` | latest stable | `enableEdgeToEdge()`, predictive back. |

Toolchain: set `compileSdk` to the newest platform that Compose requires (release notes mention compileSdk
37.1 for Compose libraries), use the AGP/Kotlin versions the current Compose release requires (see the
Compose–Kotlin compatibility page), and set `targetSdk ≥ 36`.

### 1.2 Gradle (Kotlin DSL)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    compileSdk = 37            // or newer; must satisfy Compose's minCompileSdk
    defaultConfig { minSdk = 24; targetSdk = 36 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00")) // or newer
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")           // explicit override
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-beta02")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.activity:activity-compose:<latest-stable>")
    // NEVER: material-icons-core / material-icons-extended (§9); NEVER: androidx.compose.material (M2)
}
```

### 1.3 Version check procedure (MUST run before writing UI code)

1. Read `gradle/libs.versions.toml` and module build files; report the material3, BOM and adaptive versions.
2. If you have web access, open `https://developer.android.com/jetpack/androidx/releases/compose-material3`
   and `.../compose-material3-adaptive`. Use the newest **stable** version with the M3E APIs you need;
   otherwise the newest **beta/RC**. Use alpha only if the user asks.
3. Read "API Changes" between the current and target versions and adapt the code. Known 1.5.0 changes:
   `SplitButtonLayout` → `SplitButton`; `rememberModalBottomSheetState`/`rememberStandardBottomSheetState`
   → `rememberBottomSheetState`; stateless `Slider` → `SliderState`; `LocalMotionScheme` removed →
   `MaterialTheme.motionScheme`; `HorizontalCenteredHeroCarousel(maxItemWidth)` → `preferredItemWidth`;
   `PolygonShape` reshaped in beta01.
4. Run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and confirm material3
   resolves to the intended version.

### 1.4 Experimental opt-ins

- Opt in **locally** (function/file): `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` or
  `@OptIn(ExperimentalMaterial3Api::class)`, with a comment stating why. NEVER add a module-wide flag.
- Still experimental in 1.5.0-beta (verify in IDE): `MaterialShapes`, `RoundedPolygon.toShape()`,
  `LoadingIndicator`, `ContainedLoadingIndicator`, `PullToRefreshDefaults.LoadingIndicator` and its colors,
  `ButtonDefaults.contentPaddingFor` (all `ExperimentalMaterial3ExpressiveApi`); `AppBarWithSearch`
  (`ExperimentalMaterial3Api`); pane scaffold/navigator APIs may need `ExperimentalMaterial3AdaptiveApi`.
- Graduated (no opt-in): `MaterialExpressiveTheme`, `MotionScheme`, `expressiveLightColorScheme`,
  emphasized typography, expressive `Button`/`ToggleButton`, `ButtonGroup`, `SplitButton`, FAB +
  `FloatingActionButtonMenu`, floating toolbars, flexible top app bars, `FlexibleBottomAppBar`,
  `ShortNavigationBar`, `WideNavigationRail`, wavy progress indicators, expressive list items and menus.

### 1.5 Activity setup (edge-to-edge + predictive back)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()                 // MUST
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}
```

- Manifest: `android:enableOnBackInvokedCallback="true"` (default at targetSdk 36; keep it explicit).
- NEVER override `onBackPressed()` or intercept `KEYCODE_BACK`; use `BackHandler`/`PredictiveBackHandler`
  or navigation-library back handling. NEVER set `windowOptOutEdgeToEdgeEnforcement`. NEVER use Accompanist
  SystemUiController.

---

## 2. Theme

### 2.1 Theme entry point (MUST)

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> AppDarkColorScheme          // generated, see 2.3
        else -> AppLightColorScheme              // generated; or expressiveLightColorScheme() as a placeholder
    }
    val reduceMotion = rememberReduceMotion()
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
        content = content,
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
```

- MUST call `MaterialExpressiveTheme` once at the root (its motion scheme defaults to expressive). Use
  nested `MaterialTheme(...)` only to override part of the theme for a subtree.
- MUST read all values through `MaterialTheme.colorScheme/.typography/.shapes/.motionScheme`.
- `expressiveLightColorScheme()` has no dark counterpart: in dark theme use a generated `darkColorScheme`
  or `dynamicDarkColorScheme`.

### 2.2 Color roles (MUST)

| Need | Role |
|---|---|
| Most important action, active state | `primary` / `onPrimary` |
| Tonal buttons, selected chips, nav indicator | `secondaryContainer` / `onSecondaryContainer` |
| Contrasting accents, hero highlights | `tertiary`, `tertiaryContainer` |
| Prominent containers (FAB, hero cards) | `primaryContainer` / `onPrimaryContainer` |
| Backgrounds and layered surfaces | `surface`, `surfaceContainerLowest…Highest`, `surfaceBright`, `surfaceDim` |
| Text and icons on surfaces | `onSurface`, `onSurfaceVariant` |
| Borders/dividers | `outline` (important), `outlineVariant` (decorative) |
| Errors | `error`, `errorContainer`, `onError`, `onErrorContainer` |

- ALWAYS pair a container with its `on*` role. Express elevation with surface-container tones, not shadows.
- NEVER hardcode `Color(0xFF…)` outside the generated scheme file.
- Dynamic color is the default on Android 12+; brand scheme is the fallback (API < 31 or fixed-brand
  products). Harmonize custom accents with the scheme; NEVER introduce off-palette colors.

### 2.3 Generating schemes and contrast

- Generate schemes from one seed color with **Material Theme Builder** (m3.material.io/theme-builder):
  light + dark in **standard, medium and high contrast**, exported for Jetpack Compose.
- On API 34+, read `UiModeManager.getContrast()` and pick the medium/high-contrast static scheme when
  dynamic color is off.
- Dark theme: generated dark scheme only; never invert manually; no pure `#000000` surfaces unless an
  explicit AMOLED mode is required.

---

## 3. Typography

- 30 styles: 15 baseline (`displayLarge` … `labelSmall`) + 15 emphasized (`displayLargeEmphasized` …).
- **Emphasized** for selected/active states, actions/CTAs, hero headlines, editorial treatments;
  **baseline** elsewhere. NEVER make a whole screen emphasized.
- NEVER fake emphasis with `FontWeight.Bold`; NEVER hardcode `fontSize`; NEVER fix text-container heights.

```kotlin
// res/font/google_sans_flex.ttf (variable font, SIL OFL, Google Fonts); variations need API 26+
@OptIn(ExperimentalTextApi::class)
private fun brandFont(weight: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)
val Brand = FontFamily(brandFont(400), brandFont(500), brandFont(600), brandFont(700))

private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Brand),
    // … every baseline style …
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = Brand),
    // … every emphasized style …
)
// material3 1.5 also has a Typography constructor taking a default FontFamily — prefer it if present.
```

- Keep Roboto/Roboto Flex, or use a variable brand font such as **Google Sans Flex** (weight, grade,
  slant, width, roundness). A custom font MUST be set on all 30 styles.
- Use variable axes for editorial/hero text only; keep the type-scale tokens.

---

## 4. Shape

### 4.1 Corner radius scale (MUST use tokens)

| Token | dp | Typical use |
|---|---|---|
| none | 0 | full-bleed media |
| extraSmall | 4 | snackbars |
| small | 8 | chips, menus, text fields |
| medium | 12 | cards |
| large | 16 | FABs, side sheets |
| largeIncreased | 20 | expressive emphasis |
| extraLarge | 28 | dialogs, bottom sheets |
| extraLargeIncreased | 32 | hero containers |
| extraExtraLarge | 48 | very large hero surfaces |
| full | 100% | buttons, icon buttons, sliders, search bar |

- Use `MaterialTheme.shapes.*`. NEVER write ad-hoc `RoundedCornerShape(13.dp)`. Missing token → define it
  once in the theme file. Remap tokens only in `AppShapes`.

### 4.2 MaterialShapes (35 shapes) — experimental

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes is still experimental in 1.5.0-beta
@Composable
fun HeroAvatar(painter: Painter, contentDescription: String?) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()),
    )
}
```

- Shapes include Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond,
  ClamShell, Pentagon, Gem, Sunny, VerySunny, Cookie4/6/7/9/12Sided, Ghostish, Clover4Leaf/8Leaf, Burst,
  SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart.
- Use **sparingly**: avatars, hero media, empty/success states, selection indicators, loading. NEVER on
  text containers, inputs, dense rows or every grid item. Shape is not semantic — never the only signal.

### 4.3 Shape morphing

- Morph only on interaction or state change. Prefer built-ins: `ButtonDefaults.shapes()`, `ToggleButton`,
  `IconButtonDefaults.shapes()`, FAB menu, `LoadingIndicator`. Custom morphs only for hero moments:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingSelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "badgeMorph",
    )
    val shape = remember(morph) { MorphShape(morph) { progress } }
    Box(modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer))
}

private class MorphShape(private val morph: Morph, private val progress: () -> Float) : Shape {
    private val path = Path()
    private val matrix = Matrix()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.reset()
        morph.toPath(progress(), path)              // polygons are normalized to 0..1
        matrix.reset(); matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
```

Verify `Morph.toPath` / `PolygonShape` signatures in your resolved version (changed in 1.5.0-beta01).

---

## 5. Motion (spring physics)

- **expressive** (recommended default; overshoot) vs **standard** (minimal bounce; utilitarian, dense,
  reduced motion).
- Six tokens: `fast|default|slow` × `Spatial|Effects`. **Spatial** → position, size, scale, rotation,
  shape. **Effects** → color, alpha, elevation (never bounce). fast = small elements; default = sheets,
  cards, panes; slow = full-screen/hero moves.

```kotlin
val motion = MaterialTheme.motionScheme
val scale by animateFloatAsState(if (pressed) 0.96f else 1f, motion.fastSpatialSpec(), label = "scale")
val tint by animateColorAsState(
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    motion.defaultEffectsSpec(), label = "tint",
)
AnimatedVisibility(
    visible = expanded,
    enter = fadeIn(motion.fastEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
) { /* … */ }
```

- NEVER use `tween(...)`, `FastOutSlowInEasing` or raw `spring(...)` literals in UI code (only inside a
  custom `MotionScheme` in the theme file). NEVER apply spatial specs to color/alpha.
- Shared elements: `SharedTransitionLayout` + `sharedElement`/`sharedBounds` with
  `boundsTransform = { _, _ -> motion.defaultSpatialSpec() }`.
- Predictive back: navigation libraries with predictive back (Navigation Compose / Navigation 3,
  `NavigableListDetailPaneScaffold`); custom surfaces via `PredictiveBackHandler` driven by gesture progress.
- Loop animations only in loading indicators.

---

## 6. Components (M3E rules + Compose APIs)

Prefer the M3E overload (with `shapes:`/size parameters). Keep `XxxDefaults` unless the design requires otherwise.

### 6.1 Buttons (5 sizes, press morph)

- Sizes XS, S (default), M, L, XL via `ButtonDefaults.ExtraSmall/Medium/Large/ExtraLargeContainerHeight` 
  with `contentPaddingFor`, `iconSizeFor`, `iconSpacingFor`, `textStyleFor`, `shapesFor`.
- At most **one** filled `Button` per region; `FilledTonalButton`/`OutlinedButton`/`TextButton` below it.
- MUST pass `shapes = ButtonDefaults.shapes()` (or `shapesFor(size)`) for the press morph.  L/XL only for
  one hero CTA per screen.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // contentPaddingFor is experimental again in 1.5.0
@Composable
fun HeroCta(onClick: () -> Unit) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(size),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = Modifier.heightIn(size),
    ) {
        Icon(painterResource(R.drawable.ic_add_24), contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)))
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
        Text("Create", style = ButtonDefaults.textStyleFor(size))
    }
}
```

### 6.2 Toggle buttons and icon buttons

- Selection: `ToggleButton`/`FilledTonalToggleButton`/`OutlinedToggleButton`/`ElevatedToggleButton`
  (round↔square on check).
- Icon buttons: pass `shapes = IconButtonDefaults.shapes()`; use `IconButtonDefaults` size helpers for
  larger/narrow/wide variants.
- Every icon-only button MUST have a localized `contentDescription` and a `TooltipBox` + `PlainTooltip`.

### 6.3 Button groups

```kotlin
// Connected (single select) — replaces SegmentedButton
@Composable
fun ViewModeSelector(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = selected == index,
                onCheckedChange = { onSelect(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) { Text(label) }
        }
    }
}

// Standard group with overflow
ButtonGroup(overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) }) {
    clickableItem(onClick = onBold, label = "Bold")
    clickableItem(onClick = onItalic, label = "Italic")
}
```

- Standard group = related actions (pressed button widens, neighbors compress). Connected group = selection.
  DON'T mix button styles in one connected group or group unrelated actions.

### 6.4 Split button

- Primary action + menu of related variants. Use `SplitButton` (not deprecated `SplitButtonLayout`) with
  `SplitButtonDefaults.LeadingButton` and `TrailingButton(checked = menuExpanded, …)`. The trailing part
  MUST open a menu and have a `contentDescription`.

### 6.5 FAB, extended FAB, FAB menu

- FABs: `FloatingActionButton`, `MediumFloatingActionButton`, `LargeFloatingActionButton`; extended:
  `Small/Medium/LargeExtendedFloatingActionButton`. Avoid the small FAB. One FAB per screen.
- FAB menu: 2–6 related actions (replaces speed dial).

```kotlin
var expanded by rememberSaveable { mutableStateOf(false) }
BackHandler(expanded) { expanded = false }
FloatingActionButtonMenu(
    expanded = expanded,
    button = {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = { expanded = it },
            modifier = Modifier.semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                contentDescription = "Create menu"
            },
        ) {
            val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) R.drawable.ic_close_24 else R.drawable.ic_add_24 } }
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.animateIcon({ checkedProgress }))
        }
    },
) {
    FloatingActionButtonMenuItem(
        onClick = { expanded = false; onNewNote() },
        icon = { Icon(painterResource(R.drawable.ic_note_24), contentDescription = null) },
        text = { Text("Note") },
    )
}
```

- Hide on scroll with `Modifier.animateFloatingActionButton(visible, alignment)`; add bottom content
  padding so the FAB never covers the last item.

### 6.6 Toolbars and app bars

- Bottom action bar → `FlexibleBottomAppBar` (docked toolbar), not the legacy bottom app bar.
- Contextual actions → `HorizontalFloatingToolbar`/`VerticalFloatingToolbar`; FAB via
  `floatingActionButton` (`FloatingToolbarDefaults.VibrantFloatingActionButton`/`StandardFloatingActionButton`);
  vibrant colors only for hero emphasis; `FloatingToolbarDefaults.exitAlwaysScrollBehavior` +
  `nestedScroll` to hide on scroll; ≤ 5 actions.
- Top: `TopAppBar`, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` (support `subtitle`), with a
  `scrollBehavior` connected to `Modifier.nestedScroll`. Medium/large for top-level/hero pages, small for detail.
- Search: `SearchBar` + `rememberSearchBarState()` (stable). `AppBarWithSearch` is `@ExperimentalMaterial3Api`.
  Expanded: `ExpandedFullScreenContainedSearchBar` (compact), `ExpandedDockedSearchBarWithGap` (medium+).

### 6.7 Navigation

| Window width class | Component |
|---|---|
| Compact (<600dp) | `ShortNavigationBar` (3–5 destinations) |
| Medium (600–839dp) | `ShortNavigationBar` with horizontal items **or** collapsed `WideNavigationRail` |
| Expanded+ (≥840dp) | `WideNavigationRail` (collapsed/expanded); `ModalWideNavigationRail` for overflow |

- Prefer `NavigationSuiteScaffold` driven by `currentWindowAdaptiveInfo()` with the M3E navigation types.
- NEVER use the baseline tall `NavigationBar` or navigation drawers for primary navigation.
- Every item has a label; selected = filled icon, unselected = outlined icon.

### 6.8 Loading and progress

- < 5 s indeterminate: `LoadingIndicator()` / `ContainedLoadingIndicator()` (experimental).
- Pull-to-refresh: `PullToRefreshBox(…, indicator = { PullToRefreshDefaults.LoadingIndicator(state, isRefreshing, Modifier.align(Alignment.TopCenter)) })`.
- Determinate or > 5 s: `LinearWavyProgressIndicator(progress = { p })` / `CircularWavyProgressIndicator`
  (flat versions for dense/calm contexts).
- Indeterminate→determinate processes use progress indicators, not `LoadingIndicator`.  Never decorative.

### 6.9 Lists, cards, carousels, sliders, menus, sheets, dialogs

- **Lists:** expressive `ListItem`/segmented items; segmented groups instead of dividers; min height 56dp.
- **Cards:** `shapes.medium`; hero cards `largeIncreased`/`extraLarge`.
- **Carousels:** `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel`,
  `HorizontalCenteredHeroCarousel` (`preferredItemWidth`); `Modifier.maskClip(MaterialTheme.shapes.extraLarge)`;
  `CarouselDefaults.ItemSpacing`/`ContentPadding`; each item has a `contentDescription`.
- **Sliders:** stateful `SliderState`/`RangeSliderState` (check required params — changed in alpha28/29);
  `VerticalSlider`; size XS–XL by importance; semantics for the value.
- **Menus:** `SelectableDropdownMenuItem`/`CheckableDropdownMenuItem`, `MenuDefaults.DropdownMenuGroupLabel`.
- **Sheets:** `ModalBottomSheet` + `rememberBottomSheetState(...)`, top corners `extraLarge`.
- **Dialogs:** `AlertDialog`/`BasicAlertDialog`, `extraLarge`, ≤ 2 actions.
- **Chips:** shape-morphing `FilterChip`/`InputChip` overloads. **Time picker:** expressive `TimePicker`.

---

## 7. Layout and adaptive design

```kotlin
val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
val isExpanded = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
```

- Decide layout by window size class, never by device type/orientation. Widths: compact < 600,
  medium 600–839, expanded 840–1199, large 1200–1599, extra-large ≥ 1600 dp.
- Canonical layouts: list-detail → `NavigableListDetailPaneScaffold`/`ListDetailPaneScaffold` +
  `rememberListDetailPaneScaffoldNavigator()`; supporting pane → `SupportingPaneScaffold`; feed →
  `LazyVerticalGrid(GridCells.Adaptive(...))`/`LazyVerticalStaggeredGrid`. Wrap panes in `AnimatedPane`.
- 4dp grid; margins 16dp (compact) / 24dp (medium+); 24dp between panes; cap reading width (~600–840dp).
- Don't lock orientation/aspect ratio (ignored on ≥600dp for targetSdk 36).
- Insets: apply `Scaffold`'s `innerPadding` to scrolling content (`contentPadding`); use
  `consumeWindowInsets` when nesting; `WindowInsets.safeDrawing`/`imePadding()` for forms; don't double-pad
  Material components; draw backgrounds behind system bars.

---

## 8. Accessibility (MUST)

- Touch targets ≥ 48×48dp (`Modifier.minimumInteractiveComponentSize()` for custom controls).
- Contrast: role pairs only; scrims behind text on images; support medium/high contrast schemes.
- Semantics: `contentDescription` on meaningful icons/images (`null` if decorative); `role`/`stateDescription`
  on custom toggles; `mergeDescendants` on rows/cards; `heading()` on section titles; `selectableGroup()`.
- Font scaling to 200% without clipping; `sp` everywhere.
- Reduced motion: animator scale 0 → `MotionScheme.standard()`, fades instead of morphs, no non-essential loops.
- Keyboard/D-pad reachable with visible focus (inset focus rings via `LocalRippleThemeConfiguration`).
- Never encode meaning only in color, shape or motion.

---

## 9. Icons (Material Symbols)

- NEVER depend on `material-icons-core`/`material-icons-extended` or use `Icons.*`: Google says the library
  is no longer recommended, no longer updated and removed from the latest material3 release.
- Download **Material Symbols** as Vector Drawable XML (Google Fonts → Icons → Android tab, or
  `google/material-design-icons`) into `res/drawable/ic_<name>_<size>.xml`; use
  `Icon(painterResource(R.drawable.ic_name_24), contentDescription = …)`. In pure Compose apps remove
  `android:tint="?attr/colorControlNormal"` from the XML. 
- Axes are fixed at export: one **style** app-wide (Rounded pairs best with M3E); **fill** 0 unselected /
  1 selected (export both); **weight** 400 default, match adjacent text; **grade** 0, −25 on dark
  surfaces; **optical size** = rendered size (20/24/40/48dp). Don't upscale 24dp vectors by hand.

---

## 10. Forbidden patterns (NEVER)

1. material3 < 1.5.0 for M3E work, or a BOM silently downgrading material3.
2. Module-wide opt-in to `ExperimentalMaterial3ExpressiveApi`/`ExperimentalMaterial3Api`.
3. `MaterialTheme(...)` as the root theme of an M3E app, or a root theme without `motionScheme`.
4. Hardcoded colors, `Color.Black`/`Color.White` for text, alpha hacks instead of roles.
5. Ad-hoc radii, MaterialShapes on text/input containers.
6. `tween`, fixed durations, legacy easing, raw spring constants; bouncy specs on color/alpha.
7. `FontWeight.Bold` as fake emphasis; hardcoded `fontSize`; fixed-height text containers.
8. Tall `NavigationBar`, drawers for primary nav, legacy bottom app bar, `SegmentedButton`,
   `SplitButtonLayout`, small FAB, multiple FABs.
9. Indeterminate `CircularProgressIndicator` for short waits; decorative or looping animations.
10. `material-icons-*` / `Icons.*`; mixed icon styles; icon-only buttons without `contentDescription`.
11. `onBackPressed()`, `KEYCODE_BACK`, `windowOptOutEdgeToEdgeEnforcement`, SystemUiController.
12. Layout by device type/orientation; locked orientation.
13. Touch targets < 48dp; color-only state; ignoring font scale or reduced motion.
14. Mixing Material 2 and Material 3 components.
15. More than one hero moment per screen; emphasized type everywhere.

---

## 11. Pre-ship checklist (verify and report each item)

- [ ] material3 resolves to ≥ 1.5.0-beta02 (or newer stable with M3E); adaptive ≥ 1.3.0; no BOM downgrade.
- [ ] Every experimental API has a local `@OptIn` + comment; no module-wide opt-in.
- [ ] Root `MaterialExpressiveTheme`: dynamic color (API 31+) + generated light/dark (+ medium/high contrast) fallbacks; expressive motion unless reduced motion / utilitarian subtree.
- [ ] grep: no `Color(0x` or literal `RoundedCornerShape(` outside theme files, no `tween(`/`durationMillis` in UI, no `Icons.` imports, no `androidx.compose.material.` (M2) imports.
- [ ] One hero moment per screen; emphasized type only for selection/actions/headlines.
- [ ] Buttons use M3E `shapes`; one filled primary per region; ≤ 1 FAB per screen.
- [ ] Navigation via `NavigationSuiteScaffold` (or ShortNavigationBar/WideNavigationRail by size class).
- [ ] Short waits use `LoadingIndicator`; determinate work uses (wavy) progress indicators.
- [ ] `enableEdgeToEdge()`; nothing hidden by system bars, cutouts or IME in portrait, landscape, split-screen, tablet/foldable.
- [ ] Predictive back works (home, in-app, FAB menu/sheet/search); no `onBackPressed`.
- [ ] Layout verified at compact, medium, expanded widths (`@PreviewScreenSizes` or resizable emulator).
- [ ] TalkBack: name, role, state for every control; logical order; headings.
- [ ] 200% font scale and largest display size: no clipping/overlap.
- [ ] Animator duration scale 0: no bounce/morph, no non-loading loops.
- [ ] Touch targets ≥ 48dp (Accessibility Scanner / `assertTouchHeightIsAtLeast`).
- [ ] Light, dark and high-contrast themes checked in Previews.

---

## 12. References

- M3 Expressive overview and research: https://m3.material.io/blog/building-with-m3-expressive
- Motion physics in Compose: https://m3.material.io/blog/m3-expressive-motion-theming
- Motion springs/tokens: https://m3.material.io/styles/motion/overview/how-it-works
- Type scale & emphasized styles: https://m3.material.io/styles/typography/type-scale-tokens
- Shape principles & 35 shapes: https://m3.material.io/styles/shape/overview-principles
- Corner radius scale: https://m3.material.io/styles/shape/corner-radius-scale
- Loading indicator: https://m3.material.io/components/loading-indicator/overview
- Navigation bar (flexible): https://m3.material.io/components/navigation-bar
- Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- material3 package reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- MaterialShapes: https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialShapes
- FloatingActionButtonMenu: https://developer.android.com/reference/kotlin/androidx/compose/material3/FloatingActionButtonMenu.composable
- ButtonGroup: https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonGroup.composable
- HorizontalFloatingToolbar: https://developer.android.com/reference/kotlin/androidx/compose/material3/HorizontalFloatingToolbar.composable
- Compose Material 3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Compose Material 3 Adaptive release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Material icons deprecation: https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary
- Material Symbols guide: https://developers.google.com/fonts/docs/material_symbols
- Androidify (official M3E sample): https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html
