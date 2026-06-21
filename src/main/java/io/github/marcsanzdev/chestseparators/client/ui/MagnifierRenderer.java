package io.github.marcsanzdev.chestseparators.client.ui;

import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/**
 * Cursor zoom loupe. It samples a small block of the real framebuffer under the cursor and draws it
 * magnified, so the loupe shows exactly what is on screen — item counts, previews, separators,
 * everything. The sampled square is kept strictly smaller than the cursor ring, so the ring and the
 * connector lines are never inside the captured area (and therefore never appear magnified inside the
 * loupe). The loupe edge, the cursor ring and the connector lines are all drawn with per-pixel
 * coverage antialiasing for clean, non-jagged shapes.
 */
public final class MagnifierRenderer {

    private MagnifierRenderer() {}

    // Base sizes at 100%. BASE_SRC_HALF * sqrt(2) < BASE_CURSOR_R must hold so the captured square
    // never overlaps the cursor ring (otherwise the ring/lines would be sampled into the loupe). All
    // sizes scale together with the configured size percentage, keeping the zoom factor constant.
    private static final int BASE_SRC_HALF = 9;
    private static final int BASE_LOUPE_R = 54;
    private static final int BASE_CURSOR_R = 14;
    private static final int BASE_GAP = 14;
    private static final int BASE_RING = 4;
    private static final int FRAME_COLOR = 0xFF202020;

