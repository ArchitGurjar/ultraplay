#!/data/data/com.termux/files/usr/bin/bash
set -e

API_FILE="app/src/main/java/com/ultrastream/app/network/StremioApi.kt"

python3 - << 'PYEOF'
with open("app/src/main/java/com/ultrastream/app/network/StremioApi.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Check if isLive is missing in Stream data class and add it safely
if "data class Stream(" in content and "isLive" not in content:
    # Find the closing parenthesis of Stream data class and insert isLive
    target = "val subtitles: List<StreamSubtitle>?"
    replacement = "val subtitles: List<StreamSubtitle>?,\n    val isLive: Boolean = false"
    content = content.replace(target, replacement, 1)
    
    with open("app/src/main/java/com/ultrastream/app/network/StremioApi.kt", "w", encoding="utf-8") as f:
        f.write(content)
    print("Added isLive property to Stream model successfully.")
else:
    print("isLive already exists or Stream model not found.")
PYEOF

