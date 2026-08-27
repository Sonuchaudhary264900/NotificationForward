package com.sonuchaudhary.notificationforward.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore

class OnboardingViewModel(private val roleStore: RoleStore) : ViewModel() {
    fun selectRole(role: DeviceRole) {
        roleStore.role = role
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(RoleStore(context.applicationContext)) }
        }
    }
}