    public static void render(DrawContext context, int cursorX, int cursorY, boolean circle, int sizePercent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;

        int pct = Math.max(100, sizePercent);
        int srcHalf = BASE_SRC_HALF * pct / 100;
        int loupeR = BASE_LOUPE_R * pct / 100;
        int cursorR = BASE_CURSOR_R * pct / 100;
        int gap = BASE_GAP * pct / 100;
        int ring = Math.max(2, BASE_RING * pct / 100);

        double scale = client.getWindow().getScaleFactor();
        int fbW = client.getWindow().getFramebufferWidth();
        int fbH = client.getWindow().getFramebufferHeight();

        int blockFb = Math.max(1, (int) Math.round(srcHalf * 2 * scale));
        int fbX = clamp((int) Math.round((cursorX - srcHalf) * scale), 0, fbW - blockFb);
        int fbYBottom = clamp(fbH - (int) Math.round((cursorY + srcHalf) * scale), 0, fbH - blockFb);

        int[] pixels = new int[blockFb * blockFb];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(blockFb * blockFb * 4);
            GL11.glReadPixels(fbX, fbYBottom, blockFb, blockFb, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            for (int i = 0; i < pixels.length; i++) {
                int r = buf.get(i * 4) & 0xFF;
                int g = buf.get(i * 4 + 1) & 0xFF;
                int b = buf.get(i * 4 + 2) & 0xFF;
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        // Place the loupe up-right of the cursor, flipping to stay on screen.
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int loupeCx = cursorX + gap + cursorR + loupeR;
        int loupeCy = cursorY - gap - cursorR - loupeR;
        if (loupeCx + loupeR + 2 > sw) loupeCx = cursorX - gap - cursorR - loupeR;
        if (loupeCy - loupeR - 2 < 0) loupeCy = cursorY + gap + cursorR + loupeR;
        loupeCy = clamp(loupeCy, loupeR + 2, sh - loupeR - 2);

        float cell = (loupeR * 2f) / blockFb;
        // Content extent: a hard-clipped disc/square, with the jagged edge hidden under the thick frame.
        int contentR = loupeR - ring;
        long contentR2 = (long) contentR * contentR;

        for (int py = 0; py < blockFb; py++) {
            for (int px = 0; px < blockFb; px++) {
                int x0 = Math.round(loupeCx - loupeR + px * cell);
                int x1 = Math.round(loupeCx - loupeR + (px + 1) * cell);
                // py = 0 is the bottom row in GL space → bottom of the region on screen.
                int y0 = Math.round(loupeCy + loupeR - (py + 1) * cell);
                int y1 = Math.round(loupeCy + loupeR - py * cell);
                double dx = (x0 + x1) / 2.0 - loupeCx;
                double dy = (y0 + y1) / 2.0 - loupeCy;
                if (circle) {
                    if (dx * dx + dy * dy > contentR2) continue; // hard clip → no bleed outside the disc
                } else if (Math.abs(dx) > contentR || Math.abs(dy) > contentR) {
                    continue;
                }
                context.fill(x0, y0, x1, y1, pixels[py * blockFb + px]);
            }
        }

        // Opaque frame of thickness `ring`, hiding the content edge (consistent for circle and square).
        if (circle) drawRingFrame(context, loupeCx, loupeCy, contentR, loupeR, FRAME_COLOR);
        else drawSquareFrame(context, loupeCx, loupeCy, contentR, loupeR, FRAME_COLOR);

        // Reticle on the captured (center) pixel.
        int half = Math.max(1, Math.round(cell / 2f));
        context.drawStrokedRectangle(loupeCx - half, loupeCy - half, half * 2, half * 2, 0xFFFFFFFF);

        // Cursor marker + connector lines that hug the outer edges (never cross the shapes).
        int cursorRing = Math.max(2, ring - 1);
        if (circle) {
            drawRingFrame(context, cursorX, cursorY, cursorR - cursorRing, cursorR, 0xDDFFFFFF);
            drawCircleConnectors(context, cursorX, cursorY, cursorR, loupeCx, loupeCy, loupeR, 0xCCFFFFFF);
        } else {
            drawSquareFrame(context, cursorX, cursorY, cursorR - cursorRing, cursorR, 0xDDFFFFFF);
            drawSquareConnectors(context, cursorX, cursorY, cursorR, loupeCx, loupeCy, loupeR, 0xCCFFFFFF);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    /** Antialiased 1px circle outline using per-pixel distance coverage, iterating only near the edge. */
    private static void drawCircleOutlineAA(DrawContext context, int cx, int cy, int r, int color) {
        int rgb = color & 0x00FFFFFF;
        int baseA = (color >>> 24) & 0xFF;
        for (int y = -r - 1; y <= r + 1; y++) {
            double inside = (double) r * r - (double) y * y;
            int edge = inside <= 0 ? 0 : (int) Math.round(Math.sqrt(inside));
            for (int side = -1; side <= 1; side += 2) {
                for (int x = edge * side - 2; x <= edge * side + 2; x++) {
                    double cov = 1.0 - Math.abs(Math.sqrt((double) x * x + (double) y * y) - r);
                    if (cov <= 0) continue;
                    int a = (int) (baseA * Math.min(1.0, cov));
                    if (a <= 0) continue;
                    context.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, (a << 24) | rgb);
                }
                if (edge == 0) break;
            }
        }
    }

    /** Opaque ring frame: filled annulus with antialiased inner and outer edges. */
    private static void drawRingFrame(DrawContext context, int cx, int cy, int rInner, int rOuter, int color) {
        int rgb = color & 0x00FFFFFF;
        for (int y = -rOuter; y <= rOuter; y++) {
            double outer = (double) rOuter * rOuter - (double) y * y;
            if (outer < 0) continue;
            int xo = (int) Math.floor(Math.sqrt(outer));
            double innerSq = (double) rInner * rInner - (double) y * y;
            int xi = innerSq <= 0 ? -1 : (int) Math.ceil(Math.sqrt(innerSq));
            if (xi < 0 || xi > xo) {
                context.fill(cx - xo, cy + y, cx + xo + 1, cy + y + 1, color);
            } else {
                context.fill(cx - xo, cy + y, cx - xi + 1, cy + y + 1, color);
                context.fill(cx + xi, cy + y, cx + xo + 1, cy + y + 1, color);
            }
        }
        // Antialias the inner and outer edges so the frame reads as a clean circle.
        drawCircleOutlineAA(context, cx, cy, rOuter, (0xCC << 24) | rgb);
        drawCircleOutlineAA(context, cx, cy, rInner, (0xCC << 24) | rgb);
    }

    /** Opaque square frame: a thick border between the inner and outer half-sizes. */
    private static void drawSquareFrame(DrawContext context, int cx, int cy, int rInner, int rOuter, int color) {
        // top, bottom, left, right bars of thickness (rOuter - rInner)
        context.fill(cx - rOuter, cy - rOuter, cx + rOuter, cy - rInner, color);
        context.fill(cx - rOuter, cy + rInner, cx + rOuter, cy + rOuter, color);
        context.fill(cx - rOuter, cy - rInner, cx - rInner, cy + rInner, color);
        context.fill(cx + rInner, cy - rInner, cx + rOuter, cy + rInner, color);
    }

    /** Two external tangent lines between the cursor circle and the loupe circle (never cross either). */
    private static void drawCircleConnectors(
            DrawContext context, int cursorX, int cursorY, int r1, int loupeCx, int loupeCy, int r2, int color) {
        double d = Math.hypot(loupeCx - cursorX, loupeCy - cursorY);
        if (d < 1) return;
        double vx = (loupeCx - cursorX) / d;
        double vy = (loupeCy - cursorY) / d;
        double c = (double) (r1 - r2) / d;
        double h = Math.sqrt(Math.max(0, 1 - c * c));
        for (int s = -1; s <= 1; s += 2) {
            double nx = vx * c - s * h * vy;
            double ny = vy * c + s * h * vx;
            drawLineAA(context, cursorX + r1 * nx, cursorY + r1 * ny, loupeCx + r2 * nx, loupeCy + r2 * ny, color);
        }
    }

    /** Two lines joining the outer corners of the cursor square and the loupe square (never cross them). */
    private static void drawSquareConnectors(
            DrawContext context, int cursorX, int cursorY, int r1, int loupeCx, int loupeCy, int r2, int color) {
        double dx = loupeCx - cursorX;
        double dy = loupeCy - cursorY;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        // Perpendicular to the cursor→loupe axis; corners along it are the outermost ones.
        int sgnx = (-dy) >= 0 ? 1 : -1;
        int sgny = dx >= 0 ? 1 : -1;
        drawLineAA(context, cursorX + r1 * sgnx, cursorY + r1 * sgny, loupeCx + r2 * sgnx, loupeCy + r2 * sgny, color);
        drawLineAA(context, cursorX - r1 * sgnx, cursorY - r1 * sgny, loupeCx - r2 * sgnx, loupeCy - r2 * sgny, color);
    }

    /** Xiaolin Wu antialiased line. */
    private static void drawLineAA(DrawContext context, double x0, double y0, double x1, double y1, int color) {
        int rgb = color & 0x00FFFFFF;
        int baseA = (color >>> 24) & 0xFF;
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) {
            double t = x0;
            x0 = y0;
            y0 = t;
            t = x1;
            x1 = y1;
            y1 = t;
        }
        if (x0 > x1) {
            double t = x0;
            x0 = x1;
            x1 = t;
            t = y0;
            y0 = y1;
            y1 = t;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double gradient = dx == 0 ? 1.0 : dy / dx;
        double intery = y0 + gradient * (Math.round(x0) - x0) + gradient;
        for (int x = (int) Math.round(x0); x <= (int) Math.round(x1); x++) {
            int yy = (int) Math.floor(intery);
            double f = intery - yy;
            plot(context, steep, x, yy, (1 - f) * baseA, rgb);
            plot(context, steep, x, yy + 1, f * baseA, rgb);
            intery += gradient;
        }
    }

    private static void plot(DrawContext context, boolean steep, int a, int b, double alpha, int rgb) {
        int ai = (int) Math.round(alpha);
        if (ai <= 0) return;
        int x = steep ? b : a;
        int y = steep ? a : b;
        context.fill(x, y, x + 1, y + 1, (ai << 24) | rgb);
    }
}
