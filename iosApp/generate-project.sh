#!/bin/bash
set -e

cd "$(dirname "$0")"

if [ -d "iosApp.xcodeproj" ]; then
  echo "🗑️  Removing existing project..."
  rm -rf iosApp.xcodeproj
fi

echo "🔨 Generating Xcode project with XcodeGen..."
xcodegen generate

echo "✅ Project generated successfully!"
echo ""
echo "You can now open the project with:"
echo "  open iosApp.xcodeproj"
