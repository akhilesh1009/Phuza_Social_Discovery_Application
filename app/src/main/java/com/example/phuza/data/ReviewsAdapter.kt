package com.example.phuza.data

import com.google.gson.* // (Ananth.k 2023)
import java.lang.reflect.Type // (Ananth.k 2023)

// (Ananth.k 2023)
class ReviewsAdapter : JsonDeserializer<List<Review>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        ctx: JsonDeserializationContext
    ): List<Review> {
        if (json == null || json.isJsonNull) return emptyList()
        // If server sends a proper array → parse it
        if (json.isJsonArray) {
            return json.asJsonArray.mapNotNull { el ->
                try {
                    ctx.deserialize<Review>(el, Review::class.java)
                } catch (_: Exception) {
                    null
                }
            }
        }
        return emptyList()
    }
}

/*
 * REFERENCES
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 */
