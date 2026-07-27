package com.ultrastream.app.domain.usecase

import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.StreamRepository
import com.ultrastream.app.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetStreamsUseCase @Inject constructor(
    private val streamRepository: StreamRepository,
    private val addonRepository: AddonRepository,
    private val preferencesManager: PreferencesManager
) {

    // ─── Existing method returning Flow (used for incremental loading) ───

    suspend operator fun invoke(
        metaId: String,
        metaType: String,
        season: Int? = null,
        episode: Int? = null
    ): Flow<List<StreamItem>> {
        val addons = addonRepository.getEnabledAddons()
        if (addons.isEmpty()) {
            return flow { emit(emptyList()) }
        }

        val addonUrls = addons.map { it.url }
        val hindiPriority = preferencesManager.getHindiPriority().first()
        val debridKey = preferencesManager.getDebridKey().first()

        return streamRepository.getStreamsFlow(
            metaId = metaId,
            metaType = metaType,
            season = season,
            episode = episode,
            addonUrls = addonUrls,
            hindiPriority = hindiPriority,
            debridKey = if (debridKey.isNotBlank()) debridKey else null
        )
    }

    // ─── New overload with quality and language filters (returns List synchronously) ───

    suspend operator fun invoke(
        metaId: String,
        metaType: String,
        season: Int? = null,
        episode: Int? = null,
        quality: String? = null,
        language: String? = null
    ): List<StreamItem> {
        val addons = addonRepository.getEnabledAddons()
        if (addons.isEmpty()) {
            return emptyList()
        }

        val addonUrls = addons.map { it.url }
        val hindiPriority = preferencesManager.getHindiPriority().first()
        val debridKey = preferencesManager.getDebridKey().first()

        // If we have both season and episode, use the filtered version
        return if (season != null && episode != null) {
            streamRepository.getStreamsForEpisode(
                metaId = metaId,
                metaType = metaType,
                season = season,
                episode = episode,
                addonUrls = addonUrls,
                hindiPriority = hindiPriority,
                debridKey = if (debridKey.isNotBlank()) debridKey else null,
                qualityFilter = quality,
                languageFilter = language
            )
        } else {
            // Fallback to regular getStreams (without quality/language filters)
            streamRepository.getStreams(
                metaId = metaId,
                metaType = metaType,
                season = season,
                episode = episode,
                addonUrls = addonUrls,
                hindiPriority = hindiPriority,
                debridKey = if (debridKey.isNotBlank()) debridKey else null
            )
        }
    }
}