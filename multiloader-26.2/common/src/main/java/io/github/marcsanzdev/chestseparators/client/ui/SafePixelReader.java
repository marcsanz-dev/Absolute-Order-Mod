package io.github.marcsanzdev.chestseparators.client.ui;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.ARBRobustness;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * Crash-safe framebuffer readback for the loupe and the eyedropper.
 *
 * <p>Plain {@code glReadPixels} trusts the current pixel-pack state. Other mods (or vanilla) may leave
 * {@code GL_PACK_ROW_LENGTH}, {@code GL_PACK_SKIP_*} or a bound {@code GL_PIXEL_PACK_BUFFER} behind, which
 * makes the driver write more bytes than the destination buffer holds (hard JVM crash on AMD,
 * EXCEPTION_ACCESS_VIOLATION in atio6axx.dll). This helper:
 * <ul>
 *   <li>clamps the region to the framebuffer,</li>
 *   <li>forces a known pack state (no PBO, alignment 4, row length 0, no skips) and restores it after,</li>
 *   <li>uses the bounds-checked {@code glReadnPixels} (GL 4.5 / KHR / ARB robustness) when available,</li>
 *   <li>adds slack to the destination buffer as a last line of defence.</li>
 * </ul>
 */
public final class SafePixelReader {

    private SafePixelReader() {}

    private static final int SLACK = 64 * 1024;

    /**
     * Reads a w*h block at framebuffer coords (x, yBottom) as opaque ARGB ints, row 0 = bottom row.
     * Returns {@code null} if nothing could be read; the caller should then skip drawing.
     */
    public static int[] readArgb(int x, int yBottom, int w, int h) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return null;
        int fbW = client.getWindow().getWidth();
        int fbH = client.getWindow().getHeight();
        if (w <= 0 || h <= 0 || fbW <= 0 || fbH <= 0 || w > fbW || h > fbH) return null;
        x = Math.max(0, Math.min(x, fbW - w));
        yBottom = Math.max(0, Math.min(yBottom, fbH - h));

        GLCapabilities caps;
        try {
            caps = GL.getCapabilities();
        } catch (Throwable t) {
            return null; // no GL context on this thread
        }

        int bytes = w * h * 4;
        ByteBuffer buf = MemoryUtil.memCalloc(bytes + SLACK);
        buf.limit(bytes); // glReadnPixels uses remaining() as the hard size limit

        int prevPbo = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevAlign = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
        int prevRowLen = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
        int prevSkipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
        int prevSkipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
        int prevSwap = GL11C.glGetInteger(GL11C.GL_PACK_SWAP_BYTES);
        int prevLsb = GL11C.glGetInteger(GL11C.GL_PACK_LSB_FIRST);
        try {
            if (prevPbo != 0) GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 4);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SWAP_BYTES, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_LSB_FIRST, 0);

            if (caps.OpenGL45 || caps.GL_KHR_robustness) {
                GL45C.glReadnPixels(x, yBottom, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buf);
            } else if (caps.GL_ARB_robustness) {
                ARBRobustness.glReadnPixelsARB(x, yBottom, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buf);
            } else {
                GL11C.glReadPixels(x, yBottom, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buf);
            }

            int[] out = new int[w * h];
            for (int i = 0; i < out.length; i++) {
                int r = buf.get(i * 4) & 0xFF;
                int g = buf.get(i * 4 + 1) & 0xFF;
                int b = buf.get(i * 4 + 2) & 0xFF;
                out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return out;
        } finally {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, prevAlign);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, prevRowLen);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, prevSkipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, prevSkipPixels);
            GL11C.glPixelStorei(GL11C.GL_PACK_SWAP_BYTES, prevSwap);
            GL11C.glPixelStorei(GL11C.GL_PACK_LSB_FIRST, prevLsb);
            if (prevPbo != 0) GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, prevPbo);
            MemoryUtil.memFree(buf);
        }
    }
}
