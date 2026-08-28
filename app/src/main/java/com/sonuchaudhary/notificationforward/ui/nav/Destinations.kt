package com.sonuchaudhary.notificationforward.ui.nav

sealed class Destination(val route: String) {
    data object RoleSelection : Destination("onboarding/role_selection")
    data object Consent : Destination("onboarding/consent")
    data object PairingCode : Destination("onboarding/pairing_code")
    data object ParentSignIn : Destination("onboarding/parent_sign_in")
    data object PairingEntry : Destination("onboarding/pairing_entry")
    data object Main : Destination("main")
}
