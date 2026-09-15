package mihon.desktop.i18n

import androidx.compose.runtime.Composable

@Composable
fun recoveryText(english: String, simplified: String, traditional: String = simplified): String =
    when (LocalStrings.current) {
        SimplifiedChineseStrings -> simplified
        TraditionalChineseStrings -> traditional
        else -> english
    }
