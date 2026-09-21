package com.streamflixreborn.streamflix.interfaceprofile

import com.streamflixreborn.streamflix.models.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceMatcherTest {
    @Test fun identicalProviderIdsDoNotCollide() {
        val first = Movie(id = "123", title = "One").apply { providerName = "A" }
        val second = Movie(id = "123", title = "Different").apply { providerName = "B" }
        assertFalse(MediaSourceMatcher.confidentlyMatches(first, second))
        assertFalse(MediaSourceMatcher.identity(first) == MediaSourceMatcher.identity(second))
    }

    @Test fun imdbIsPreferredForMatching() {
        val first = Movie(id = "a", title = "Localized title", imdbId = "tt123").apply { providerName = "slow" }
        val second = Movie(id = "b", title = "Original title", imdbId = "TT123").apply { providerName = "fast" }
        assertTrue(MediaSourceMatcher.confidentlyMatches(first, second))
        assertEquals("fast", MediaSourceMatcher.group(listOf(first, second), listOf("fast", "slow")).single().first().providerName)
    }

    @Test fun titleWithoutYearIsNotAggressivelyMatched() {
        assertFalse(MediaSourceMatcher.confidentlyMatches(Movie("1", "The Thing"), Movie("2", "The Thing")))
    }
}
