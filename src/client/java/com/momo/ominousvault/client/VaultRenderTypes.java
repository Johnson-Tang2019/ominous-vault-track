package com.momo.ominousvault.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

final class VaultRenderTypes {
    static final RenderType SEE_THROUGH_LINES = createSeeThroughLines();

    private VaultRenderTypes() {
    }

    private static RenderType createSeeThroughLines() {
        // 26.2 changed RenderPipeline.Snippet's internal layout. Reuse the public
        // vanilla line snippet instead of copying the old 26.1 fields manually.
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ominous-vault-track", "pipeline/see_through_lines"))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();

        RenderSetup setup = RenderSetup.builder(pipeline)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup();

        // RenderType.create(String, RenderSetup) is public in 26.2, so the old
        // access widener is no longer required.
        return RenderType.create("ominous_vault_see_through_lines", setup);
    }
}
