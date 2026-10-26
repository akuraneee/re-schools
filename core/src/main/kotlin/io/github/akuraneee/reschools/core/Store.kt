package io.github.akuraneee.reschools.core

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/** Хранилище «ключ — строка». На телефоне — файлы приложения, в тестах — память. */
interface Store {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

class MemoryStore : Store {
    private val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun set(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

/** Кэш в JSON. Кэш можно потерять без последствий, поэтому нечитаемая запись — просто «ничего нет». */
class Persist(@PublishedApi internal val store: Store) {
    @PublishedApi internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // даты в ключах и значениях карт верхнего уровня (понедельник → неделя)
        serializersModule = SerializersModule { contextual(LocalDateSerializer) }
    }

    inline fun <reified T> get(key: String): T? =
        store.get(key)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    inline fun <reified T> set(key: String, value: T) =
        store.set(key, json.encodeToString(value))

    fun remove(key: String) = store.remove(key)
}
