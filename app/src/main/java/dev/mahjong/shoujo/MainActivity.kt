package dev.mahjong.shoujo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.mahjong.shoujo.ui.nav.AppNavGraph
import dev.mahjong.shoujo.ui.theme.MahjongShoujoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MahjongShoujoTheme {
                AppNavGraph()
            }
        }
    }
}
