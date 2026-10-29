package com.haseebflix.app.util

/** Builds TMDB image URLs, stepping down one size tier in Data Saver mode. */
object TmdbImage {
    private const val BASE = "https://image.tmdb.org/t/p/"
    private val poster = listOf(185, 342, 500, 780)
    private val backdrop = listOf(300, 780, 1280)
    private val still = listOf(185, 300)
    private val profile = listOf(185)

    private fun url(path: String?, sizes: List<Int>, wanted: Int, saver: Boolean): String? {
        if (path.isNullOrBlank()) return null
        var i = sizes.indexOfFirst { it >= wanted }.let { if (it < 0) sizes.lastIndex else it }
        if (saver && i > 0) i--
        return "${BASE}w${sizes[i]}$path"
    }

    fun poster(path: String?, wanted: Int = 342, saver: Boolean = false) = url(path, poster, wanted, saver)
    fun backdrop(path: String?, wanted: Int = 780, saver: Boolean = false) = url(path, backdrop, wanted, saver)
    fun still(path: String?, wanted: Int = 300, saver: Boolean = false) = url(path, still, wanted, saver)
    fun profile(path: String?, saver: Boolean = false) = url(path, profile, 185, saver)
}
