package io.github.akuraneee.reschools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import io.github.akuraneee.reschools.AppViewModel
import io.github.akuraneee.reschools.BuildConfig
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.ui.components.ProvideNow
import io.github.akuraneee.reschools.ui.diary.DiaryScreen
import io.github.akuraneee.reschools.ui.home.HomeScreen
import io.github.akuraneee.reschools.ui.settings.SettingsScreen
import io.github.akuraneee.reschools.ui.stats.StatsScreen
import io.github.akuraneee.reschools.ui.login.LoginScreen
import io.github.akuraneee.reschools.ui.web.WebScreen
import io.github.akuraneee.reschools.ui.welcome.WelcomeScreen

private object Route {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val LOGIN_WEB = "login-web"
    const val SITE = "site"
}

private enum class Tab { Home, Diary, Stats }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppStopped() }

    // часы для «что идёт сейчас» на главной и в дневнике
    ProvideNow { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (!state.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Surface
        }
        val motion = MaterialTheme.motionScheme
        val spatial = motion.defaultSpatialSpec<IntOffset>()
        val effects = motion.defaultEffectsSpec<Float>()
        // Экраны второго уровня выезжают сбоку, главный немного уходит влево; пружины — из темы
        NavHost(
            navController = nav,
            startDestination = Route.MAIN,
            enterTransition = { slideInHorizontally(spatial) { it / 4 } + fadeIn(effects) },
            exitTransition = { slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects) },
            popEnterTransition = { slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects) },
            popExitTransition = { slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects) },
        ) {
            composable(Route.MAIN) {
                MainScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onOpenWeek = viewModel::openWeek,
                    onLoadHistory = viewModel::loadHistory,
                    onOpenSettings = { nav.navigate(Route.SETTINGS) },
                    onLogin = { nav.navigate(Route.LOGIN) },
                )
            }
            composable(Route.SETTINGS) {
                SettingsScreen(
                    state = state,
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = { nav.popBackStack() },
                    onSelectPupil = viewModel::selectPupil,
                    onHomeworkMode = viewModel::setHomeworkMode,
                    onThreshold = viewModel::setThreshold,
                    onBells = viewModel::setBells,
                    onOpenSite = { nav.navigate(Route.SITE) },
                    onOtherAccount = {
                        viewModel.forgetSiteLogin()
                        nav.navigate(Route.LOGIN)
                    },
                    onClearData = viewModel::clearData,
                )
            }
            composable(Route.LOGIN) {
                LoginScreen(
                    signingIn = state.signingIn,
                    error = state.loginError,
                    onSignIn = { user, password ->
                        viewModel.signIn(
                            user, password,
                            onDone = { nav.popBackStack(Route.MAIN, inclusive = false) },
                            onNeedsBrowser = { nav.navigate(Route.LOGIN_WEB) },
                        )
                    },
                    onEdit = { if (state.loginError != null) viewModel.clearLoginError() },
                    onViaSite = { nav.navigate(Route.LOGIN_WEB) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Route.LOGIN_WEB) {
                WebScreen(
                    loginMode = true,
                    startUrl = null,
                    onBack = { nav.popBackStack() },
                    onLanded = { url ->
                        viewModel.completeLogin(url)
                        nav.popBackStack(Route.MAIN, inclusive = false)
                    },
                )
            }
            composable(Route.SITE) {
                WebScreen(
                    loginMode = false,
                    startUrl = state.pupil?.let { "${it.origin}/pupil/${it.id}" },
                    onBack = { nav.popBackStack() },
                    onLanded = {},
                )
            }
        }
    } }
}

/** «Главная», «Дневник» и «Статистика» под адаптивной навигацией: нижняя панель на телефоне, боковая — на планшете. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // ShortNavigationBar / WideNavigationRail в NavigationSuiteScaffold — экспериментальные в 1.5.0-beta
@Composable
internal fun MainScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenWeek: (java.time.LocalDate, Boolean) -> Unit,
    onLoadHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
) {
    if (state.needLogin) {
        WelcomeScreen(state, onLogin = onLogin)
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // «Назад» из дневника ведёт на главную, с главной — сворачивает приложение
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    // Тип навигации — по ширине окна, а не по типу устройства
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val navType = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> NavigationSuiteType.WideNavigationRailCollapsed
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> NavigationSuiteType.ShortNavigationBarMedium
        else -> NavigationSuiteType.ShortNavigationBarCompact
    }
    // Панель навигации сама учитывает системные отступы со своей стороны; содержимому остаются остальные.
    val contentInsets = if (navType == NavigationSuiteType.WideNavigationRailCollapsed) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)
    } else {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }

    NavigationSuiteScaffold(
        layoutType = navType,
        navigationSuiteItems = {
            Tab.entries.forEach { entry ->
                val selected = tab == entry
                val (label, icon, iconFilled) = when (entry) {
                    Tab.Home -> Triple(R.string.nav_home, R.drawable.ic_home_24, R.drawable.ic_home_fill_24)
                    Tab.Diary -> Triple(R.string.nav_diary, R.drawable.ic_menu_book_24, R.drawable.ic_menu_book_fill_24)
                    Tab.Stats -> Triple(R.string.nav_stats, R.drawable.ic_bar_chart_24, R.drawable.ic_bar_chart_24)
                }
                item(
                    selected = selected,
                    onClick = { tab = entry },
                    // выбранная вкладка — с заливкой, остальные — контурные
                    icon = { Icon(painterResource(if (selected) iconFilled else icon), contentDescription = null) },
                    label = { Text(stringResource(label)) },
                )
            }
        },
    ) {
        when (tab) {
            Tab.Home -> HomeScreen(
                state = state,
                contentInsets = contentInsets,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onLogin = onLogin,
            )
            Tab.Diary -> DiaryScreen(
                state = state,
                contentInsets = contentInsets,
                onOpenWeek = onOpenWeek,
                onLogin = onLogin,
            )
            Tab.Stats -> StatsScreen(
                state = state,
                contentInsets = contentInsets,
                onLoadHistory = onLoadHistory,
                onRetry = onRefresh,
                onLogin = onLogin,
            )
        }
    }
}
