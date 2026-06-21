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

    // SRC_HALF must satisfy SRC_HALF * sqrt(2) < CURSOR_R, so the captured square never overlaps the
    // cursor ring; otherwise the ring/lines would be sampled into the loupe.
    private static final int SRC_HALF = 9; // half of the sampled square (logical px); zoom = LOUPE_R / SRC_HALF
    private static final int LOUPE_R = 54; // loupe radius on screen (logical px)
    private static final int CURSOR_R = 14; // radius of the small ring drawn on the cursor
    private static final int GAP = 14; // gap between cursor ring and loupe

    public static void render(DrawContext context, int cursorX, int cursorY, boolean circle) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;
        double scale = client.getWindow().getScaleFactor();
        int fbW = client.getWindow().getFramebufferWidth();
        int fbH = client.getWindow().getFramebufferHeight();

        int blockFb = Math.max(1, (int) Math.round(SRC_HALF * 2 * scale));
        int fbX = clamp((int) Math.round((cursorX - SRC_HALF) * scale), 0, fbW - blockFb);
        int fbYBottom = clamp(fbH - (int) Math.round((cursorY + SRC_HALF) * scale), 0, fbH - blockFb);

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
        int loupeCx = cursorX + GAP + CURSOR_R + LOUPE_R;
        int loupeCy = cursorY - GAP - CURSOR_R - LOUPE_R;
        if (loupeCx + LOUPE_R + 2 > sw) loupeCx = cursorX - GAP - CURSOR_R - LOUPE_R;
        if (loupeCy - LOUPE_R - 2 < 0) loupeCy = cursorY + GAP + CURSOR_R + LOUPE_R;
        loupeCy = clamp(loupeCy, LOUPE_R + 2, sh - LOUPE_R - 2);

        float cell = (LOUPE_R * 2f) / blockFb;

        // Magnified pixels with coverage-antialiased circular edge (outside pixels are skipped → no box).
        for (int py = 0; py < blockFb; py++) {
            for (int px = 0; px < blockFb; px++) {
                int x0 = Math.round(loupeCx - LOUPE_R + px * cell);
                int x1 = Math.round(loupeCx - LOUPE_R + (px + 1) * cell);
                // py = 0 is the bottom row in GL space → bottom of the region on screen.
                int y0 = Math.round(loupeCy + LOUPE_R - (py + 1) * cell);
                int y1 = Math.round(loupeCy + LOUPE_R - py * cell);
                int color = pixels[py * blockFb + px];
                if (circle) {
                    double dx = (x0 + x1) / 2.0 - loupeCx;
                    double dy = (y0 + y1) / 2.0 - loupeCy;
                    double cov = LOUPE_R - Math.hypot(dx, dy) + 0.5;
                    if (cov <= 0) continue;
                    if (cov < 1) color = (color & 0x00FFFFFF) | ((int) (cov * 255) << 24);
                }
                context.fill(x0, y0, x1, y1, color);
            }
        }

        // Clean antialiased outline of the loupe.
        if (circle) drawCircleOutlineAA(context, loupeCx, loupeCy, LOUPE_R, 0xFF000000);
        else context.drawStrokedRectangle(loupeCx - LOUPE_R, loupeCy - LOUPE_R, LOUPE_R * 2, LOUPE_R * 2, 0xFF000000);

        // Reticle on the captured (center) pixel.
        int half = Math.max(1, Math.round(cell / 2f));
        context.drawStrokedRectangle(loupeCx - half, loupeCy - half, half * 2, half * 2, 0xFFFFFFFF);

        // Cursor ring + two connector lines, drawn well outside the sampled square.
        if (circle) drawCircleOutlineAA(context, cursorX, cursorY, CURSOR_R, 0xDDFFFFFF);
        else
            context.drawStrokedRectangle(
                    cursorX - CURSOR_R, cursorY - CURSOR_R, CURSOR_R * 2, CURSOR_R * 2, 0xDDFFFFFF);
        drawConnectors(context, cursorX, cursorY, CURSOR_R, loupeCx, loupeCy, LOUPE_R, 0xAAFFFFFF);
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

    private static void drawConnectors(
            DrawContext context,
            int cursorX,
            int cursorY,
            int cursorR,
            int loupeCx,
            int loupeCy,
            int loupeR,
            int color) {
        double ang = Math.atan2(loupeCy - cursorY, loupeCx - cursorX);
        double perp = ang + Math.PI / 2.0;
        double pdx = Math.cos(perp);
        double pdy = Math.sin(perp);
        for (int s = -1; s <= 1; s += 2) {
            double x1 = cursorX + pdx * cursorR * s;
            double y1 = cursorY + pdy * cursorR * s;
            double x2 = loupeCx + pdx * loupeR * s;
            double y2 = loupeCy + pdy * loupeR * s;
            drawLineAA(context, x1, y1, x2, y2, color);
        }
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
