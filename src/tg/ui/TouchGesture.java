package tg.ui;

import javax.microedition.lcdui.Canvas;

/**
 * Small allocation-free pointer gesture recogniser for MIDP Canvas screens.
 *
 * It deliberately recognises only what the client needs: a short tap, a long
 * press and a drag.  Screens keep ownership of hit testing and scrolling, so
 * keyboard actions remain their single source of behaviour.
 */
public final class TouchGesture
{
    public static final int NONE = 0;
    public static final int TAP = 1;
    public static final int LONG_PRESS = 2;

    /** Long enough not to turn an ordinary deliberate tap into a menu. */
    public static final long LONG_PRESS_MS = 650L;

    private static final int DRAG_SLOP = 6;

    private boolean pressed;
    private boolean dragged;
    private int startX;
    private int startY;
    private int lastX;
    private int lastY;
    private int deltaX;
    private int deltaY;
    private long pressedAt;

    public void press(int x, int y)
    {
        press(x, y, System.currentTimeMillis());
    }

    /** Explicit clock overload keeps the timing boundary deterministic in tests. */
    public void press(int x, int y, long now)
    {
        pressed = true;
        dragged = false;
        startX = lastX = x;
        startY = lastY = y;
        deltaX = deltaY = 0;
        pressedAt = now;
    }

    /**
     * Record pointer motion.  Once the initial slop is crossed every later
     * motion is a drag and {@link #deltaX}/{@link #deltaY} report its increment.
     */
    public boolean drag(int x, int y)
    {
        deltaX = deltaY = 0;
        if (!pressed) { return false; }
        if (!dragged && Math.abs(x - startX) <= DRAG_SLOP
                && Math.abs(y - startY) <= DRAG_SLOP)
        {
            return false;
        }
        dragged = true;
        deltaX = x - lastX;
        deltaY = y - lastY;
        lastX = x;
        lastY = y;
        return true;
    }

    public int deltaX() { return deltaX; }
    public int deltaY() { return deltaY; }
    public boolean isPressed() { return pressed; }
    public boolean isDragging() { return dragged; }

    /**
     * Request a live-drag frame without blocking the pointer callback.
     *
     * MIDP coalesces pending repaint regions. If several pointer events arrive
     * before the display can paint, one frame therefore reads the screen's
     * newest offset instead of forcing every stale intermediate position. In
     * particular, do not call serviceRepaints here: on slow vendor VMs it keeps
     * pointerDragged blocked for a complete paint and builds visible input lag.
     */
    public void repaintFrame(Canvas canvas, int x, int y, int width, int height)
    {
        if (canvas == null || width <= 0 || height <= 0) { return; }
        canvas.repaint(x, y, width, height);
    }

    public int release(int x, int y)
    {
        return release(x, y, System.currentTimeMillis());
    }

    /** Explicit clock overload keeps the timing boundary deterministic in tests. */
    public int release(int x, int y, long now)
    {
        if (!pressed) { return NONE; }
        if (!dragged && (Math.abs(x - startX) > DRAG_SLOP
                || Math.abs(y - startY) > DRAG_SLOP))
        {
            dragged = true;
        }
        int result = dragged ? NONE
                : (now - pressedAt >= LONG_PRESS_MS ? LONG_PRESS : TAP);
        pressed = false;
        dragged = false;
        deltaX = deltaY = 0;
        return result;
    }
}
