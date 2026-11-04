#!/bin/bash
# Push all R37 body morph changes to GitHub

cd "$(dirname "$0")"

echo "Staging all changes..."
git add .

echo "Committing..."
git commit -m "R37: Integrate 3D body morphing for weight/body-shape onboarding

- Replace AI-generated sprites with deformed 3D models (male & female)
- Generate 6 body fat variants (bf1-bf6) from Meshy GLB sources
- Add BodyShapePage with morphing character slider
- Add WeightPage showing user's selected body shape
- Recalibrate focus area anchors for accurate anatomy
- Reorder flow: body-shape → height → weight → focus
- Add GitHub Actions workflow to auto-build APK on push"

echo "Pushing to current branch..."
git push

echo ""
echo "✓ Done! Check GitHub Actions at:"
echo "  https://github.com/Sohaib020/myfit-tracker/actions"
echo ""
echo "The APK will be available as a downloadable artifact within ~5 minutes"
