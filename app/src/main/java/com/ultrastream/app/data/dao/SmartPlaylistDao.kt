package com.ultrastream.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.ultrastream.app.data.models.SmartPlaylist

@Dao
interface SmartPlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: SmartPlaylist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<SmartPlaylist>)

    @Query("SELECT * FROM smart_playlists")
    suspend fun getAll(): List<SmartPlaylist>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    suspend fun getById(id: String): SmartPlaylist?

    @Delete
    suspend fun delete(playlist: SmartPlaylist)

    @Query("DELETE FROM smart_playlists")
    suspend fun deleteAll()
}

