package com.ultrastream.app.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.PlaylistEpisode
import com.ultrastream.app.data.models.SmartPlaylist
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.StreamRepository
import com.ultrastream.app.data.dao.SmartPlaylistDao
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.utils.LinkVerifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.work.*
import com.ultrastream.app.worker.SmartPlaylistWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateSmartPlaylistUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val moshi: Moshi
) {
    private val episodeListType = Types.newParameterizedType(List::class.java, PlaylistEpisode::class.java)
    private val episodeAdapter = moshi.adapter<List<PlaylistEpisode>>(episodeListType)

    suspend operator fun invoke(meta: MetaItem, season: Int): Boolean {
        val episodes = meta.videos?.filter { it.season == season } ?: return false
        if (episodes.isEmpty()) return false

        val playlistId = "${meta.id}_S${season}_${System.currentTimeMillis()}"
        
        val episodesToFetch = episodes.map { 
            PlaylistEpisode(
                epNum = it.episode ?: 0,
                epName = it.name ?: it.title ?: "Episode ${it.episode}",
                title = "${meta.name} - S${season}E${it.episode}",
                stream = null,
                isMissing = true
            )
        }

        val initialPlaylist = SmartPlaylist(
            id = playlistId,
            metaId = meta.id,
            metaName = meta.name,
            poster = meta.poster,
            season = season,
            addon = "SmartPlaylist",
            total = episodes.size,
            fetched = 0,
            status = "Queued",
            episodesJson = episodeAdapter.toJson(episodesToFetch)
        )
        smartPlaylistDao.insert(initialPlaylist)

        val inputData = workDataOf(
            "playlistId" to playlistId,
            "metaId" to meta.id,
            "metaType" to meta.type,
            "metaName" to meta.name,
            "season" to season,
            "episodesToFetchJson" to episodeAdapter.toJson(episodesToFetch)
        )

        val workRequest = OneTimeWorkRequestBuilder<SmartPlaylistWorker>()
            .setInputData(inputData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            playlistId,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return true
    }
}

