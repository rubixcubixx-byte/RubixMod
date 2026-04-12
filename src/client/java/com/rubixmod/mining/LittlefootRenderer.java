package com.rubixmod.mining;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rubixmod.config.RubixConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Renders a thick orange bounding-box outline and a tracer line from the
 * player's camera to every tracked Littlefoot entity.
 *
 * Uses a custom {@link RenderPipeline} with {@link DepthTestFunction#NO_DEPTH_TEST}
 * so the lines render through terrain (X-ray style).
 */
public class LittlefootRenderer {

    // Orange-gold accent — RGB 0-255
    private static final int CR = 255, CG = 140, CB = 0;

    /**
     * X-ray line pipeline: identical to the vanilla LINES pipeline but with depth
     * testing fully disabled so geometry draws through terrain.
     */
    private static final RenderPipeline XRAY_PIPELINE = RenderPipeline
            .builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("rubixmod", "lines_xray"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();

    /** RenderType that wraps the x-ray pipeline — used in place of {@link net.minecraft.client.renderer.rendertype.RenderTypes#LINES}. */
    private static final RenderType LINES_XRAY = RenderType.create(
            "rubixmod:lines_xray",
            RenderSetup.builder(XRAY_PIPELINE).createRenderSetup()
    );

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(LittlefootRenderer::onAfterEntities);
    }

    private static void onAfterEntities(WorldRenderContext context) {
        if (!RubixConfig.get().littlefootTrackerEnabled) return;

        List<Entity> targets = LittlefootTracker.getTracked();
        if (targets.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        // Camera position — needed to translate from world coords to render coords
        Vec3 cam = client.gameRenderer.getMainCamera().position();

        PoseStack ps = context.matrices();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(LINES_XRAY);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = ps.last();

        for (Entity entity : targets) {
            AABB bb     = entity.getBoundingBox().inflate(0.06);
            Vec3 center = entity.getBoundingBox().getCenter();

            // ── Bounding-box outline (x-ray, draws through walls) ────────────
            drawBox(pose, lines, bb, CR, CG, CB, 255, 3.0f);

            // ── Tracer line: crosshair (camera eye) → entity centre ──────────
            // cam is the camera's world position, which after our translate(-cam)
            // lands at render-space (0,0,0) — exactly the crosshair origin.
            drawLine(pose, lines,
                    (float) cam.x, (float) cam.y, (float) cam.z,
                    (float) center.x, (float) center.y, (float) center.z,
                    CR, CG, CB, 200, 2.0f);
        }

        ps.popPose();
        buffers.endBatch(LINES_XRAY);
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** Draws all 12 edges of an axis-aligned bounding box. */
    private static void drawBox(PoseStack.Pose pose, VertexConsumer vc, AABB b,
                                int r, int g, int blue, int a, float lineWidth) {
        float x0 = (float) b.minX, x1 = (float) b.maxX;
        float y0 = (float) b.minY, y1 = (float) b.maxY;
        float z0 = (float) b.minZ, z1 = (float) b.maxZ;

        // Bottom face
        drawLine(pose, vc, x0, y0, z0,  x1, y0, z0,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y0, z0,  x1, y0, z1,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y0, z1,  x0, y0, z1,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x0, y0, z1,  x0, y0, z0,  r, g, blue, a, lineWidth);
        // Top face
        drawLine(pose, vc, x0, y1, z0,  x1, y1, z0,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y1, z0,  x1, y1, z1,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y1, z1,  x0, y1, z1,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x0, y1, z1,  x0, y1, z0,  r, g, blue, a, lineWidth);
        // Verticals
        drawLine(pose, vc, x0, y0, z0,  x0, y1, z0,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y0, z0,  x1, y1, z0,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x1, y0, z1,  x1, y1, z1,  r, g, blue, a, lineWidth);
        drawLine(pose, vc, x0, y0, z1,  x0, y1, z1,  r, g, blue, a, lineWidth);
    }

    /** Emits two vertices forming a single line segment. */
    private static void drawLine(PoseStack.Pose pose, VertexConsumer vc,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int r, int g, int b, int a, float lineWidth) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
    }
}
