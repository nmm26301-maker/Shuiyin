#!/usr/bin/env bash
set -euo pipefail
version=$(python3 -c "import pathlib,re; print(re.search(r\"versionName '([0-9]+[.][0-9]+[.][0-9]+)'\",pathlib.Path('app/build.gradle').read_text()).group(1))")
tag="v${version}"
file="Shuiyin-v${version}.apk"
cp apk/app-debug.apk "$file"
sha256sum "$file" > "$file.sha256"
error_log=$(mktemp)
trap 'rm -f "$error_log"' EXIT
if target=$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/$tag" --jq .target_commitish 2>"$error_log"); then
  test "$target" = "$GITHUB_SHA" || { echo 'Refusing to replace a release from another commit'; exit 1; }
  gh release upload "$tag" --repo "$GITHUB_REPOSITORY" "$file" "$file.sha256" --clobber
elif grep -q 'HTTP 404' "$error_log"; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" "$file" "$file.sha256" --target "$GITHUB_SHA" --prerelease --title "水印工坊 ${tag} 测试版" --notes "Development APK for commit $GITHUB_SHA. Automated regressions do not prove live Douyin or all-site availability."
else
  cat "$error_log" >&2
  exit 1
fi
