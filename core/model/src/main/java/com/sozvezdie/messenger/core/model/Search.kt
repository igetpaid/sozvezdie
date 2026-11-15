package com.sozvezdie.messenger.core.model

/**
 * Приводит текст к виду, удобному для поиска:
 * нижний регистр, «ё» заменяем на «е», убираем «@» и лишние пробелы.
 *
 * Символ «@» не важен: запросы «@maksim» и «maksim» должны находить одно и то же.
 */
fun normalizeSearch(text: String): String =
    text.lowercase()
        .replace('ё', 'е')
        .replace("@", "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Найденное сообщение вместе с названием чата. */
data class FoundMessage(
    val message: Message,
    val chatId: String,
    val chatTitle: String,
)
