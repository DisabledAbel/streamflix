package com.streamflixreborn.streamflix.interfaceprofile

import android.content.Context
import android.util.Log
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.utils.ParentalControlUtils
import com.streamflixreborn.streamflix.utils.UserDataCache
import com.streamflixreborn.streamflix.utils.UserDataCache.toEpisode
import com.streamflixreborn.streamflix.utils.UserDataCache.toMovie
import com.streamflixreborn.streamflix.utils.UserDataCache.toTvShow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** The single orchestration point for provider fan-out. It never mutates currentProvider. */
class MultiProviderRepository(
    private val context: Context,
    private val profile: () -> InterfaceProfile = InterfaceProfileManager::requireActive,
    private val registry: () -> Collection<Provider> = { Provider.providers.keys },
) {
    data class ProviderFailure(val providerName: String, val cause: Throwable)
    data class Result<T>(val value: T, val failures: List<ProviderFailure> = emptyList())
    data class Library(
        val favoriteMovies: List<Movie>,
        val favoriteTvShows: List<TvShow>,
        val continueWatching: List<AppAdapter.Item>,
    )

    fun enabledProviders(): List<Provider> {
        val active = profile()
        val byName = registry().associateBy { it.name }
        return active.providerPriority.plus(active.enabledProviderNames).distinct()
            .filter(active.enabledProviderNames::contains).mapNotNull(byName::get)
            .filter { active.preferredLanguage == null || it.language == active.preferredLanguage }
    }

    suspend fun getHome(): Result<List<Category>> = aggregate { provider ->
        provider.getHome().map { category ->
            category.copy(
                name = if (enabledProviders().size > 1 && category.name.isNotBlank()) "${category.name} · ${provider.name}" else category.name,
                list = tag(category.list, provider),
            )
        }
    }.let { Result(it.value.flatten(), it.failures) }

    suspend fun search(query: String, page: Int = 1): Result<List<AppAdapter.Item>> =
        aggregate { provider -> ParentalControlUtils.filterItems(tag(provider.search(query, page), provider)) }
            .let { Result(it.value.flatten(), it.failures) }

    suspend fun getMovies(page: Int = 1): Result<List<Movie>> =
        aggregate { provider -> provider.getMovies(page).onEach { it.providerName = provider.name } }
            .let { Result(it.value.flatten(), it.failures) }

    suspend fun getTvShows(page: Int = 1): Result<List<TvShow>> =
        aggregate { provider -> provider.getTvShows(page).onEach { it.providerName = provider.name } }
            .let { Result(it.value.flatten(), it.failures) }

    fun getLibrary(): Library {
        val providers = enabledProviders()
        val movies = mutableListOf<Movie>()
        val shows = mutableListOf<TvShow>()
        val watching = mutableListOf<AppAdapter.Item>()
        providers.forEach { provider ->
            val data = UserDataCache.read(context, provider) ?: return@forEach
            movies += data.favoritesMovies.map { it.toMovie().apply { providerName = provider.name } }
            shows += data.favoritesTvShows.map { it.toTvShow().apply { providerName = provider.name } }
            watching += data.continueWatchingMovies.map { it.toMovie().apply { providerName = provider.name } }
            // Episodes retain their enclosing show's identity in the cache and are not coalesced by id.
            watching += data.continueWatchingEpisodes.map { it.toEpisode() }
        }
        return Library(
            movies.sortedByDescending { it.favoritedAtMillis ?: 0L },
            shows.sortedByDescending { it.favoritedAtMillis ?: 0L },
            watching.sortedByDescending {
                when (it) {
                    is Movie -> it.watchHistory?.lastEngagementTimeUtcMillis ?: it.lastPlayedAtMillis ?: 0L
                    else -> 0L
                }
            },
        )
    }

    fun orderedSources(items: List<com.streamflixreborn.streamflix.models.Show>) =
        MediaSourceMatcher.group(items, profile().providerPriority)

    fun safeFallback(current: com.streamflixreborn.streamflix.models.Show, candidates: List<com.streamflixreborn.streamflix.models.Show>) =
        candidates.filter { it !== current && MediaSourceMatcher.confidentlyMatches(current, it) }
            .sortedBy { candidate ->
                val name = (candidate as? Movie)?.providerName ?: (candidate as? TvShow)?.providerName
                profile().providerPriority.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
            }.firstOrNull()

    private suspend fun <T> aggregate(block: suspend (Provider) -> T): Result<List<T>> = supervisorScope {
        val responses = enabledProviders().map { provider ->
            async {
                runCatching { block(provider) }.fold(
                    onSuccess = { it to null },
                    onFailure = {
                        Log.w("MultiProvider", "${provider.name} failed", it)
                        null to ProviderFailure(provider.name, it)
                    },
                )
            }
        }.awaitAll()
        Result(responses.mapNotNull { it.first }, responses.mapNotNull { it.second })
    }

    private fun tag(items: List<AppAdapter.Item>, provider: Provider): List<AppAdapter.Item> = items.onEach {
        when (it) {
            is Movie -> it.providerName = provider.name
            is TvShow -> it.providerName = provider.name
        }
    }
}
