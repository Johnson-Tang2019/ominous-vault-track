package com.momo.ominousvault.client;

import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Collection;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class VaultRenderer {
    public void render(LevelRenderContext context, Minecraft client, Collection<VaultKey> vaults) {
        ModConfig config = ConfigManager.get();
        PoseStack matrices = context.poseStack();
        var cameraState = context.levelState().cameraRenderState;
        Vec3 camera = cameraState.pos;
        Vector3f crosshairOrigin = getCrosshairOrigin(cameraState, matrices.last().pose());
        boolean renderTracer = VaultTrackerController.shouldRenderTracer(client, config);
        int tracerColor = toOpaqueArgb(config.tracerColor);
        String server = VaultTrackerController.currentServerKey(client);
        String dimension = VaultTrackerController.currentDimensionKey(client.level);
        BlockPos playerPos = client.player.blockPosition();
        long blockRadius = (long) config.renderRadius * 16L;
        long radiusSquared = blockRadius * blockRadius;

        matrices.pushPose();
        VertexConsumer boxLines = context.bufferSource().getBuffer(VaultRenderTypes.SEE_THROUGH_LINES);
        for (VaultKey key : vaults) {
            if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;
            boolean excluded = VaultStorage.isExcluded(key);
            if (excluded && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
            int boxColor = toOpaqueArgb(excluded ? config.excludedColor : config.highlightColor);
            // Match vanilla/Meteor's world-render convention: submit camera-relative coordinates
            // and let ShapeRenderer transform normals through the active pose.
            ShapeRenderer.renderShape(
                    matrices,
                    boxLines,
                    Shapes.block(),
                    key.x() - camera.x,
                    key.y() - camera.y,
                    key.z() - camera.z,
                    boxColor,
                    2.0F
            );
        }
        context.bufferSource().endBatch(VaultRenderTypes.SEE_THROUGH_LINES);

        if (renderTracer) {
            VertexConsumer tracerLines = context.bufferSource().getBuffer(RenderTypes.lines());
            for (VaultKey key : vaults) {
                if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;
                if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
                Vec3 end = Vec3.atCenterOf(key.toBlockPos());
                drawLine(tracerLines, matrices.last(), crosshairOrigin.x, crosshairOrigin.y, crosshairOrigin.z,
                        end.x - camera.x, end.y - camera.y, end.z - camera.z, tracerColor);
            }
        }

        matrices.popPose();
        // END_MAIN is the final vanilla buffer stage. Fabric explicitly requires consumers
        // rendered there to be flushed by the subscriber.
        context.bufferSource().endBatch();
    }

    private static boolean isInRenderScope(VaultKey key, String server, String dimension, BlockPos playerPos, long radiusSquared) {
        if (!key.server().equals(server) || !key.dimension().equals(dimension)) return false;
        long dx = (long) key.x() - playerPos.getX();
        long dy = (long) key.y() - playerPos.getY();
        long dz = (long) key.z() - playerPos.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static Vector3f getCrosshairOrigin(
            net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
            Matrix4f pose
    ) {
        if (cameraState.projectionMatrix == null) {
            return new Vector3f(0.0F, -0.15F, 0.0F);
        }

        // Include the frame's real model-view and pose matrices so hurt tilt and view bob are
        // inverted as well. This is what keeps Meteor's tracer origin locked to the crosshair
        // while the player walks.
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        Vector4f center = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F)
                .mul(new Matrix4f(cameraState.projectionMatrix).invert())
                .mul(view.invert());
        center.div(center.w);
        return new Vector3f(center.x, center.y, center.z);
    }

    private static void drawLine(VertexConsumer consumer, PoseStack.Pose pose, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
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
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(argb).setLineWidth(2.0F).setNormal(pose, normal);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(argb).setLineWidth(2.0F).setNormal(pose, normal);
    }

    private static int toOpaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
