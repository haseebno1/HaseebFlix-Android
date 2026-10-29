package com.haseebflix.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.haseebflix.app.domain.MediaType
import com.haseebflix.app.domain.TitleSummary

@Entity(tableName = "title", primaryKeys = ["type", "id"])
data class TitleEntity(
    val type: MediaType, val id: Int, val title: String, val overview: String,
    val posterPath: String?, val backdropPath: String?, val rating: Double, val votes: Int,
    val date: String, val genreIds: String, val adult: Boolean, val cachedAt: Long,
)

@Entity(tableName = "feed", primaryKeys = ["feedKey", "position"])
data class FeedEntity(val feedKey: String, val position: Int, val type: MediaType, val id: Int, val cachedAt: Long)

@Entity(tableName = "detail", primaryKeys = ["type", "id"])
data class DetailEntity(val type: MediaType, val id: Int, val json: String, val cachedAt: Long)

@Entity(tableName = "episodes", primaryKeys = ["showId", "season"])
data class EpisodeCacheEntity(val showId: Int, val season: Int, val json: String, val cachedAt: Long)

@Entity(tableName = "genre_count", primaryKeys = ["genreId"])
data class GenreCountEntity(val genreId: Int, val count: Int, val cachedAt: Long)

@Entity(tableName = "watchlist", primaryKeys = ["type", "id"])
data class WatchlistEntity(val type: MediaType, val id: Int, val addedAt: Long, val notified: Boolean = false)

@Entity(tableName = "favorite", primaryKeys = ["type", "id"])
data class FavoriteEntity(val type: MediaType, val id: Int, val addedAt: Long)

@Entity(tableName = "follow", primaryKeys = ["showId"])
data class FollowEntity(val showId: Int, val followedAt: Long, val lastNotified: String? = null)

@Entity(tableName = "progress", primaryKeys = ["type", "id", "season", "episode"])
data class ProgressEntity(
    val type: MediaType, val id: Int, val season: Int, val episode: Int,
    val positionMs: Long, val durationMs: Long, val updatedAt: Long,
)

@Entity(tableName = "search_history", primaryKeys = ["text"])
data class SearchEntity(val text: String, val searchedAt: Long)

@Entity(tableName = "notification")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,            // EPISODE | PREMIERE
    val type: MediaType, val titleId: Int, val title: String, val subtitle: String,
    val season: Int, val episode: Int, val imagePath: String?, val createdAt: Long, val isRead: Boolean = false,
)

fun TitleEntity.toDomain() = TitleSummary(
    type, id, title, overview, posterPath, backdropPath, rating, votes, date,
    genreIds.split(",").mapNotNull { it.toIntOrNull() }, adult,
)

fun TitleSummary.toEntity(now: Long = System.currentTimeMillis()) = TitleEntity(
    type, id, title, overview, posterPath, backdropPath, rating, votes, date, genreIds.joinToString(","), adult, now,
)
