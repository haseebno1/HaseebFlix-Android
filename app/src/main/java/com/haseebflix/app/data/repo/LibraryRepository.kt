package com.haseebflix.app.data.repo

import com.haseebflix.app.data.local.*
import com.haseebflix.app.domain.ContinueItem
import com.haseebflix.app.domain.MediaType
import com.haseebflix.app.domain.TitleSummary
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(private val db: AppDatabase) {
    private val dao get() = db.libraryDao()
    private fun now() = System.currentTimeMillis()

    val watchlist: Flow<List<TitleSummary>> = db.libraryDao().watchlist().map { l -> l.map { it.toDomain() } }
    val favorites: Flow<List<TitleSummary>> = db.libraryDao().favorites().map { l -> l.map { it.toDomain() } }
    val following: Flow<List<TitleSummary>> = db.libraryDao().following().map { l -> l.map { it.toDomain() } }
    val watchlistKeys: Flow<Set<String>> = db.libraryDao().watchlistKeys().map { it.toSet() }
    val favoriteKeys: Flow<Set<String>> = db.libraryDao().favoriteKeys().map { it.toSet() }
    val followIds: Flow<Set<Int>> = db.libraryDao().followIds().map { it.toSet() }

    /** Latest progress row per title, joined to its cached summary. */
    val history: Flow<List<ContinueItem>> = db.libraryDao().latestPerTitle().map { rows ->
        rows.mapNotNull { p ->
            db.titleDao().get(p.type, p.id)?.let {
                ContinueItem(it.toDomain(), p.season, p.episode, p.positionMs, p.durationMs, p.updatedAt)
            }
        }
    }
    val continueWatching: Flow<List<ContinueItem>> = history.map { l -> l.filter { it.durationMs > 0 && it.fraction < 0.95f } }

    fun progressFor(type: MediaType, id: Int): Flow<List<ProgressEntity>> = dao.progressFor(type, id)

    suspend fun toggleWatchlist(t: TitleSummary) {
        if (dao.watchlistKeys().first().contains(t.key)) dao.removeWatch(t.type, t.id)
        else { db.titleDao().upsertOne(t.toEntity()); dao.addWatch(WatchlistEntity(t.type, t.id, now())) }
    }
    suspend fun removeFromWatchlist(items: Collection<TitleSummary>) = items.forEach { dao.removeWatch(it.type, it.id) }

    suspend fun toggleFavorite(t: TitleSummary) {
        if (dao.favoriteKeys().first().contains(t.key)) dao.removeFavorite(t.type, t.id)
        else { db.titleDao().upsertOne(t.toEntity()); dao.addFavorite(FavoriteEntity(t.type, t.id, now())) }
    }

    suspend fun toggleFollow(t: TitleSummary) {
        if (dao.followIds().first().contains(t.id)) dao.removeFollow(t.id)
        else { db.titleDao().upsertOne(t.toEntity()); dao.addFollow(FollowEntity(t.id, now())) }
    }

    suspend fun savePlayback(t: TitleSummary, season: Int, episode: Int, positionMs: Long, durationMs: Long) {
        if (db.titleDao().get(t.type, t.id) == null) db.titleDao().upsertOne(t.toEntity())
        dao.saveProgress(ProgressEntity(t.type, t.id, season, episode, positionMs, durationMs, now()))
    }

    suspend fun removeFromHistory(t: TitleSummary) = dao.clearProgress(t.type, t.id)
    suspend fun clearHistory() = dao.clearAllProgress()

    // notifications feed
    val notifications: Flow<List<NotificationEntity>> = db.notificationDao().observe()
    val unreadCount: Flow<Int> = db.notificationDao().unread()
    suspend fun markAllRead() = db.notificationDao().markAllRead()
    suspend fun markRead(id: Long) = db.notificationDao().markRead(id)
    suspend fun deleteNotification(id: Long) = db.notificationDao().delete(id)
}
