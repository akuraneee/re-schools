package io.github.akuraneee.reschools.data

import io.github.akuraneee.reschools.core.Store
import java.io.File

/** Кэш дневника и настройки — файлы в закрытом каталоге приложения. */
class FileStore(private val dir: File) : Store {
    init { dir.mkdirs() }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    override fun get(key: String): String? = runCatching { file(key).takeIf { it.exists() }?.readText() }.getOrNull()

    // Сначала во временный файл, потом переименование: оборванная запись не испортит прежнюю.
    override fun set(key: String, value: String) {
        runCatching {
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(value)
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        }
    }

    override fun remove(key: String) { runCatching { file(key).delete() } }
}
