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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class VaultRenderer {
    public void render(LevelRenderContext context, Minecraft client, Collection<VaultKey> vaults) {
        ModConfig config = ConfigManager.get();
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        boolean renderTracer = VaultTrackerController.shouldRenderTracer(client, config);
        String server = VaultTrackerController.currentServerKey(client);
        String dimension = VaultTrackerController.currentDimensionKey(client.level);

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.last().pose();

        VertexConsumer lines = context.bufferSource().getBuffer(RenderTypes.lines());
        for (VaultKey key : vaults) {
            if (!key.server().equals(server) || !key.dimension().equals(dimension)) continue;

            boolean excluded = VaultStorage.isExcluded(key);
            if (excluded && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
            int boxColor = toOpaqueArgb(excluded ? config.excludedColor : config.highlightColor);
            BlockPos pos = key.toBlockPos();
            drawBox(lines, matrix, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, boxColor);
        }

        if (renderTracer) {
            int tracerColor = toOpaqueArgb(config.tracerColor);
            for (VaultKey key : vaults) {
                if (!key.server().equals(server) || !key.dimension().equals(dimension)) continue;
                if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;

                Vec3 end = Vec3.atCenterOf(key.toBlockPos());
                drawLine(lines, matrix, camera.x, camera.y - 0.15, camera.z, end.x, end.y, end.z, tracerColor);
            }
        }

        matrices.popPose();
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        drawLine(consumer, matrix, x1, y1, z1, x2, y1, z1, argb);
        drawLine(consumer, matrix, x2, y1, z1, x2, y1, z2, argb);
        drawLine(consumer, matrix, x2, y1, z2, x1, y1, z2, argb);
        drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, argb);
        drawLine(consumer, matrix, x1, y2, z1, x2, y2, z1, argb);
        drawLine(consumer, matrix, x2, y2, z1, x2, y2, z2, argb);
        drawLine(consumer, matrix, x2, y2, z2, x1, y2, z2, argb);
        drawLine(consumer, matrix, x1, y2, z2, x1, y2, z1, argb);
        drawLine(consumer, matrix, x1, y1, z1, x1, y2, z1, argb);
        drawLine(consumer, matrix, x2, y1, z1, x2, y2, z1, argb);
        drawLine(consumer, matrix, x2, y1, z2, x2, y2, z2, argb);
        drawLine(consumer, matrix, x1, y1, z2, x1, y2, z2, argb);
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(argb).setLineWidth(2.0F).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(argb).setLineWidth(2.0F).setNormal(nx, ny, nz);
    }

    private static int toOpaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
