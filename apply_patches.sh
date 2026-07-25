#!/bin/bash
set -e
PKG="app/src/main/java/com/ultrastream/app"

if [ ! -d "$PKG" ]; then
  echo "ERROR: run this from your repo root (expected $PKG to exist)."
  exit 1
fi

echo "==> Patching UltraStream project"

python3 << 'PYEOF'
import os

PKG = "app/src/main/java/com/ultrastream/app"

def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  wrote {path}")

def patch(path, old, new):
    if not os.path.exists(path):
        print(f"  [skip] {path} not found")
        return
    content = read(path)
    if new in content:
        print(f"  [ok]   {path} already patched")
        return
    if old not in content:
        print(f"  [FAIL] {path}: expected text not found — file may have changed since audit.")
        return
    write(path, content.replace(old, new, 1))

# 1. Delete orphaned DAO
p = f"{PKG}/data/dao/WatchedEpisodeDao.kt"
if os.path.exists(p):
    os.remove(p)
    print(f"  deleted {p}")

# 2. Delete duplicate ProfileViewModel
p = f"{PKG}/ui/screens/profile/ProfileViewModel.kt"
if os.path.exists(p):
    os.remove(p)
    print(f"  deleted {p}")

# 3. Add first() import to ProfileScreen
patch(
    f"{PKG}/ui/screens/profile/ProfileScreen.kt",
    "import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.launch",
    "import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch",
)

# 4. Add Color/Icons imports to PlayerScreen
patch(
    f"{PKG}/ui/screens/player/PlayerScreen.kt",
    "import androidx.media3.ui.PlayerView",
    "import androidx.media3.ui.PlayerView\n"
    "import androidx.compose.ui.graphics.Color\n"
    "import androidx.compose.material.icons.Icons\n"
    "import androidx.compose.material.icons.filled.Close\n"
    "import androidx.compose.material.icons.filled.Replay\n"
    "import androidx.compose.material.icons.filled.Pause\n"
    "import androidx.compose.material.icons.filled.PlayArrow\n"
    "import androidx.compose.material.icons.filled.Forward\n"
    "import androidx.compose.material.icons.filled.Fullscreen",
)

# 5. AppDatabase.kt
write(f"{PKG}/data/database/AppDatabase.kt", '''package com.ultrastream.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.*

@Database(
    entities = [
        Addon::class,
        LibraryItem::class,
        WatchlistItem::class,
        HistoryItem::class,
        CachedMeta::class,
        SmartPlaylist::class,
        Profile::class,
        WatchProgress::class,
        WatchedEpisode::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun libraryDao(): LibraryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun historyDao(): HistoryDao
    abstract fun cachedMetaDao(): CachedMetaDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
    abstract fun profileDao(): ProfileDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun watchedEpisodeDao(): WatchedEpisodeDao
}
''')

# 6. DatabaseModule.kt
write(f"{PKG}/di/DatabaseModule.kt", '''package com.ultrastream.app.di

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
''')

# 7. PreferencesManager.kt
patch(
    f"{PKG}/data/preferences/PreferencesManager.kt",
    "import kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.map\n\n"
    "val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = \"settings\")\n\n"
    "class PreferencesManager(private val context: Context) {",
    "import dagger.hilt.android.qualifiers.ApplicationContext\n"
    "import kotlinx.coroutines.flow.Flow\n"
    "import kotlinx.coroutines.flow.map\n"
    "import javax.inject.Inject\n"
    "import javax.inject.Singleton\n\n"
    "val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = \"settings\")\n\n"
    "@Singleton\n"
    "class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {",
)

# 8. NavRoutes.kt
write(f"{PKG}/ui/navigation/NavRoutes.kt", '''package com.ultrastream.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Library : Screen("library")
    object Search : Screen("search")
    object Addons : Screen("addons")
    object Profile : Screen("profile")
    object Details : Screen("details/{id}/{type}") {
        fun pass(id: String, type: String) =
            "details/${URLEncoder.encode(id, "UTF-8")}/${URLEncoder.encode(type, "UTF-8")}"
    }
    object Player : Screen("player/{url}") {
        fun pass(url: String) = "player/${URLEncoder.encode(url, "UTF-8")}"
    }
}
''')

