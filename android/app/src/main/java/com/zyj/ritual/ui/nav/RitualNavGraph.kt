package com.zyj.ritual.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zyj.ritual.RitualApp
import com.zyj.ritual.ui.screens.article.ArticleScreen
import com.zyj.ritual.ui.screens.calendar.CalendarScreen
import com.zyj.ritual.ui.screens.history.HistoryScreen
import com.zyj.ritual.ui.screens.progress.ProgressScreen
import com.zyj.ritual.ui.screens.settings.SettingsScreen
import com.zyj.ritual.ui.screens.settings.SettingsViewModel
import com.zyj.ritual.ui.screens.setup.SetupScreen
import com.zyj.ritual.ui.screens.setup.SetupViewModel
import com.zyj.ritual.ui.screens.today.TodayScreen
import com.zyj.ritual.ui.screens.today.TodayViewModel
import com.zyj.ritual.ui.state.RitualStateHost

/**
 * 导航路由常量。
 */
object Routes {
    const val SETUP = "setup"
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val PROGRESS = "progress"
    const val VOCAB = "vocab"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val ARTICLE = "article/{articleIndex}"

    fun article(articleIndex: Int) = "article/$articleIndex"
}

@Composable
fun RitualNavGraph(
    navController: NavHostController,
    app: RitualApp,
    todayViewModel: TodayViewModel,
    startDestination: String,
) {
    // 共享状态订阅一次，四个 tab 都读它
    val uiState by todayViewModel.uiState.collectAsStateWithLifecycle()
    val goToSetup = { navController.navigate(Routes.SETUP) }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SETUP) {
            val vm: SetupViewModel = viewModel(factory = viewModelFactory {
                initializer { SetupViewModel(app.repository) }
            })
            SetupScreen(
                viewModel = vm,
                onStart = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }

        // 底部 tab 首页（今天聚合页）
        composable(Routes.TODAY) {
            val vocabState by app.vocabRepository.aggregateStateFlow().collectAsStateWithLifecycle(initialValue = null)

            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                TodayScreen(
                    state = state,
                    onCheckTask = { art, task -> todayViewModel.checkTask(art, task) },
                    onUndoTask = { art, task -> todayViewModel.undoTask(art, task) },
                    onOpenArticle = {
                        navController.navigate(Routes.article(state.progress.currentArticle))
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onBackfillFirst = { todayViewModel.backfillFirst() },
                    onReschedule = { todayViewModel.reschedule() },
                    vocabState = vocabState,
                    onAddVocabWords = { words -> todayViewModel.addVocabRecord(words, "new") },
                    onAddVocabReview = { words -> todayViewModel.addVocabRecord(words, "backlog") },
                    onOpenVocabBoard = {
                        navController.navigate(Routes.VOCAB) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }

        composable(Routes.CALENDAR) {
            val vocabState by app.vocabRepository.aggregateStateFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                CalendarScreen(
                    state = state,
                    vocabState = vocabState,
                    onCheckTask = { art, task -> todayViewModel.checkTask(art, task) },
                    onUndoTask = { art, task -> todayViewModel.undoTask(art, task) },
                )
            }
        }

        composable(Routes.PROGRESS) {
            val vocabState by app.vocabRepository.aggregateStateFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                ProgressScreen(
                    state = state,
                    vocabState = vocabState,
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenVocabBoard = {
                        navController.navigate(Routes.VOCAB) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }

        // 历史是真二级页：不在 BottomNavTabs.all 里，底栏自动隐藏，
        // 跟 article/{articleIndex} 同一套（MainActivity.kt:78 判的就是那张表）
        composable(Routes.HISTORY) {
            val vocabState by app.vocabRepository.aggregateStateFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                HistoryScreen(
                    history = state.history,
                    vocabRecords = vocabState?.records.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.VOCAB) {
            val vm: com.zyj.ritual.ui.screens.vocab.VocabViewModel = viewModel(factory = viewModelFactory {
                initializer { com.zyj.ritual.ui.screens.vocab.VocabViewModel(app.vocabRepository) }
            })
            com.zyj.ritual.ui.screens.vocab.VocabCalendarScreen(
                viewModel = vm,
                onNavigateToSetup = { goToSetup() },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { SettingsViewModel(app.repository, app.backupRepository, app.clock) }
            })
            val settingsState by vm.state.collectAsStateWithLifecycle()
            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                SettingsScreen(
                    state = state,
                    settingsViewModel = vm,
                    settingsState = settingsState,
                    onReEnterSetup = { navController.navigate(Routes.SETUP) },
                    onReschedule = { vm.reschedule() },
                    onUndoLastArticle = { vm.undoLastArticle() },
                    onChangeDaysPerArticle = { days -> vm.setDaysPerArticle(days) },
                )
            }
        }

        composable(
            Routes.ARTICLE,
            arguments = listOf(navArgument("articleIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val articleIndex = backStackEntry.arguments?.getInt("articleIndex") ?: 1
            RitualStateHost(uiState = uiState, onGoToSetup = { goToSetup() }) { state ->
                ArticleScreen(
                    articleIndex = articleIndex,
                    state = state,
                    onCheckTask = { art, task -> todayViewModel.checkTask(art, task) },
                    onUndoTask = { art, task -> todayViewModel.undoTask(art, task) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
