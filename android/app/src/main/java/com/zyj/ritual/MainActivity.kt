package com.zyj.ritual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.zyj.ritual.ui.nav.BottomNavTabs
import com.zyj.ritual.ui.nav.RitualBottomNav
import com.zyj.ritual.ui.nav.RitualNavGraph
import com.zyj.ritual.ui.nav.Routes
import com.zyj.ritual.ui.screens.today.TodayViewModel
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as RitualApp

        // 启动路由：先把 DataStore 读一次，决定进设置页还是今日页。
        // 读完之前 splash 一直挂着，避免先闪一下今日页再跳向导。
        // null = 还没读出来。
        val startDestination = MutableStateFlow<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination.value == null }

        lifecycleScope.launch {
            val plan = runCatching { app.repository.getPlan() }.getOrNull()
            startDestination.value = if (plan != null) Routes.TODAY else Routes.SETUP
        }

        setContent {
            RitualTheme {
                val start by startDestination.collectAsStateWithLifecycle()
                // start 为 null 时 splash 还在盖着，这里渲染空白即可
                start?.let { RitualAppScaffold(startDestination = it, app = app) }
            }
        }
    }
}

@Composable
private fun RitualAppScaffold(startDestination: String, app: RitualApp) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 四个 tab 共用同一个 ViewModel 实例。
    // 这里在 NavHost 之外调 viewModel()，拿到的是 Activity 的 ViewModelStore，
    // 所以切 tab 不会重建、也不会出现四份并行的数据流。
    val todayViewModel: TodayViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TodayViewModel(app.repository, app.vocabRepository, app.clock) }
        }
    )

    // 底部导航只在四个 tab 页显示
    val showBottomNav = currentRoute in BottomNavTabs.all

    Scaffold(
        containerColor = RitualColors.bg,
        bottomBar = {
            if (showBottomNav) {
                RitualBottomNav(
                    currentRoute = currentRoute ?: Routes.TODAY,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            RitualNavGraph(
                navController = navController,
                app = app,
                todayViewModel = todayViewModel,
                startDestination = startDestination,
            )
        }
    }
}
