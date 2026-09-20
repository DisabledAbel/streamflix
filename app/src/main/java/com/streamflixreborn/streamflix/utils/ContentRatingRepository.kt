package com.streamflixreborn.streamflix.utils

import android.util.Log
import com.streamflixreborn.streamflix.models.ContentRating
import java.util.concurrent.ConcurrentHashMap

/** Fallback certification source for providers which do not include one in their details model. */
object ContentRatingRepository {
    private const val TAG = "ContentRatingRepository"
    private val movieCache = ConcurrentHashMap<String, ContentRating?>()
    private val seriesCache = ConcurrentHashMap<String, ContentRating?>()
    private val missingMovies = ConcurrentHashMap.newKeySet<String>()
    private val missingSeries = ConcurrentHashMap.newKeySet<String>()

    suspend fun movie(title: String, year: Int?, language: String?): ContentRating? = lookup(
        key = "${title.trim().lowercase()}|${year ?: ""}|${language.orEmpty()}",
        cache = movieCache,
        missing = missingMovies,
    ) { TmdbUtils.getMovieContentRating(title, year, language) }

    suspend fun series(title: String, year: Int?, language: String?): ContentRating? = lookup(
        key = "${title.trim().lowercase()}|${year ?: ""}|${language.orEmpty()}",
        cache = seriesCache,
        missing = missingSeries,
    ) { TmdbUtils.getTvShowContentRating(title, year, language) }

    suspend fun providerFirst(
        providerRating: ContentRating?,
        fallback: suspend () -> ContentRating?,
    ): ContentRating? = providerRating ?: fallback()

    internal suspend fun lookup(
        key: String,
        cache: ConcurrentHashMap<String, ContentRating?>,
        missing: MutableSet<String>,
        onFailure: (Exception) -> Unit = { error ->
            Log.w(TAG, "Certification lookup failed for $key; it may be retried", error)
        },
        loader: suspend () -> ContentRating?,
    ): ContentRating? {
        cache[key]?.let { return it }
        if (key in missing) return null
        return try {
            loader().also { rating ->
                if (rating == null) missing += key else cache[key] = rating
            }
        } catch (error: Exception) {
            // Do not turn a temporary API/transport/parsing failure into a session-long miss.
            onFailure(error)
            null
        }
    }
}
