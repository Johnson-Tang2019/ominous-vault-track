# Build status

- Target: Minecraft 26.2 / Fabric
- Java target: 25
- Porting status: source migration complete
- Local compile status: NOT RUN
- Reason: generation environment contains OpenJDK 21 only and no Gradle executable

Static migration checks performed:

- moved world rendering to Fabric 26.2 submit-node rendering (`COLLECT_SUBMITS`)
- replaced old `ShapeRenderer` / direct buffer submission
- rebuilt see-through line RenderType from `RenderPipelines.LINES_SNIPPET`
- removed obsolete access widener
- migrated `Minecraft.isSingleplayer()` to `hasSingleplayerServer()`
- migrated screen access to `Minecraft.gui.screen()` / `Minecraft.gui.setScreen()`
- retained ominous-only vault state filtering and loaded-chunk scanning

Build on a machine with JDK 25 using Gradle 9.5.x:

    gradle build

The remapped mod JAR should then appear under `build/libs/`.
