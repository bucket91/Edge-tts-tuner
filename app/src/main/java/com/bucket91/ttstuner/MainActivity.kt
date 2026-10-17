package com.bucket91.ttstuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bucket91.ttstuner.ui.RulesScreen
import com.bucket91.ttstuner.ui.SettingsScreen
import com.bucket91.ttstuner.ui.TuneScreen
import com.bucket91.ttstuner.ui.WordsScreen
import com.bucket91.ttstuner.ui.theme.BanglaTtsTunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BanglaTtsTunerTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var tuneInputFromHistory by rememberSaveable { mutableStateOf("") }

    val navItems = listOf(
        NavigationItem("Tune", Icons.Default.Tune, "tab_tune"),
        NavigationItem("Words", Icons.Default.FormatListBulleted, "tab_words"),
        NavigationItem("Rules", Icons.Default.Gavel, "tab_rules"),
        NavigationItem("Settings", Icons.Default.Settings, "tab_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> TuneScreen(
                    initialInputText = tuneInputFromHistory,
                    onClearInitialInput = { tuneInputFromHistory = "" }
                )
                1 -> WordsScreen()
                2 -> RulesScreen()
                3 -> SettingsScreen(
                    onReopenHistory = { historyItem ->
                        tuneInputFromHistory = historyItem.input
                        selectedTabIndex = 0
                    }
                )
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)
