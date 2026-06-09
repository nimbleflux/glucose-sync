#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

if [ -n "$(git status --porcelain)" ]; then
  echo "Error: Working tree is not clean. Commit or stash changes first."
  exit 1
fi

BRANCH=$(git branch --show-current)
if [ "$BRANCH" != "main" ]; then
  echo "Error: Must be on main branch. Current: $BRANCH"
  exit 1
fi

BUMP="${1:-patch}"

APP_GRADLE="$PROJECT_DIR/app/build.gradle.kts"
WEAR_GRADLE="$PROJECT_DIR/wear/build.gradle.kts"

CURRENT_VERSION=$(grep 'versionName = ' "$APP_GRADLE" | head -1 | sed 's/.*"\(.*\)".*/\1/')
CURRENT_CODE=$(grep 'versionCode = ' "$APP_GRADLE" | head -1 | sed 's/.*= \(.*\)/\1/')

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

case "$BUMP" in
  major)
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
    ;;
  minor)
    MINOR=$((MINOR + 1))
    PATCH=0
    ;;
  patch)
    PATCH=$((PATCH + 1))
    ;;
  *)
    echo "Usage: $0 [major|minor|patch]"
    exit 1
    ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
NEW_CODE=$((CURRENT_CODE + 1))

echo "Bumping: $CURRENT_VERSION ($CURRENT_CODE) → $NEW_VERSION ($NEW_CODE)"

sed -i.bak "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$APP_GRADLE" && rm -f "$APP_GRADLE.bak"
sed -i.bak "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" "$APP_GRADLE" && rm -f "$APP_GRADLE.bak"
sed -i.bak "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$WEAR_GRADLE" && rm -f "$WEAR_GRADLE.bak"
sed -i.bak "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" "$WEAR_GRADLE" && rm -f "$WEAR_GRADLE.bak"

git add "$APP_GRADLE" "$WEAR_GRADLE"
git commit -m "chore(release): v$NEW_VERSION"
git tag "v$NEW_VERSION"

echo ""
echo "Release v$NEW_VERSION committed and tagged."
echo "Push with: git push origin main --follow-tags"
