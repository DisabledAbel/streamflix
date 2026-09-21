package com.streamflixreborn.streamflix.interfaceprofile

import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.format
import java.text.Normalizer

data class ProviderMediaKey(val providerName: String, val mediaType: String, val providerItemId: String)

object MediaSourceMatcher {
    fun identity(item: Show): ProviderMediaKey = ProviderMediaKey(
        providerName = when (item) { is Movie -> item.providerName; is TvShow -> item.providerName; else -> null }.orEmpty(),
        mediaType = if (item is Movie) "movie" else "tv",
        providerItemId = when (item) { is Movie -> item.id; is TvShow -> item.id },
    )

    fun confidentlyMatches(a: Show, b: Show): Boolean {
        if ((a is Movie) != (b is Movie)) return false
        val ai = (a as? Movie)?.imdbId ?: (a as? TvShow)?.imdbId
        val bi = (b as? Movie)?.imdbId ?: (b as? TvShow)?.imdbId
        if (!ai.isNullOrBlank() && !bi.isNullOrBlank()) return ai.equals(bi, true)
        val ay = (a as? Movie)?.released?.format("yyyy") ?: (a as? TvShow)?.released?.format("yyyy")
        val by = (b as? Movie)?.released?.format("yyyy") ?: (b as? TvShow)?.released?.format("yyyy")
        val at = when (a) { is Movie -> a.title; is TvShow -> a.title }
        val bt = when (b) { is Movie -> b.title; is TvShow -> b.title }
        return ay != null && ay == by && normalize(at) == normalize(bt)
    }

    fun <T : Show> group(items: List<T>, priority: List<String>): List<List<T>> {
        val groups = mutableListOf<MutableList<T>>()
        items.forEach { item ->
            val group = groups.firstOrNull { confidentlyMatches(it.first(), item) }
            if (group == null) groups += mutableListOf(item) else group += item
        }
        val rank = priority.withIndex().associate { it.value to it.index }
        return groups.map { it.sortedBy { show ->
            val provider = (show as? Movie)?.providerName ?: (show as? TvShow)?.providerName
            rank[provider] ?: Int.MAX_VALUE
        } }
    }

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}
