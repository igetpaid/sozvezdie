package com.sozvezdie.messenger.core.model

/**
 * Приводит введённую ссылку к короткому коду группы.
 *
 * Пользователь может вставить `@groupk7x2`, `groupk7x2` или полную ссылку
 * вида `https://sozvezdie.app/g/groupk7x2`. Все эти варианты должны
 * превратиться в один и тот же код.
 */
fun normalizeGroupLink(input: String): String {
    val text = input.trim().lowercase()
    if (text.isEmpty()) return ""

    // Берём последнее слово и последнюю часть после косой черты:
    // так от полной ссылки остаётся только код.
    val token = text
        .substringAfterLast(' ')
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
        .removePrefix("@")

    // В коде группы разрешены только латинские буквы, цифры и подчёркивание.
    return token.filter { symbol ->
        symbol in 'a'..'z' || symbol in '0'..'9' || symbol == '_'
    }
}
