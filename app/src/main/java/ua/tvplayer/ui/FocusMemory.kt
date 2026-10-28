package ua.tvplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Sends focus back to the child of a row that had it last, or else to a preferred child.
 *
 * Stands in for Compose's focusRestorer, which pins lazy items and crashes with "Release should
 * only be called once" when a row is rebuilt mid-move, as happens when switching voice. Only
 * children still on screen are offered, so a disposed item never receives focus.
 */
class FocusMemory {
    private val attached = mutableSetOf<FocusRequester>()
    private var last: FocusRequester? = null
    private var preferred: FocusRequester? = null

    internal fun attach(requester: FocusRequester, isPreferred: Boolean) {
        attached += requester
        if (isPreferred) preferred = requester else if (preferred == requester) preferred = null
    }

    internal fun detach(requester: FocusRequester) {
        attached -= requester
        if (last == requester) last = null
        if (preferred == requester) preferred = null
    }

    internal fun focused(requester: FocusRequester) {
        last = requester
    }

    /** Where focus should land when it enters the row. */
    fun target(): FocusRequester =
        last?.takeIf { it in attached } ?: preferred?.takeIf { it in attached } ?: FocusRequester.Default

    /** Forget the last focused child, e.g. after the selection changed. */
    fun reset() {
        last = null
    }
}

@Composable
fun rememberFocusMemory() = remember { FocusMemory() }

/** On the row: entering it goes to [FocusMemory.target]. */
fun Modifier.restoreFocus(memory: FocusMemory): Modifier = focusProperties { enter = { memory.target() } }

/** On each child: registers it with the row's memory while it is composed. */
@Composable
fun Modifier.rememberFocus(memory: FocusMemory, preferred: Boolean = false): Modifier {
    val requester = remember { FocusRequester() }
    DisposableEffect(memory, requester, preferred) {
        memory.attach(requester, preferred)
        onDispose { memory.detach(requester) }
    }
    return focusRequester(requester).onFocusChanged { if (it.hasFocus) memory.focused(requester) }
}
