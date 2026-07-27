package com.ultrastream.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ultrastream.app.data.dao.SmartPlaylistDao
import com.ultrastream.app.data.models.PlaylistEpisode
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.StreamRepository
import com.ultrastream.app.utils.LinkVerifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import com.ultrastream.app.data.preferences.PreferencesManager

@HiltWorker
class SmartPlaylistWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val streamRepository: StreamRepository,
    private val addonRepository: AddonRepository,
    private val preferencesManager: PreferencesManager,
    private val linkVerifier: LinkVerifier,
    private val moshi: Moshi
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val playlistId = inputData.getString("playlistId") ?: return Result.failure()
        val metaId = inputData.getString("metaId") ?: return Result.failure()
        val metaType = inputData.getString("metaType") ?: return Result.failure()
        val metaName = inputData.getString("metaName") ?: ""
        val season = inputData.getInt("season", 1)

        val episodesJson = inputData.getString("episodesToFetchJson") ?: return Result.failure()
        val listType = Types.newParameterizedType(List::class.java, PlaylistEpisode::class.java)
        val epAdapter = moshi.adapter<List<PlaylistEpisode>>(listType)
        val episodesToFetch = epAdapter.fromJson(episodesJson) ?: return Result.failure()

        val addonUrls = addonRepository.getEnabledAddons().map { it.url }
        val hindiPriority = preferencesManager.getHindiPriority().firstOrNull() ?: true
        val debridKey = preferencesManager.getDebridKey().firstOrNull() ?: ""

        val fetchedEpisodes = mutableListOf<PlaylistEpisode>()
        
        try {
            for (index in episodesToFetch.indices) {
                val ep = episodesToFetch[index]
                val epNum = ep.epNum
                
                val streams = streamRepository.getStreams(
                    metaId,
                    metaType,
                    season,
                    epNum,
                    addonUrls,
                    hindiPriority,
                    debridKey.takeIf { it.isNotBlank() }
                )

                var bestWorkingStream: StreamItem? = null
                for (stream in streams) {
                    val sUrl = stream.url ?: stream.streamUrl ?: stream.externalUrl
                    if (sUrl != null && !sUrl.startsWith("magnet:")) {
                        if (linkVerifier.verifyLink(sUrl)) {
                            bestWorkingStream = stream
                            break
                        }
                    }
                }

                fetchedEpisodes.add(
                    PlaylistEpisode(
                        epNum = epNum,
                        epName = ep.epName,
                        title = "${metaName} - S${season}E${epNum}",
                        stream = bestWorkingStream,
                        isMissing = bestWorkingStream == null
                    )
                )

                smartPlaylistDao.updatePlaylist(
                    id = playlistId,
                    fetched = index + 1,
                    status = if (index + 1 == episodesToFetch.size) "Ready" else "Fetching...",
                    episodesJson = epAdapter.toJson(fetchedEpisodes)
                )
            }
            return Result.success()
        } catch (e: Exception) {
            smartPlaylistDao.updateStatus(playlistId, "Failed: ${e.message}")
            return Result.retry()
        }
    }
}
