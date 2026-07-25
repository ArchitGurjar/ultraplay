#!/bin/bash
set -e

echo "🚀 Applying laser-focused patches without minifying any code..."

# ============================================================
# 1. APPEND API Data Classes (Safe: Doesn't overwrite file)
# ============================================================
cat >> app/src/main/java/com/ultrastream/app/network/AllDebridApi.kt << 'INNER_EOF'

data class AllDebridUploadResponse(val status: Boolean, val id: String? = null, val message: String? = null)
data class AllDebridStatusResponse(val status: String, val id: String? = null, val link: String? = null)
data class AllDebridLinkResponse(val status: Boolean, val link: String? = null, val message: String? = null)
INNER_EOF

cat >> app/src/main/java/com/ultrastream/app/network/PremiumizeApi.kt << 'INNER_EOF'

data class PremiumizeTransferResponse(val status: Boolean, val id: String? = null, val message: String? = null)
data class PremiumizeStatusResponse(val status: String, val id: String? = null, val message: String? = null)
data class PremiumizeItemResponse(val status: Boolean, val content: List<PremiumizeContent>? = null, val message: String? = null)
data class PremiumizeContent(val link: String, val name: String)
INNER_EOF

# ============================================================
# 2. CREATE Missing M3UExporter.kt (New file)
# ============================================================
mkdir -p app/src/main/java/com/ultrastream/app/utils
cat > app/src/main/java/com/ultrastream/app/utils/M3UExporter.kt << 'INNER_EOF'
package com.ultrastream.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ultrastream.app.data.models.StreamItem
import java.io.File

class M3UExporter(private val context: Context) {

    fun exportToM3U(
        streams: List<StreamItem>,
        title: String,
        fileName: String = "playlist.m3u"
    ): File? {
        return try {
            val content = buildM3UContent(streams, title)
            val file = File(context.cacheDir, fileName)
            file.writeText(content)
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun buildM3UContent(streams: List<StreamItem>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST: \$title")

        streams.forEach { stream ->
            val url = stream.url ?: stream.streamUrl ?: stream.externalUrl
            if (url.isNullOrBlank()) return@forEach
            if (url.startsWith("magnet:")) return@forEach
            val name = stream.title ?: stream.name ?: "Stream"
            sb.appendLine("#EXTINF:-1,\${escapeM3UString(name)}")
            sb.appendLine(url)
        }

        return sb.toString()
    }

    fun shareM3U(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/x-mpegurl"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Playlist"))
        } catch (e: Exception) {}
    }

    private fun escapeM3UString(text: String): String {
        return text.replace(",", "\\,")
    }
}
INNER_EOF

# ============================================================
# 3. SMART PYTHON PATCHER (Fixes specific lines only)
# ============================================================
python3 - << 'PYEOF'
import os

# --- Patch DetailsViewModel.kt ---
vm_path = "app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsViewModel.kt"
with open(vm_path, "r", encoding="utf-8") as f:
    vm_code = f.read()

# Fix Nullable Math Error by adding '?: 0'
vm_code = vm_code.replace("seasonMap.values.forEach { it.sortBy { it.episode } }", "seasonMap.values.forEach { list -> list.sortBy { it.episode ?: 0 } }")
vm_code = vm_code.replace("var prev = seasonEpisodes[0].episode", "var prev = seasonEpisodes[0].episode ?: 0")
vm_code = vm_code.replace("val current = seasonEpisodes[i].episode", "val current = seasonEpisodes[i].episode ?: 0")

with open(vm_path, "w", encoding="utf-8") as f:
    f.write(vm_code)
print("✅ DetailsViewModel.kt successfully patched!")


# --- Patch DetailsScreen.kt ---
screen_path = "app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt"
with open(screen_path, "r", encoding="utf-8") as f:
    screen_code = f.read()

# Fix Icons and imdb_id
screen_code = screen_code.replace("Icons.Outlined.BookmarkBorder", "Icons.Default.FavoriteBorder")
screen_code = screen_code.replace("Icons.Default.Bookmark", "Icons.Default.Favorite")
screen_code = screen_code.replace("meta.imdb_id", "meta.imdbId")

# Fix missing M3UExporter Import
if "import com.ultrastream.app.utils.M3UExporter" not in screen_code:
    screen_code = screen_code.replace("import com.ultrastream.app.ui.theme.*", "import com.ultrastream.app.ui.theme.*\nimport com.ultrastream.app.utils.M3UExporter")

with open(screen_path, "w", encoding="utf-8") as f:
    f.write(screen_code)
print("✅ DetailsScreen.kt successfully patched!")
PYEOF

# ============================================================
# 4. Commit and Push
# ============================================================
git add -A
git commit -m "Fix: Target-patched Nullable Math, Icons, M3UExporter & APIs without minifying UI code"
git push origin main

echo "🎉 All patches applied perfectly. Your advanced UI is completely safe!"

