#!/bin/bash
OUTPUT_FILE="ultrastream_complete_project.md"
echo "# UltraStream Complete Project Export" > "$OUTPUT_FILE"
echo "Generated on: $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

find app -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.gradle" -o -name "*.properties" -o -name "*.pro" \) | while read -r file; do
    echo "### File: \`./$file\`" >> "$OUTPUT_FILE"
    echo '