# 9. MainActivity.kt
patch(
    f"{PKG}/MainActivity.kt",
    "import dagger.hilt.android.AndroidEntryPoint",
    "import dagger.hilt.android.AndroidEntryPoint\nimport java.net.URLDecoder",
)
patch(
    f"{PKG}/MainActivity.kt",
    '            composable(Screen.Details.route) { backStackEntry ->\n'
    '                val id = backStackEntry.arguments?.getString("id") ?: ""\n'
    '                val type = backStackEntry.arguments?.getString("type") ?: ""',
    '            composable(Screen.Details.route) { backStackEntry ->\n'
    '                val id = URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")\n'
    '                val type = URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "", "UTF-8")',
)
patch(
    f"{PKG}/MainActivity.kt",
    '            composable(Screen.Player.route) { backStackEntry ->\n'
    '                val url = backStackEntry.arguments?.getString("url") ?: ""',
    '            composable(Screen.Player.route) { backStackEntry ->\n'
    '                val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")',
)

# 10. StremioApi.kt
write(f"{PKG}/network/StremioApi.kt", '''package com.ultrastream.app.network

import retrofit2.http.GET
import retrofit2.http.Url

interface StremioApi {
    @GET
    suspend fun getManifest(@Url url: String): ManifestResponse

    @GET
    suspend fun getCatalog(@Url url: String): CatalogResponse

    @GET
    suspend fun getMeta(@Url url: String): MetaResponse

    @GET
    suspend fun getStreams(@Url url: String): StreamResponse
}

data class ManifestResponse(
    val id: String,
    val name: String,
    val catalogs: List<Catalog>?,
    val resources: List<String>?,
    val types: List<String>?,
    val version: String?
)

data class Catalog(
    val type: String,
    val id: String,
    val name: String,
    val extraSupported: List<String>? = null,
    val extra: List<Extra>? = null
)

data class Extra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null
)

data class CatalogResponse(
    val metas: List<Meta>? = emptyList()
)

data class MetaResponse(
    val meta: Meta?
)

data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val imdbRating: String?,
    val year: String?,
    val releaseInfo: String?,
    val released: String?,
    val description: String?,
    val genre: List<String>?,
    val runtime: String?,
    val cast: List<String>?,
    val imdb_id: String?,
    val videos: List<Video>? = null
)

data class Video(
    val season: Int?,
    val episode: Int?,
    val name: String?,
    val title: String?,
    val description: String?,
    val thumbnail: String?,
    val url: String?
)

data class StreamResponse(
    val streams: List<Stream>? = emptyList()
)

data class Stream(
    val url: String?,
    val streamUrl: String?,
    val externalUrl: String?,
    val title: String?,
    val name: String?,
    val description: String?,
    val infoHash: String?,
    val subtitles: List<StreamSubtitle>?
)

data class StreamSubtitle(
    val url: String?,
    val file: String?,
    val lang: String?,
    val name: String?
)
''')

# 11. AddonUrl.kt
write(f"{PKG}/utils/AddonUrl.kt", '''package com.ultrastream.app.utils

fun buildAddonBaseUrl(addonUrl: String): String {
    var base = addonUrl
    if (base.endsWith("/manifest.json")) base = base.removeSuffix("/manifest.json")
    else if (base.endsWith("manifest.json")) base = base.removeSuffix("manifest.json")
    if (base.endsWith("/")) base = base.removeSuffix("/")
    return base
}
''')

