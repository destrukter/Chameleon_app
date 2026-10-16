package com.destrukter.chameleon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.destrukter.chameleon.ui.ChameleonTheme
import com.destrukter.chameleon.ui.LocalCategoryScreen
import com.destrukter.chameleon.ui.LocalOverviewScreen
import com.destrukter.chameleon.ui.LocalPassScreen
import com.destrukter.chameleon.ui.LocalSetupScreen
import com.destrukter.chameleon.ui.MenuScreen
import com.destrukter.chameleon.ui.OnlineHomeScreen
import com.destrukter.chameleon.ui.OnlineSessionScreen
import com.destrukter.chameleon.ui.RulesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChameleonTheme {
                ChameleonApp(onExit = ::finish)
            }
        }
    }
}

@Composable
private fun ChameleonApp(onExit: () -> Unit, vm: AppViewModel = viewModel()) {
    BackHandler {
        if (!vm.back()) onExit()
    }
    val back = { vm.back(); Unit }
    when (vm.screen) {
        Screen.Menu -> MenuScreen(
            onLocal = { vm.navigate(Screen.LocalSetup) },
            onOnline = { vm.navigate(Screen.OnlineHome) },
            onRules = { vm.navigate(Screen.Rules) },
        )
        Screen.Rules -> RulesScreen(onBack = back)
        Screen.LocalSetup -> LocalSetupScreen(vm.local, vm.packs, onBack = back)
        Screen.LocalCategory -> LocalCategoryScreen(vm.local, onBack = back)
        Screen.LocalPass -> LocalPassScreen(vm.local, onBack = back)
        Screen.LocalOverview -> LocalOverviewScreen(vm.local, onMenu = { vm.navigate(Screen.Menu) })
        Screen.OnlineHome -> OnlineHomeScreen(vm.online, onBack = back)
        Screen.OnlineSession -> OnlineSessionScreen(vm.online)
    }
}
