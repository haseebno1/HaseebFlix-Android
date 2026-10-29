package com.haseebflix.app.data.local

import androidx.room.*
import com.haseebflix.app.domain.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TitleDao {
    @Upsert abstract suspend fun upsert(list: List<TitleEntity>)
    @Upsert abstract suspend fun upsertOne(item: TitleEntity)
    @Query("SELECT * FROM title WHERE type = :type AND id = :id") abstract suspend fun get(type: MediaType, id: Int): TitleEntity?

    @Query("SELECT t.* FROM title t JOIN feed f ON f.type = t.type AND f.id = t.id WHERE f.feedKey = :key ORDER BY f.position")
    abstract fun observeFeed(key: String): Flow<List<TitleEntity>>

    @Query("SELECT MIN(cachedAt) FROM feed WHERE feedKey = :key") abstract suspend fun feedTime(key: String): Long?
    @Query("DELETE FROM feed WHERE feedKey = :key") abstract suspend fun clearFeed(key: String)
    @Upsert abstract suspend fun upsertFeed(list: List<FeedEntity>)
    @Query("DELETE FROM feed") abstract suspend fun clearAllFeeds()

    @Transaction
    open suspend fun replaceFeed(key: String, titles: List<TitleEntity>, feed: List<FeedEntity>) {
        upsert(titles); clearFeed(key); upsertFeed(feed)
    }

    /** Keeps anything the user saved, follows or has progress on. */
    @Query(
        """DELETE FROM title WHERE
        NOT EXISTS (SELECT 1 FROM watchlist w WHERE w.type = title.type AND w.id = title.id) AND
        NOT EXISTS (SELECT 1 FROM favorite f WHERE f.type = title.type AND f.id = title.id) AND
        NOT EXISTS (SELECT 1 FROM follow fo WHERE title.type = 'TV' AND fo.showId = title.id) AND
        NOT EXISTS (SELECT 1 FROM progress p WHERE p.type = title.type AND p.id = title.id)"""
    )
    abstract suspend fun purgeUnreferenced()
}

@Dao
interface DetailDao {
    @Query("SELECT * FROM detail WHERE type = :type AND id = :id") fun observe(type: MediaType, id: Int): Flow<DetailEntity?>
    @Query("SELECT * FROM detail WHERE type = :type AND id = :id") suspend fun get(type: MediaType, id: Int): DetailEntity?
    @Upsert suspend fun upsert(e: DetailEntity)
    @Query("SELECT * FROM episodes WHERE showId = :id AND season = :season") suspend fun episodes(id: Int, season: Int): EpisodeCacheEntity?
    @Upsert suspend fun upsertEpisodes(e: EpisodeCacheEntity)
    @Query("SELECT * FROM genre_count WHERE genreId = :id") suspend fun genreCount(id: Int): GenreCountEntity?
    @Upsert suspend fun upsertGenreCount(e: GenreCountEntity)
    @Query("DELETE FROM detail") suspend fun clearDetails()
    @Query("DELETE FROM episodes") suspend fun clearEpisodes()
    @Query("DELETE FROM genre_count") suspend fun clearGenreCounts()
}

@Dao
interface LibraryDao {
    @Query("SELECT t.* FROM title t JOIN watchlist w ON w.type = t.type AND w.id = t.id ORDER BY w.addedAt DESC") fun watchlist(): Flow<List<TitleEntity>>
    @Query("SELECT t.* FROM title t JOIN favorite f ON f.type = t.type AND f.id = t.id ORDER BY f.addedAt DESC") fun favorites(): Flow<List<TitleEntity>>
    @Query("SELECT t.* FROM title t JOIN follow f ON t.type = 'TV' AND t.id = f.showId ORDER BY f.followedAt DESC") fun following(): Flow<List<TitleEntity>>
    @Query("SELECT type || ':' || id FROM watchlist") fun watchlistKeys(): Flow<List<String>>
    @Query("SELECT type || ':' || id FROM favorite") fun favoriteKeys(): Flow<List<String>>
    @Query("SELECT showId FROM follow") fun followIds(): Flow<List<Int>>
    @Query("SELECT * FROM follow") suspend fun follows(): List<FollowEntity>
    @Query("SELECT * FROM watchlist") suspend fun watchlistAll(): List<WatchlistEntity>

    @Upsert suspend fun addWatch(e: WatchlistEntity)
    @Query("DELETE FROM watchlist WHERE type = :type AND id = :id") suspend fun removeWatch(type: MediaType, id: Int)
    @Query("UPDATE watchlist SET notified = 1 WHERE type = :type AND id = :id") suspend fun markWatchNotified(type: MediaType, id: Int)
    @Upsert suspend fun addFavorite(e: FavoriteEntity)
    @Query("DELETE FROM favorite WHERE type = :type AND id = :id") suspend fun removeFavorite(type: MediaType, id: Int)
    @Upsert suspend fun addFollow(e: FollowEntity)
    @Query("DELETE FROM follow WHERE showId = :id") suspend fun removeFollow(id: Int)
    @Query("UPDATE follow SET lastNotified = :key WHERE showId = :id") suspend fun setLastNotified(id: Int, key: String)

    @Upsert suspend fun saveProgress(p: ProgressEntity)
    @Query("SELECT * FROM progress WHERE type = :type AND id = :id") fun progressFor(type: MediaType, id: Int): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress p WHERE updatedAt = (SELECT MAX(updatedAt) FROM progress WHERE type = p.type AND id = p.id) ORDER BY updatedAt DESC")
    fun latestPerTitle(): Flow<List<ProgressEntity>>
    @Query("DELETE FROM progress WHERE type = :type AND id = :id") suspend fun clearProgress(type: MediaType, id: Int)
    @Query("DELETE FROM progress") suspend fun clearAllProgress()
}

@Dao
interface SearchDao {
    @Query("SELECT text FROM search_history ORDER BY searchedAt DESC LIMIT 10") fun recent(): Flow<List<String>>
    @Upsert suspend fun add(e: SearchEntity)
    @Query("DELETE FROM search_history WHERE text = :text") suspend fun remove(text: String)
    @Query("DELETE FROM search_history") suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification ORDER BY createdAt DESC") fun observe(): Flow<List<NotificationEntity>>
    @Query("SELECT COUNT(*) FROM notification WHERE isRead = 0") fun unread(): Flow<Int>
    @Insert suspend fun insert(e: NotificationEntity)
    @Query("UPDATE notification SET isRead = 1") suspend fun markAllRead()
    @Query("UPDATE notification SET isRead = 1 WHERE id = :id") suspend fun markRead(id: Long)
    @Query("DELETE FROM notification WHERE id = :id") suspend fun delete(id: Long)
}

@Database(
    entities = [
        TitleEntity::class, FeedEntity::class, DetailEntity::class, EpisodeCacheEntity::class, GenreCountEntity::class,
        WatchlistEntity::class, FavoriteEntity::class, FollowEntity::class, ProgressEntity::class,
        SearchEntity::class, NotificationEntity::class,
    ],
    version = 1, exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun titleDao(): TitleDao
    abstract fun detailDao(): DetailDao
    abstract fun libraryDao(): LibraryDao
    abstract fun searchDao(): SearchDao
    abstract fun notificationDao(): NotificationDao
}
