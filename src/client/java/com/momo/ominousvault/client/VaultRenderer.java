package com.momo.ominousvault.client;

import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collection;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;

public class VaultRenderer {
    public void render(LevelRenderContext context, Minecraft client, Collection<VaultKey> vaults) {
        ModConfig config = ConfigManager.get();
        PoseStack matrices = context.poseStack();
        if (matrices == null) return;

        var collector = context.submitNodeCollector();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        boolean renderTracer = VaultTrackerController.shouldRenderTracer(client, config);
        int tracerColor = toOpaqueArgb(config.tracerColor);
        String server = VaultTrackerController.currentServerKey(client);
        String dimension = VaultTrackerController.currentDimensionKey(client.level);
        BlockPos playerPos = client.player.blockPosition();
        long blockRadius = (long) config.renderRadius * 16L;
        long radiusSquared = blockRadius * blockRadius;

        for (VaultKey key : vaults) {
            if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;
            boolean excluded = VaultStorage.isExcluded(key);
            if (excluded && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;

            int boxColor = toOpaqueArgb(excluded ? config.excludedColor : config.highlightColor);
            matrices.pushPose();
            matrices.translate(
                    key.x() - camera.x,
                    key.y() - camera.y,
                    key.z() - camera.z
            );
            collector.submitShapeOutline(
                    matrices,
                    Shapes.block(),
                    VaultRenderTypes.SEE_THROUGH_LINES,
                    boxColor,
                    2.0F,
                    true
            );
            matrices.popPose();
        }

        if (!renderTracer) return;

        // Use the same depth-disabled RenderType for tracers so the line remains
        // visible even when the vault is behind trial-chamber walls.
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (VaultKey key : vaults) {
            if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;
            if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;

            Vec3 end = Vec3.atCenterOf(key.toBlockPos());
            collector.submitCustomGeometry(
                    matrices,
                    VaultRenderTypes.SEE_THROUGH_LINES,
                    (pose, consumer) -> drawLine(
                            consumer,
                            pose,
                            camera.x, camera.y, camera.z,
                            end.x, end.y, end.z,
                            tracerColor
                    )
            );
        }

        matrices.popPose();
    }

    private static boolean isInRenderScope(
            VaultKey key,
            String server,
            String dimension,
            BlockPos playerPos,
            long radiusSquared
    ) {
        if (!key.server().equals(server) || !key.dimension().equals(dimension)) return false;
        long dx = (long) key.x() - playerPos.getX();
        long dy = (long) key.y() - playerPos.getY();
        long dz = (long) key.z() - playerPos.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static void drawLine(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int argb
    ) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        Vector3f normal = new Vector3f(nx, ny, nz);
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(argb)
                .setLineWidth(2.0F)
                .setNormal(pose, normal);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(argb)
                .setLineWidth(2.0F)
                .setNormal(pose, normal);
    }

    private static int toOpaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
