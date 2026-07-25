package com.ultrastream.app.di

import android.content.Context
import androidx.room.Room
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ultrastream.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideAddonDao(db: AppDatabase): AddonDao = db.addonDao()
    @Provides fun provideLibraryDao(db: AppDatabase): LibraryDao = db.libraryDao()
    @Provides fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()
    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
    @Provides fun provideCachedMetaDao(db: AppDatabase): CachedMetaDao = db.cachedMetaDao()
    @Provides fun provideSmartPlaylistDao(db: AppDatabase): SmartPlaylistDao = db.smartPlaylistDao()
    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()
    @Provides fun provideWatchedEpisodeDao(db: AppDatabase): WatchedEpisodeDao = db.watchedEpisodeDao()
}

