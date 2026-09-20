package com.streamflixreborn.streamflix

import com.streamflixreborn.streamflix.models.ContentRating
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.adapters.AppAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentRatingModelTest {
    @Test
    fun `movie copy preserves official certifications`() {
        listOf("G", "PG", "PG-13", "R").forEach { certification ->
            val movie = Movie(id = "1", title = "Movie", contentRating = ContentRating(certification))
            assertEquals(certification, movie.copy().contentRating?.certification)
        }
    }

    @Test
    fun `tv show copy preserves official certifications`() {
        listOf("TV-PG", "TV-14", "TV-MA").forEach { certification ->
            val series = TvShow(id = "1", title = "Series", contentRating = ContentRating(certification))
            assertEquals(certification, series.copy().contentRating?.certification)
        }
    }

    @Test
    fun `missing certification remains missing`() {
        assertNull(Movie(id = "1", title = "Movie").copy().contentRating)
        assertNull(TvShow(id = "1", title = "Series").copy().contentRating)
    }

    @Test
    fun `certification changes model equality for DiffUtil`() {
        val unratedMovie = Movie(id = "1", title = "Movie").apply {
            itemType = AppAdapter.Type.MOVIE_MOBILE
        }
        val copiedMovie = unratedMovie.copy().apply { itemType = AppAdapter.Type.MOVIE_MOBILE }
        assertEquals(unratedMovie, copiedMovie)
        val ratedMovie = unratedMovie.copy(contentRating = ContentRating("PG-13")).apply {
            itemType = AppAdapter.Type.MOVIE_MOBILE
        }
        assertNotEquals(unratedMovie, ratedMovie)

        val unratedSeries = TvShow(id = "2", title = "Series").apply {
            itemType = AppAdapter.Type.TV_SHOW_MOBILE
        }
        val copiedSeries = unratedSeries.copy().apply { itemType = AppAdapter.Type.TV_SHOW_MOBILE }
        assertEquals(unratedSeries, copiedSeries)
        val ratedSeries = unratedSeries.copy(contentRating = ContentRating("TV-14")).apply {
            itemType = AppAdapter.Type.TV_SHOW_MOBILE
        }
        assertNotEquals(unratedSeries, ratedSeries)
    }
}
