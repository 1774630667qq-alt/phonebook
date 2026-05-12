package com.phonebook.senior.util

import org.json.JSONArray

object MediaUriStore {

    fun encode(uris: List<String>): String {
        val array = JSONArray()
        uris.filter { it.isNotBlank() }.distinct().forEach { array.put(it) }
        return array.toString()
    }

    fun decode(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyList()

        if (trimmed.startsWith("[")) {
            return runCatching {
                val array = JSONArray(trimmed)
                List(array.length()) { index -> array.optString(index) }
                    .filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }

        return trimmed.lines()
            .flatMap { it.split("|") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
