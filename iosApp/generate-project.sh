#!/bin/bash
set -e

cd "$(dirname "$0")"

PROJECT_NAME="iosApp"
LEGACY_PROJECT_NAME="Finsight"

if [ -d "${PROJECT_NAME}.xcodeproj" ]; then
  echo "Removing existing ${PROJECT_NAME}.xcodeproj..."
  rm -rf "${PROJECT_NAME}.xcodeproj"
fi

if [ -d "${LEGACY_PROJECT_NAME}.xcodeproj" ]; then
  echo "Removing legacy ${LEGACY_PROJECT_NAME}.xcodeproj..."
  rm -rf "${LEGACY_PROJECT_NAME}.xcodeproj"
fi

echo "Generating Xcode project with XcodeGen..."
xcodegen generate

echo "Project generated successfully."
echo ""
echo "You can now open the project with:"
echo "  open ${PROJECT_NAME}.xcodeproj"
