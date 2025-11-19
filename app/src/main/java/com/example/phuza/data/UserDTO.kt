package com.example.phuza.data

import com.google.gson.annotations.SerializedName // (Ananth.k 2023)
import com.google.gson.annotations.JsonAdapter // (Ananth.k 2023)

// (Ananth.k 2023)
data class UserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("favoriteDrink") val favoriteDrink: String? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("dateofbirth") val dateOfBirth: String? = null,

    @JsonAdapter(ReviewsAdapter::class)
    @SerializedName("reviews") val reviews: List<Review> = emptyList()
)

/*
 * REFERENCES
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 */
