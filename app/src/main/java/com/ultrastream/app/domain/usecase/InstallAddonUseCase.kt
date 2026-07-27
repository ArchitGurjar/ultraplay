package com.ultrastream.app.domain.usecase

import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.repository.AddonRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallAddonUseCase @Inject constructor(
    private val addonRepository: AddonRepository
) {
    suspend operator fun invoke(rawUrl: String): Addon? {
        var safeUrl = rawUrl.trim()
        if (safeUrl.startsWith("stremio://")) {
            safeUrl = safeUrl.replace("stremio://", "https://")
        } else if (!safeUrl.startsWith("http://") && !safeUrl.startsWith("https://")) {
            safeUrl = "https://$safeUrl"
        }

        // Improved logic: find where manifest.json should be
        // Some URLs are complex (like Torrentio with filters), so we append manifest.json 
        // only if it's not already at the end of the main path segments.
        val uri = android.net.Uri.parse(safeUrl)
        val path = uri.path ?: ""
        if (!path.endsWith("manifest.json")) {
            safeUrl = if (safeUrl.endsWith("/")) "${safeUrl}manifest.json" else "$safeUrl/manifest.json"
        }
        
        return addonRepository.installAddon(safeUrl)
    }
}