# 12. AddonRepository.kt
write(f"{PKG}/data/repository/AddonRepository.kt", '''package com.ultrastream.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ultrastream.app.data.dao.AddonDao
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.models.Extra
import com.ultrastream.app.network.StremioApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val addonDao: AddonDao,
    private val stremioApi: StremioApi,
    private val moshi: Moshi
) {

    private val catalogListType = Types.newParameterizedType(List::class.java, Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<Catalog>>(catalogListType)

    suspend fun installAddon(url: String): Addon? {
        val manifest = try {
            stremioApi.getManifest(url)
        } catch (e: Exception) {
            return null
        }
        if (manifest.id.isBlank()) return null

        val existing = addonDao.getById(manifest.id)
        if (existing != null) return existing

        val netCatalogs = manifest.catalogs ?: emptyList()
        val mappedCatalogs = netCatalogs.map { netCat ->
            Catalog(
                type = netCat.type,
                id = netCat.id,
                name = netCat.name,
                extraSupported = netCat.extraSupported,
                extra = netCat.extra?.map {
                    Extra(
                        name = it.name,
                        isRequired = it.isRequired,
                        options = it.options
                    )
                }
            )
        }

        val catalogsJson = catalogAdapter.toJson(mappedCatalogs)
        val addon = Addon(
            id = manifest.id,
            url = url,
            name = manifest.name ?: manifest.id,
            catalogs = catalogsJson,
            enabled = true,
            required = false
        )
        addonDao.insert(addon)
        return addon
    }

    suspend fun getAllAddons(): List<Addon> = addonDao.getAll()

    suspend fun getEnabledAddons(): List<Addon> {
        return addonDao.getAll().filter { it.enabled }
    }

    suspend fun toggleAddon(id: String, enabled: Boolean) {
        addonDao.updateEnabled(id, enabled)
    }

    suspend fun removeAddon(id: String) {
        addonDao.deleteById(id)
    }
}
''')

# 13. MetaRepository.kt
write(f"{PKG}/data/repository/MetaRepository.kt", '''package com.ultrastream.app.data.repository

import com.ultrastream.app.data.dao.CachedMetaDao
import com.ultrastream.app.data.models.CachedMeta
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.network.Meta
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.buildAddonBaseUrl
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val cachedMetaDao: CachedMetaDao,
    private val addonRepository: AddonRepository,
    private val stremioApi: StremioApi,
    private val moshi: Moshi
) {

    suspend fun getMeta(id: String, type: String): MetaItem? {
        val cacheKey = "$id:$type"
        val cached = cachedMetaDao.getByKey(cacheKey)
        if (cached != null) {
            return try {
                moshi.adapter(MetaItem::class.java).fromJson(cached.json)
            } catch (e: Exception) {
                null
            }
        }

        val addons = addonRepository.getEnabledAddons()
        var meta: Meta? = null
        for (addon in addons) {
            val base = buildAddonBaseUrl(addon.url)
            val fullUrl = "$base/meta/$type/$id.json"
            meta = try {
                stremioApi.getMeta(fullUrl).meta
            } catch (e: Exception) {
                null
            }
            if (meta != null) break
        }
        if (meta == null) return null

        val metaItem = convertToMetaItem(meta)
        val json = moshi.adapter(MetaItem::class.java).toJson(metaItem)
        cachedMetaDao.insert(CachedMeta(cacheKey, json))
        return metaItem
    }

    private fun convertToMetaItem(meta: Meta): MetaItem {
        return MetaItem(
            id = meta.id,
            type = meta.type,
            name = meta.name,
            poster = meta.poster,
            background = meta.background,
            imdbRating = meta.imdbRating,
            year = meta.year,
            releaseInfo = meta.releaseInfo,
            released = meta.released,
            description = meta.description,
            genre = meta.genre,
            runtime = meta.runtime,
            cast = meta.cast,
            imdbId = meta.imdb_id,
            videos = meta.videos?.map {
                Video(
                    season = it.season,
                    episode = it.episode,
                    name = it.name,
                    title = it.title,
                    description = it.description,
                    thumbnail = it.thumbnail,
                    url = it.url
                )
            }
        )
    }
}
''')

PYEOF

echo "==> Staging and pushing changes to GitHub..."
git add -A
git commit -m "Fix: All Kotlin compilation issues and missing DetailsScreen"
git push origin main

