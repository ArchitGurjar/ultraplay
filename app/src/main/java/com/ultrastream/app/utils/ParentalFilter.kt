package com.ultrastream.app.utils

import com.ultrastream.app.data.models.MetaItem

object ParentalFilter {
    fun shouldShow(item: MetaItem, parentalControl: Boolean, rating: String): Boolean {
        if (!parentalControl) return true
        // if rating is "PG-13", allow PG and below, etc.
        // We'll use imdbRating as a proxy: if rating >= 7.0, consider mature.
        val imdb = item.imdbRating?.toDoubleOrNull() ?: 0.0
        return when (rating) {
            "G" -> true
            "PG" -> imdb < 7.0
            "PG-13" -> imdb < 7.5
            "R" -> imdb < 8.0
            "NC-17" -> imdb < 8.5
            else -> true
        }
    }
}
