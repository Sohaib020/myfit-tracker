# Equipment & Anatomy Images Integration

This branch implements realistic 3D equipment images and anatomical diagrams for the MyFit Tracker app.

## What's Included

### New Components
- **EquipmentDisplay.kt** - Reusable Compose components for displaying equipment images
  - `EquipmentImage()` - Single equipment with fallback to icon
  - `EquipmentSection()` - Grid layout for equipment display
  - `EquipmentCarousel()` - Horizontal scrolling equipment selector

### Updates
- **MuscleMap.kt** - Added Cardio category with anatomical diagrams
  - Cardio now displays realistic anatomical visualization instead of generic icon
  - Shows full-body engagement with primary muscles highlighted

## Required Image Assets

### Anatomy Diagrams (WebP format)
Place in: `app/src/main/res/drawable-nodpi/anatomy/`

**Cardio-specific:**
```
cardio_front.webp                    # Full front body (neutral)
cardio_front_quadriceps.webp         # Quadriceps highlighted
cardio_front_calves.webp             # Calves highlighted
cardio_back.webp                     # Full back body (neutral)
cardio_back_hamstrings.webp          # Hamstrings highlighted
cardio_back_glutes.webp              # Glutes highlighted
```

**Recommended sources:**
- [InjuryMap Free Anatomy Illustrations](https://injurymap.com/free-human-anatomy-illustrations/)
- [Wikimedia Commons Human Body Diagrams](https://commons.wikimedia.org/wiki/Human_body_diagrams)

### Equipment 3D Renders (WebP format)
Place in: `app/src/main/res/drawable-nodpi/equipment/`

**Complete equipment list:**
```
barbell.webp           # Straight barbell
dumbbell.webp          # Single dumbbell
kettlebell.webp        # Kettlebell
trapbar.webp           # Trap/hex bar
ezbar.webp             # EZ curl bar
legpress.webp          # Leg press machine
chestpress.webp        # Chest press machine
cable.webp             # Cable machine
smith.webp             # Smith machine
hacksquat.webp         # Hack squat machine
pullupbar.webp         # Pull-up bar
bench.webp             # Weight bench
treadmill.webp         # Treadmill
bike.webp              # Stationary bike
rowingmachine.webp     # Rowing machine
elliptical.webp        # Elliptical machine
medicineball.webp      # Medicine ball
exerciseball.webp      # Exercise/Swiss ball
band.webp              # Resistance bands
suspension.webp        # Suspension straps
plate.webp             # Weight plate
foamroller.webp        # Foam roller
dip.webp               # Dip station
none.webp              # Transparent/empty (for bodyweight)
other.webp             # Generic fitness equipment
machine.webp           # Generic machine
```

**Recommended sources:**
- [Open3DModel Gym Equipment](https://open3dmodel.com/en/3d-models/gym-equipment)
- [TurboSquid Free 3D Models](https://www.turbosquid.com/Search/3D-Models/free/gym)
- Google Images (filter by Creative Commons license)

## Image Specifications

- **Format:** WebP (lossless or high-quality lossy)
- **Resolution:** 512×512 or 1024×1024 px
- **Background:** Transparent (PNG converted to WebP) or white
- **Quality:** 85% WebP quality (good balance between size and clarity)
- **Target file size:** <200KB per image

## PNG to WebP Conversion

Using ImageMagick:
```bash
magick convert input.png output.webp -quality 85
```

Using cwebp:
```bash
cwebp -q 85 input.png -o output.webp
```

Online converter: https://convertio.co/png-webp/

## Integration Points

The components are ready to use in:

1. **Exercise Detail Screen** - Show equipment needed for an exercise
```kotlin
EquipmentSection(
    equipmentList = listOf(exercise.equipment),
    modifier = Modifier.padding(horizontal = 16.dp)
)
```

2. **Target Muscles Filter** - Cardio now shows anatomical diagram automatically via TARGET_ART

3. **Day Builder** - Optional equipment selector
```kotlin
EquipmentCarousel(
    equipmentList = availableEquipment,
    selectedEquipment = selectedEquipment,
    onSelect = { equipment -> selectedEquipment = equipment }
)
```

## Testing

After adding images:
1. Build the app: `./gradlew assembleGithubDebug`
2. Navigate to Exercises screen
3. Tap "Cardio" in TARGET MUSCLES - should show anatomical diagram
4. View any exercise detail - should show equipment images if provided
5. Monitor logcat for missing image warnings

## Cache Configuration

Image caches can be adjusted in EquipmentDisplay.kt:
- Equipment images: 24 MB max (line ~19)
- Anatomy images: 12 MB max (in MuscleMap.kt, line ~66)

Adjust based on device capabilities or reduce image resolution if needed.

## Next Steps

1. Download anatomy diagrams from InjuryMap or Wikimedia
2. Download/render equipment 3D images
3. Convert all PNG files to WebP format
4. Place in correct drawable-nodpi directories
5. Build and test on device
6. Merge PR when working correctly

## Attribution

This implementation uses open-source resources:
- **Anatomy:** InjuryMap (free commercial use)
- **Equipment:** Open3DModel (Creative Commons)

See LICENSE and ATTRIBUTION files for detailed credits.

---

**Status:** Ready for image assets  
**Files changed:** 2 (EquipmentDisplay.kt, MuscleMap.kt)  
**Tests:** Manual testing required after image addition
