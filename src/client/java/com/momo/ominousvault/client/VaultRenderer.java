package com.momo.ominousvault.client;

import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Collection;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
        var optionsState = context.gameRenderer().getGameRenderState().optionsRenderState;
        Vector3f crosshairOrigin = getCrosshairOrigin(cameraState, optionsState.bobView, optionsState.damageTiltStrength);
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
            boolean bobView,
            double damageTiltStrength
    ) {
        if (cameraState.projectionMatrix == null || cameraState.viewRotationMatrix == null) {
            return new Vector3f(0.0F, -0.15F, 0.0F);
        }

        PoseStack bobStack = new PoseStack();
        applyHurtBob(cameraState, bobStack, damageTiltStrength);
        if (bobView) applyViewBob(cameraState, bobStack);

        // GameRenderer multiplies view bob into the projection matrix before rendering the
        // level. Meteor unprojects the screen center with that same matrix.
        Matrix4f projection = new Matrix4f(cameraState.projectionMatrix).mul(bobStack.last().pose());
        Vector4f center = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F)
                .mul(projection.invert())
                .mul(new Matrix4f(cameraState.viewRotationMatrix).invert());
        center.div(center.w);
        return new Vector3f(center.x, center.y, center.z);
    }

    private static void applyHurtBob(
            net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
            PoseStack matrices,
            double damageTiltStrength
    ) {
        var entity = cameraState.entityRenderState;
        if (!entity.isLiving) return;

        float hurt = entity.hurtTime;
        if (entity.isDeadOrDying) {
            float duration = Math.min(entity.deathTime, 20.0F);
            matrices.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
        }
        if (hurt < 0.0F) return;

        hurt /= entity.hurtDuration;
        hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.hurtDir));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * damageTiltStrength)));
        matrices.mulPose(Axis.YP.rotationDegrees(entity.hurtDir));
    }

    private static void applyViewBob(
            net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
            PoseStack matrices
    ) {
        var entity = cameraState.entityRenderState;
        if (!entity.isPlayer) return;

        float walk = entity.backwardsInterpolatedWalkDistance;
        float bob = entity.bob;
        matrices.translate(
                Mth.sin(walk * (float) Math.PI) * bob * 0.5F,
                -Math.abs(Mth.cos(walk * (float) Math.PI) * bob),
                0.0F
        );
        matrices.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walk * (float) Math.PI) * bob * 3.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * bob) * 5.0F));
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
