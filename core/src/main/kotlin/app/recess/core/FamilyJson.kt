package app.recess.core

import kotlinx.serialization.json.Json

/** JSON codec for persisting [FamilyState]; tolerant of fields added or removed between versions. */
object FamilyJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(state: FamilyState): String = json.encodeToString(FamilyState.serializer(), state)

    fun decode(text: String): FamilyState = json.decodeFromString(FamilyState.serializer(), text)
}
