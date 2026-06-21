package io.github.marcsanzdev.chestseparators.client.ui;

import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/**
 * Cursor zoom loupe. It samples a block of the real framebuffer around the cursor and draws it
 * magnified, so the loupe shows exactly what is on screen — item counts, previews, separators,
 * everything — a true magnification rather than a redraw. The loupe sits up-right of the cursor with
 * a small ring on the cursor and two connector lines; pixels outside the chosen shape are skipped, so
 * a circle reads as a clean circle with no surrounding box.
 */
public final class MagnifierRenderer {

    private MagnifierRenderer() {}

    private static final int SRC_HALF = 13; // half of the sampled region (logical px); zoom = LOUPE_R / SRC_HALF
    private static final int LOUPE_R = 52; // loupe radius on screen (logical px)
    private static final int GAP = 16; // gap between cursor and loupe edge
    private static final int OUTLINE = 0xFF000000;

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
        int loupeCx = cursorX + GAP + LOUPE_R;
        int loupeCy = cursorY - GAP - LOUPE_R;
        if (loupeCx + LOUPE_R + 2 > sw) loupeCx = cursorX - GAP - LOUPE_R;
        if (loupeCy - LOUPE_R - 2 < 0) loupeCy = cursorY + GAP + LOUPE_R;

        float cell = (LOUPE_R * 2f) / blockFb;
        int r2 = LOUPE_R * LOUPE_R;

        // Magnified pixels, clipped to the shape (outside pixels are simply not drawn → no box).
        for (int py = 0; py < blockFb; py++) {
            for (int px = 0; px < blockFb; px++) {
                int x0 = Math.round(loupeCx - LOUPE_R + px * cell);
                int x1 = Math.round(loupeCx - LOUPE_R + (px + 1) * cell);
                // py = 0 is the bottom row in GL space → bottom of the region on screen.
                int y0 = Math.round(loupeCy + LOUPE_R - (py + 1) * cell);
                int y1 = Math.round(loupeCy + LOUPE_R - py * cell);
                if (circle) {
                    float ddx = (x0 + x1) / 2f - loupeCx;
                    float ddy = (y0 + y1) / 2f - loupeCy;
                    if (ddx * ddx + ddy * ddy > r2) continue;
                }
                context.fill(x0, y0, x1, y1, pixels[py * blockFb + px]);
            }
        }

        // Reticle on the captured (center) pixel.
        int half = Math.max(1, Math.round(cell / 2f));
        context.drawStrokedRectangle(loupeCx - half, loupeCy - half, half * 2, half * 2, 0xFFFFFFFF);

        // Outline of the loupe and the small ring on the cursor, joined by two connector lines.
        drawOutline(context, loupeCx, loupeCy, LOUPE_R, OUTLINE, circle);
        int smallR = SRC_HALF;
        drawOutline(context, cursorX, cursorY, smallR, 0xCCFFFFFF, circle);
        drawConnectors(context, cursorX, cursorY, smallR, loupeCx, loupeCy, LOUPE_R, 0x99FFFFFF);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private static void drawOutline(DrawContext context, int cx, int cy, int r, int color, boolean circle) {
        if (!circle) {
            context.drawStrokedRectangle(cx - r, cy - r, r * 2, r * 2, color);
            return;
        }
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy)));
            context.fill(cx - dx - 1, cy + dy, cx - dx + 1, cy + dy + 1, color);
            context.fill(cx + dx - 1, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawConnectors(
            DrawContext context,
            int cursorX,
            int cursorY,
            int smallR,
            int loupeCx,
            int loupeCy,
            int loupeR,
            int color) {
        double ang = Math.atan2(loupeCy - cursorY, loupeCx - cursorX);
        double perp = ang + Math.PI / 2.0;
        double pdx = Math.cos(perp);
        double pdy = Math.sin(perp);
        for (int s = -1; s <= 1; s += 2) {
            int x1 = (int) Math.round(cursorX + pdx * smallR * s);
            int y1 = (int) Math.round(cursorY + pdy * smallR * s);
            int x2 = (int) Math.round(loupeCx + pdx * loupeR * s);
            int y2 = (int) Math.round(loupeCy + pdy * loupeR * s);
            drawLine(context, x1, y1, x2, y2, color);
        }
    }

    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
