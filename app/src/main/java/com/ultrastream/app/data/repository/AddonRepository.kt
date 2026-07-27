package com.ultrastream.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ultrastream.app.data.dao.AddonDao
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.models.Extra
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.AppRefreshManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val addonDao: AddonDao,
    private val stremioApi: StremioApi,
    private val moshi: Moshi,
    private val appRefreshManager: AppRefreshManager
) {

    private val catalogListType = Types.newParameterizedType(List::class.java, Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<Catalog>>(catalogListType)

    suspend fun installAddon(url: String): Addon? {
        val manifest = try {
            stremioApi.getManifest(url)
        } catch (e: Exception) {
            android.util.Log.e("AddonRepository", "Failed to fetch manifest from $url", e)
            return null
        }
        val manifestId = manifest.id ?: "addon_${url.hashCode()}"
        val manifestName = manifest.name ?: manifestId.split(".").lastOrNull() ?: "Unnamed Addon"

        val existing = addonDao.getById(manifestId)
        if (existing != null) return existing

        val netCatalogs = manifest.catalogs ?: emptyList()
        val mappedCatalogs = netCatalogs.map { netCat ->
            Catalog(
                type = netCat.type,
                id = netCat.id,
                name = netCat.name,
                extraSupported = netCat.extraSupported,
                extra = netCat.extra?.map { extra ->
                    Extra(
                        name = extra.name,
                        isRequired = extra.isRequired,
                        options = extra.options
                    )
                }
            )
        }

        val catalogsJson = catalogAdapter.toJson(mappedCatalogs)
        val addon = Addon(
            id = manifestId,
            url = url,
            name = manifestName,
            catalogs = catalogsJson,
            enabled = true,
            required = false
        )
        addonDao.insert(addon)
        appRefreshManager.triggerRefresh()
        return addon
    }

    suspend fun getAllAddons(): List<Addon> = addonDao.getAll()

    suspend fun getEnabledAddons(): List<Addon> {
        return addonDao.getAll().filter { it.enabled }
    }

    suspend fun toggleAddon(id: String, enabled: Boolean) {
        addonDao.updateEnabled(id, enabled)
        appRefreshManager.triggerRefresh()
    }

    suspend fun removeAddon(id: String) {
        addonDao.deleteById(id)
        appRefreshManager.triggerRefresh()
    }

    suspend fun insertRawAddons(addons: List<Addon>) {
        addonDao.insertAll(addons)
    }

    suspend fun ensureDefaultAddons() {
        val enabled = getEnabledAddons()
        if (enabled.isEmpty()) {
            installAddon("https://v3-cinemeta.strem.io/manifest.json")
        }
    }
}

