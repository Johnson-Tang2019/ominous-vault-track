# 26.2 Port Notes

## Version changes

- Minecraft: `26.1` -> `26.2`
- Fabric Loader: `0.19.3`
- Fabric Loom: `1.17.19`
- Fabric API: `0.158.0+26.2`
- Cloth Config: `26.2.155`
- Mod Menu: `20.0.1`
- Java: 25

## Rendering rewrite

The upstream 26.1 renderer used `LevelRenderEvents.END_MAIN`, `MultiBufferSource`, `ShapeRenderer`, and direct vertex buffering. Minecraft 26.2 overhauled feature rendering and removed `ShapeRenderer` in favor of submit-node features.

The port now uses:

- `LevelRenderEvents.COLLECT_SUBMITS`
- `LevelRenderContext.submitNodeCollector()`
- `SubmitNodeCollector.submitShapeOutline(...)`
- `SubmitNodeCollector.submitCustomGeometry(...)`

The custom see-through `RenderType` is still retained because its depth test is `CompareOp.ALWAYS_PASS` with depth writes disabled. Both the vault outline and tracer use that same RenderType, so both remain visible through walls.

## Pipeline compatibility

The 26.1 source manually copied every field from `RenderPipelines.LINES` into a `RenderPipeline.Snippet`. That constructor changed in 26.2 (bind groups, multiple color targets, vertex bindings, etc.).

Instead, the 26.2 port builds from the public vanilla `RenderPipelines.LINES_SNIPPET` and only overrides:

- pipeline identifier
- depth/stencil state

This is substantially less coupled to Blaze3D internals.

## Access widener

The upstream access widener only made `RenderType.create(String, RenderSetup)` accessible. It is public in 26.2, so the access widener and its `fabric.mod.json` declaration were removed.

## Tracer behavior

The old renderer reconstructed an exact crosshair origin using projection matrices, hurt tilt, and view bobbing. That code depends on 26.1 render-state internals. The 26.2 port instead starts the tracer at the current camera position and submits it through the feature renderer. It therefore still appears from the screen center while avoiding the fragile 26.1 matrix reconstruction code.

## Additional 26.2 API migrations

Two non-rendering Minecraft client API changes were also required:

- `Minecraft.isSingleplayer()` -> `Minecraft.hasSingleplayerServer()`
- direct `Minecraft.screen` / `Minecraft.setScreen(...)` access -> `Minecraft.gui.screen()` / `Minecraft.gui.setScreen(...)`

The chunk scanning and ominous-vault predicate remain structurally unchanged: loaded block entities are inspected and only `minecraft:vault` states with the `ominous=true` property are retained.

## Build verification status

The port has been statically migrated against the Minecraft/Fabric 26.2 API surface, but this working environment only provides JDK 21 and no local Gradle installation. Minecraft 26.2 targets Java 25, so an actual Gradle compile/remap could not be executed here. The source package is therefore marked as **not locally compiled** rather than being represented as a tested binary.
