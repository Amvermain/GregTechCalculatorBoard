package com.gtceu.calcboard.client.gui.export;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.render.*;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Off-screen full-page export. The live screen is never panned, resized, or deselected. */
public final class FlowPngExporter {
    // Accessed only on the Minecraft thread; also prevents queued captures retaining large images.
    private static boolean busy;
    private FlowPngExporter() {}

    public static void request(BoardScreen screen) {
        if (busy) { toast("gui.gtcalcboard.png.busy"); return; }
        // Run at the start of a subsequent frame, outside the toolbar's active render batch.
        screen.requestPngCapture();
        toast("gui.gtcalcboard.png.working");
    }

    public static void capture(BoardScreen screen) {
        if (busy) return;
        busy = true;
        try {
            RenderSystem.assertOnRenderThread();
            var graph = screen.getGraph();
            List<NodeWidget> nodes = graph.getNodes().stream()
                    .filter(n -> !graph.isNodeInFoldedFrame(n.getId()))
                    .map(n -> new NodeWidget(n, screen)).toList();
            var widgets = nodes.stream().collect(java.util.stream.Collectors.toMap(n -> n.getNode().getId(), n -> n));
            FlowImageBounds bounds = new FlowImageBounds();
            for (NodeWidget node : nodes) {
                bounds.include(node.getNode().getPosX(), node.getNode().getPosY(), node.getWidth(), node.getHeight());
            }
            for (var frame : graph.getFrames()) {
                double height = frame.getHeight();
                if (frame.isSharedMachineFrame() && frame.isFolded()) {
                    height = 70 + Math.max(1, FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame).maxPortCount()) * 18;
                }
                bounds.include(frame.getPosX(), frame.getPosY(), frame.getWidth(), height);
            }
            for (var note : graph.getStickyNotes()) {
                bounds.include(note.getPosX(), note.getPosY(), note.getWidth(), note.getHeight());
            }
            float[] cp = new float[4];
            for (var edge : graph.getConnections()) {
                var p = CanvasWireRenderer.resolveWireEndpointsForWidgets(graph, n -> widgets.get(n.getId()), edge);
                if (p == null || p.isInternalCull()) continue;
                ConnectionRenderer.computeControlPoints(p.x1(), p.y1(), p.x2(), p.y2(), p.fromDirX(), p.toDirX(), cp);
                // A Bezier lies inside the convex hull of its endpoints and control points.
                bounds.include(p.x1(), p.y1(), 0, 0);
                bounds.include(p.x2(), p.y2(), 0, 0);
                bounds.include(cp[0], cp[1], 0, 0);
                bounds.include(cp[2], cp[3], 0, 0);
            }
            var plan = bounds.plan(RenderSystem.maxSupportedTextureSize());
            NativeImage image = render(screen, nodes, plan);
            CompletableFuture.runAsync(() -> finish(image, plan));
        } catch (IllegalArgumentException e) {
            busy = false;
            if ("empty".equals(e.getMessage()) || "too_large".equals(e.getMessage())) {
                toast("empty".equals(e.getMessage()) ? "gui.gtcalcboard.png.empty" : "gui.gtcalcboard.png.too_large");
            } else {
                GregTechCalcBoard.LOGGER.error("Invalid flow geometry during PNG capture", e);
                toast("gui.gtcalcboard.png.failed");
            }
        } catch (Exception | OutOfMemoryError e) {
            busy = false;
            GregTechCalcBoard.LOGGER.error("Flow PNG capture failed", e);
            toast("gui.gtcalcboard.png.failed");
        }
    }

    private static NativeImage render(BoardScreen screen, List<NodeWidget> nodes, FlowImageBounds.Plan plan) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        TextureTarget target = null;
        GuiGraphics graphics = null;
        modelView.pushPose();
        try (var scope = new ExportRenderScope()) {
            RenderSystem.disableScissor();
            target = new TextureTarget(plan.width(), plan.height(), true, Minecraft.ON_OSX);
            target.setClearColor(0.055f, 0.065f, 0.085f, 1.0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            modelView.setIdentity();
            modelView.translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, plan.width(), plan.height(), 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            graphics.pose().scale(FlowImageBounds.SCALE, FlowImageBounds.SCALE, 1);
            graphics.pose().translate(-plan.left(), -plan.top(), 0);
            var graph = screen.getGraph();
            double left = plan.left(), top = plan.top();
            double right = left + plan.width() / (double) FlowImageBounds.SCALE;
            double bottom = top + plan.height() / (double) FlowImageBounds.SCALE;
            // Coordinates outside the content avoid hover effects, including negative-position boards.
            double mouse = -Double.MAX_VALUE;
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            CanvasGroupFrameRenderer.renderFrames(graphics, graph, mouse, mouse, null, Set.of(), left, right, top, bottom);
            CanvasStickyNoteRenderer.renderNotes(graphics, graph, mouse, mouse, Set.of(), left, right, top, bottom);
            var widgets = nodes.stream().collect(java.util.stream.Collectors.toMap(n -> n.getNode().getId(), n -> n));
            new CanvasWireRenderer().renderWires(graphics, screen, graph, mouse, mouse, left, right, top, bottom,
                    FlowImageBounds.SCALE, n -> widgets.get(n.getId()));
            for (int i = 0; i < nodes.size(); i++) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 1 + i * Math.min(0.5f, 1000.0f / nodes.size()));
                nodes.get(i).render(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
                graphics.pose().popPose();
            }
            graphics.flush();
            return Screenshot.takeScreenshot(target);
        } finally {
            try {
                // Do not leave partial export batches to be drawn into the next on-screen frame.
                if (graphics != null) graphics.flush();
            } finally {
                if (target != null) target.destroyBuffers();
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
                RenderSystem.depthFunc(depthFunction);
                RenderSystem.depthMask(depthWrite);
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
                else RenderSystem.disableScissor();
            }
        }
    }

    private static void finish(NativeImage image, FlowImageBounds.Plan plan) {
        try (image) {
            byte[] png = image.asByteArray();
            try {
                PngClipboard.copy(png);
                notifyResult("gui.gtcalcboard.png.copied", plan.width(), plan.height());
            } catch (Exception | LinkageError e) {
                // Headless launchers, clipboard ownership, and unsupported desktop backends can fail.
                Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/gtcalcboard");
                Files.createDirectories(directory);
                Path file = Files.createTempFile(directory, "flow-", ".png");
                Files.write(file, png);
                GregTechCalcBoard.LOGGER.warn("Image clipboard unavailable; saved flow to {}", file, e);
                notifyResult("gui.gtcalcboard.png.saved", file.toAbsolutePath().toString());
            }
        } catch (Exception | OutOfMemoryError e) {
            GregTechCalcBoard.LOGGER.error("Flow PNG encoding/copy failed", e);
            notifyResult("gui.gtcalcboard.png.failed");
        } finally {
            Minecraft.getInstance().execute(() -> busy = false);
        }
    }

    private static void toast(String key, Object... args) {
        BoardToast.show(Component.translatable(key, args));
    }
    private static void notifyResult(String key, Object... args) {
        Minecraft.getInstance().execute(() -> toast(key, args));
    }
}
