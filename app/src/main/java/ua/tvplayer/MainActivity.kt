package ua.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.tvplayer.data.Item
import ua.tvplayer.ui.AppTheme
import ua.tvplayer.ui.BrowseScreen
import ua.tvplayer.ui.BrowseViewModel
import ua.tvplayer.ui.DetailsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val stack = remember { mutableStateListOf<Item>() }
    val states = rememberSaveableStateHolder()
    val browse: BrowseViewModel = viewModel()
    val current = stack.lastOrNull()

    BackHandler(enabled = current != null) { stack.removeLastOrNull() }

    // Opening a title zooms in gently from the grid; going back eases out the other way.
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val opening = targetState != null
            (fadeIn(tween(350)) + scaleIn(tween(400), initialScale = if (opening) 0.96f else 1.03f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(400), targetScale = if (opening) 1.03f else 0.96f))
        },
        contentKey = { it?.url },
        label = "page",
    ) { item ->
        if (item == null) {
            states.SaveableStateProvider("browse") {
                BrowseScreen(browse, onOpen = { stack.add(it) })
            }
        } else {
            DetailsScreen(item)
        }
    }
}
