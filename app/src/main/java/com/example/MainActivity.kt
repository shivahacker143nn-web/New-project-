package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.configureAppCheck
import com.example.ui.AishuViewModel
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PersonalizationScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.settings.SettingsDetailScreen
import com.example.ui.screens.settings.SettingsMenuScreen
import com.example.ui.screens.settings.SettingsSection
import com.example.ui.theme.AishuTheme
import com.example.voice.WakeWordForegroundService

object AishuRoutes {
    const val ROUTE_SPLASH = "splash"
    const val ROUTE_PERMISSIONS = "permissions"
    const val ROUTE_LOGIN = "login"
    const val ROUTE_PERSONALIZATION = "personalization"
    const val ROUTE_ASSISTANT = "assistant"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_DETAIL = "settings_detail/{sectionId}"

    fun settingsDetail(sectionId: String) = "settings_detail/$sectionId"
}

class MainActivity : ComponentActivity() {

    private val viewModel: AishuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CRITICAL: Initialize App Check first before any Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        if (intent?.getBooleanExtra(WakeWordForegroundService.EXTRA_ACTIVATED_BY_WAKE_WORD, false) == true) {
            viewModel.onWakeWordActivated()
        }

        setContent {
            AishuTheme {
                AishuNavigationRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(WakeWordForegroundService.EXTRA_ACTIVATED_BY_WAKE_WORD, false)) {
            viewModel.onWakeWordActivated()
        }
    }
}

@Composable
fun AishuNavigationRoot(viewModel: AishuViewModel) {
    val navController = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    var initialRouteResolved by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsLoaded, settings.onboardingDone) {
        if (settingsLoaded && !initialRouteResolved) {
            initialRouteResolved = true
            if (settings.onboardingDone) {
                navController.navigate(AishuRoutes.ROUTE_ASSISTANT) {
                    popUpTo(AishuRoutes.ROUTE_SPLASH) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AishuRoutes.ROUTE_SPLASH,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(260))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(260))
        }
    ) {
        // Page 1: Get Started -> Permissions
        composable(AishuRoutes.ROUTE_SPLASH) {
            SplashScreen(
                onGetStarted = {
                    if (settings.onboardingDone) {
                        navController.navigate(AishuRoutes.ROUTE_ASSISTANT) {
                            popUpTo(AishuRoutes.ROUTE_SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(AishuRoutes.ROUTE_PERMISSIONS)
                    }
                }
            )
        }

        // Page 2: Permissions -> Login / Skip
        composable(AishuRoutes.ROUTE_PERMISSIONS) {
            PermissionsScreen(
                onContinue = {
                    navController.navigate(AishuRoutes.ROUTE_LOGIN)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Page 3: Login / Skip -> Personalization
        composable(AishuRoutes.ROUTE_LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onAuthenticated = {
                    val next = if (settings.onboardingDone) {
                        AishuRoutes.ROUTE_ASSISTANT
                    } else {
                        AishuRoutes.ROUTE_PERSONALIZATION
                    }
                    navController.navigate(next) {
                        if (next == AishuRoutes.ROUTE_ASSISTANT) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Page 4: Personalization -> Main AI SHU Assistant
        composable(AishuRoutes.ROUTE_PERSONALIZATION) {
            PersonalizationScreen(
                viewModel = viewModel,
                onComplete = {
                    navController.navigate(AishuRoutes.ROUTE_ASSISTANT) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Page 5: Main AI SHU Interface
        composable(AishuRoutes.ROUTE_ASSISTANT) {
            AssistantScreen(
                viewModel = viewModel,
                onOpenSettings = {
                    navController.navigate(AishuRoutes.ROUTE_SETTINGS)
                },
                onOpenApiSettings = {
                    navController.navigate(AishuRoutes.settingsDetail(SettingsSection.API_SETTINGS.id))
                }
            )
        }

        composable(AishuRoutes.ROUTE_SETTINGS) {
            SettingsMenuScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSelectSection = { section ->
                    navController.navigate(AishuRoutes.settingsDetail(section.id))
                }
            )
        }

        composable(
            route = AishuRoutes.ROUTE_SETTINGS_DETAIL,
            arguments = listOf(navArgument("sectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sectionId = backStackEntry.arguments?.getString("sectionId")
            val section = SettingsSection.entries.firstOrNull { it.id == sectionId }
                ?: SettingsSection.API_SETTINGS
            SettingsDetailScreen(
                section = section,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(AishuRoutes.ROUTE_LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
