Ominous Vault Track 26.2 - Iris always-visible ESP patch

Changes:
- Moves vault outline and tracer rendering out of the world RenderType pipeline.
- Projects the vault's 3D cube into screen space.
- Draws the 12 box edges + tracer in Fabric's final HUD layer.
- This makes the overlay independent of depth and Iris shader-pack composite passes.
- Keeps existing colors, range, excluded-vault behavior and tracer item requirement.

Replace/upload these files on the mc-26.2 branch, preserving paths:
- src/client/java/com/momo/ominousvault/OminousVaultTrackClient.java
- src/client/java/com/momo/ominousvault/client/VaultTrackerController.java
- src/client/java/com/momo/ominousvault/client/VaultRenderer.java

The existing GitHub Actions build workflow will rebuild automatically after commit.
