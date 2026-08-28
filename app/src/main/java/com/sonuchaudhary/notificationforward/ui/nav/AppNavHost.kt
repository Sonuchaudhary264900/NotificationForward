package com.sonuchaudhary.notificationforward.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonuchaudhary.notificationforward.MainScreen
import com.sonuchaudhary.notificationforward.data.FamilyRepository
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import com.sonuchaudhary.notificationforward.ui.onboarding.RoleSelectionScreen
import com.sonuchaudhary.notificationforward.ui.pairing.ConsentScreen
import com.sonuchaudhary.notificationforward.ui.pairing.PairingCodeScreen
import com.sonuchaudhary.notificationforward.ui.pairing.PairingEntryScreen
import com.sonuchaudhary.notificationforward.ui.pairing.PairingViewModel
import com.sonuchaudhary.notificationforward.ui.pairing.ParentSignInScreen

/**
 * Flow-control navigation only (Onboarding graph vs. Main graph). The existing bottom-tab
 * switching inside MainScreen is untouched and stays a plain `when` dispatch — it's still the
 * right fit for a small fixed set of sibling screens.
 */
@Composable
fun AppNavHost(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val roleStore = remember { RoleStore(context) }
    val navController = rememberNavController()

    val startRoute = remember {
        val parentSignedIn = FirebaseModule.auth.currentUser?.isAnonymous == false
        when {
            roleStore.isPaired || roleStore.pairingSkipped -> Destination.Main.route
            roleStore.role == DeviceRole.CHILD && !roleStore.hasConsented -> Destination.Consent.route
            roleStore.role == DeviceRole.CHILD -> Destination.PairingCode.route
            roleStore.role == DeviceRole.PARENT && !parentSignedIn -> Destination.ParentSignIn.route
            roleStore.role == DeviceRole.PARENT -> Destination.PairingEntry.route
            else -> Destination.RoleSelection.route
        }
    }

    val resetToRoleSelection: () -> Unit = {
        roleStore.reset()
        navController.navigate(Destination.RoleSelection.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Destination.RoleSelection.route) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    roleStore.role = role
                    val next = when (role) {
                        DeviceRole.CHILD -> Destination.Consent.route
                        DeviceRole.PARENT -> Destination.ParentSignIn.route
                        DeviceRole.NONE -> Destination.RoleSelection.route
                    }
                    navController.navigate(next) {
                        popUpTo(Destination.RoleSelection.route) { inclusive = true }
                    }
                },
                onSkip = {
                    roleStore.pairingSkipped = true
                    navController.navigate(Destination.Main.route) {
                        popUpTo(Destination.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Destination.Consent.route) {
            ConsentScreen(
                onAccept = {
                    roleStore.consentAcceptedAt = System.currentTimeMillis()
                    navController.navigate(Destination.PairingCode.route) {
                        popUpTo(Destination.Consent.route) { inclusive = true }
                    }
                },
                onBack = resetToRoleSelection
            )
        }

        composable(Destination.ParentSignIn.route) {
            ParentSignInScreen(
                onSignedIn = {
                    navController.navigate(Destination.PairingEntry.route) {
                        popUpTo(Destination.ParentSignIn.route) { inclusive = true }
                    }
                },
                onBack = resetToRoleSelection
            )
        }

        composable(Destination.PairingCode.route) {
            val pairingViewModel: PairingViewModel = viewModel(factory = PairingViewModel.factory(context))
            PairingCodeScreen(
                viewModel = pairingViewModel,
                onPaired = {
                    navController.navigate(Destination.Main.route) {
                        popUpTo(navController.graph.findStartDestination().route ?: Destination.RoleSelection.route) {
                            inclusive = true
                        }
                    }
                },
                onBack = resetToRoleSelection
            )
        }

        composable(Destination.PairingEntry.route) {
            val pairingViewModel: PairingViewModel = viewModel(factory = PairingViewModel.factory(context))
            PairingEntryScreen(
                viewModel = pairingViewModel,
                onPaired = {
                    navController.navigate(Destination.Main.route) {
                        popUpTo(navController.graph.findStartDestination().route ?: Destination.RoleSelection.route) {
                            inclusive = true
                        }
                    }
                },
                onBack = resetToRoleSelection
            )
        }

        composable(Destination.Main.route) {
            val familyId = roleStore.familyId
            if (roleStore.isPaired && familyId != null) {
                val syncScope = rememberCoroutineScope()
                DisposableEffect(familyId) {
                    val familyRepository = FamilyRepository(context)
                    familyRepository.startSyncingDevices(familyId, syncScope)
                    onDispose { familyRepository.stopSyncingDevices() }
                }
            }
            MainScreen(settingsStore = settingsStore)
        }
    }
}
