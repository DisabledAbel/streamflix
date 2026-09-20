package com.streamflixreborn.streamflix

import com.streamflixreborn.streamflix.models.ContentRating
import com.streamflixreborn.streamflix.utils.ContentRatingRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ContentRatingRepositoryTest {
    @Test
    fun `provider certification has priority`() = runBlocking {
        var fallbackCalls = 0
        val result = ContentRatingRepository.providerFirst(ContentRating("PG")) {
            fallbackCalls++
            ContentRating("R")
        }
        assertEquals("PG", result?.certification)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `fallback is used when provider certification is missing`() = runBlocking {
        val result = ContentRatingRepository.providerFirst(null) { ContentRating("TV-MA") }
        assertEquals("TV-MA", result?.certification)
    }

    @Test
    fun `transient failure is not cached and can retry`() = runBlocking {
        val cache = ConcurrentHashMap<String, ContentRating?>()
        val missing = ConcurrentHashMap.newKeySet<String>()
        var attempts = 0
        val loader: suspend () -> ContentRating? = {
            attempts++
            if (attempts == 1) throw IllegalStateException("temporary")
            ContentRating("PG-13")
        }
        assertNull(ContentRatingRepository.lookup("movie", cache, missing, {}, loader))
        assertEquals("PG-13", ContentRatingRepository.lookup("movie", cache, missing, {}, loader)?.certification)
        assertEquals(2, attempts)
    }

    @Test
    fun `confirmed missing certification is safely cached`() = runBlocking {
        val cache = ConcurrentHashMap<String, ContentRating?>()
        val missing = ConcurrentHashMap.newKeySet<String>()
        var attempts = 0
        val loader: suspend () -> ContentRating? = { attempts++; null }
        assertNull(ContentRatingRepository.lookup("series", cache, missing, {}, loader))
        assertNull(ContentRatingRepository.lookup("series", cache, missing, {}, loader))
        assertEquals(1, attempts)
    }

    @Test
    fun `cancellation is propagated without being treated as a lookup failure`() = runBlocking {
        val cache = ConcurrentHashMap<String, ContentRating?>()
        val missing = ConcurrentHashMap.newKeySet<String>()
        var failureCalls = 0

        try {
            ContentRatingRepository.lookup("movie", cache, missing, { failureCalls++ }) {
                throw CancellationException("cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: structured-concurrency cancellation must reach the caller.
        }

        assertEquals(0, failureCalls)
        assertEquals(false, "movie" in missing)
    }
}
