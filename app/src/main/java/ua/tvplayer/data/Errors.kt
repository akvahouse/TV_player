package ua.tvplayer.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What to tell the viewer, instead of an exception's English message. */
fun Throwable.userMessage(): String = when (this) {
    is UnknownHostException -> "Немає з'єднання з інтернетом"
    is SocketTimeoutException -> "Сайт не відповідає. Спробуємо ще раз"
    is IOException -> if (message?.startsWith("HTTP") == true) "Сайт тимчасово недоступний" else "Немає з'єднання з інтернетом"
    else -> "Не вдалося завантажити сторінку"
}
