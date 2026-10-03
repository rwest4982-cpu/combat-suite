# Combat Suite (Fabric 1.21.11)

Singleplayer combat automation with a tabbed config screen and Mod Menu support.

Everything is gated on `MinecraftClient.isInSingleplayer()`. On a multiplayer server no module does anything.

## Build

Needs JDK 21 and Gradle 8.14+ (or any Gradle that Loom 1.14 supports).

    cd combatsuite
    gradle wrapper        # one time, creates ./gradlew
    ./gradlew build       # Windows: gradlew.bat build

The jar is `build/libs/combatsuite-1.0.0.jar` (not the `-sources` one). Drop it in `.minecraft/mods` with Fabric Loader 0.19+, Fabric API and (optional) Mod Menu.

Versions in `gradle.properties` were read from the Fabric and TerraformersMC maven metadata:
Yarn 1.21.11+build.6, Loader 0.19.5, Fabric API 0.141.6+1.21.11, Mod Menu 17.0.0.
The Loom version in `build.gradle` (1.14-SNAPSHOT) was not checked, change it if Gradle cannot resolve it.

## Controls

- Right Shift opens the config screen (rebind in Controls). Mod Menu also opens it.
- Unbound keys to toggle Trigger Bot, Stun Web and Head Web live in the same Controls category.
- Settings save to `config/combatsuite.json`.

## Modules

**Trigger Bot**: hits when a valid target is in range and your attack charge is ready.
Silent Aim off: hits what your crosshair is on. Silent Aim on: hits the nearest valid target in range without moving the camera.
Options: range, attack strength, min and random delay, require weapon, pause while using item, crits only.

**Silent Aim**: rotation is sent to the server only for the action, then your real rotation is sent again. Aim point: eyes, center or feet.

**Stun Web**: when a target comes in range: axe the shield down (optional, with retries), hit them upward, wait for them to leave the ground, then place a cobweb at the predicted landing spot.

**Head Web**: same launch, then place a cobweb in the cell their head will occupy, box that cell with any full hotbar block except cobweb (sides, then top), then a pressure plate on the floor under them.
If the head cell has nothing to place against, it can fall back to webbing their feet (the plate is skipped).
Scaffold depth places support blocks under box blocks that have nothing to click on.

All delays are in ticks (20 per second). Head Web takes priority if both combos are on. Both use the range on the Stun Web tab.

**Targets**: players and bots (fake players count as players), hostile, neutral, passive, ignore invisible, require line of sight.

## Hotbar needs

- Cobweb (required)
- A sword (launch hit) and an axe (shield break) are used if present
- Any full solid block for the Head Web box, a pressure plate for the plate step

## Known limits

- Not compiled or tested yet. If something fails to build, the likeliest spots are the 1.21.9+ `KeyBinding.Category` call in `CombatSuite.java` and mapping names in `Util.java`.
- Blocks cannot be placed inside an entity's hitbox, so box blocks next to a target standing off-center can fail. Failed placements are skipped.
- Landing prediction is an estimate from the target's velocity. Raise or lower Landing Prediction if webs land early or late.
