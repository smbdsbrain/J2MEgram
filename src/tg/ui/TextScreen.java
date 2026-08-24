package tg.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;

/**
 * Scrollable read-only text view.
 *
 * A Form with StringItems would be simpler, but it allocates an Item per line
 * and reflows on every append - unacceptable for a log that can hold a hundred
 * lines on a device whose heap we have not measured. This draws the visible
 * window only, straight from a String[] the caller already owns.
 *
 * Written as a Canvas so the same class works for the diagnostic log, the
 * capability report and the crash log, and so key handling is explicit rather
 * than at the mercy of a device-specific Form implementation.
 */
public class TextScreen extends Canvas
{
    private final String title;
    private String[] source;         // as supplied
    private String[] lines;          // wrapped to the current width
    private int wrappedForWidth = -1;
    private int top;                 // index of the first visible line
    private final Font font;
    private final int lineHeight;
    private final Metrics metrics = new Metrics();
    private Theme theme;
    private final TouchGesture touch = new TouchGesture();
    private TouchContextListener touchContextListener;
    /** Uncommitted pixel part of a live pointer scroll. */
    private int touchPaintOffsetY;

    public TextScreen(String title, String[] lines)
    {
        this(title, lines, Theme.byId(Theme.LIGHT));
    }

    public TextScreen(String title, String[] lines, Theme theme)
    {
        this.title = title;
        this.source = lines == null ? new String[0] : lines;
        this.lines = this.source;
        this.font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        this.lineHeight = Math.max(font.getHeight(), EmojiText.GLYPH);
        this.theme = theme == null ? Theme.byId(Theme.LIGHT) : theme;
    }

    public void setTheme(Theme value)
    {
        theme = value == null ? Theme.byId(Theme.LIGHT) : value;
        repaint();
    }

    public void setTouchContextListener(TouchContextListener value)
    {
        touchContextListener = value;
    }

    public void setLines(String[] newLines)
    {
        this.source = newLines == null ? new String[0] : newLines;
        this.wrappedForWidth = -1;
        this.lines = this.source;
        if (top > maxTop()) { top = maxTop(); }
        repaint();
    }

    public String[] getLines()
    {
        return source;
    }

    /**
     * Wrap to the screen width.
     *
     * Without this a long line is simply clipped at the right edge, and on a
     * device with no other diagnostics that silently hides the end of an error
     * message - which is usually the part that says what went wrong. Wrapping
     * is done here rather than in setLines because the canvas has no width
     * until it is first shown.
     */
    private void rewrapIfNeeded()
    {
        updateMetrics();
        int width = metrics.contentWidth;
        if (width <= 8 || wrappedForWidth == width)
        {
            return;
        }
        wrappedForWidth = width;

        // Two passes so the result array is allocated exactly once.
        int count = 0;
        for (int pass = 0; pass < 2; pass++)
        {
            if (pass == 1)
            {
                lines = new String[count];
                count = 0;
            }
            for (int i = 0; i < source.length; i++)
            {
                String s = source[i] == null ? "" : source[i];
                int start = 0;
                do
                {
                    int end = fitFrom(s, start, width);
                    if (pass == 1) { lines[count] = s.substring(start, end); }
                    count++;
                    start = end;
                }
                while (start < s.length());
            }
        }
        if (top > maxTop()) { top = maxTop(); }
    }

    /** Index one past the last character of {@code s} that fits from {@code start}. */
    private int fitFrom(String s, int start, int width)
    {
        int n = s.length();
        if (start >= n) { return n; }
        int i = start;
        while (i < n)
        {
            int next = EmojiText.nextBoundary(s, i);
            if (EmojiText.substringWidth(s, start, next - start, font) > width)
            {
                return i > start ? i : next;
            }
            i = next;
        }
        return n;
    }

    /** Jump to the newest content - what you want after a log update. */
    public void scrollToEnd()
    {
        top = maxTop();
        repaint();
    }

    protected void paint(Graphics g)
    {
        rewrapIfNeeded();
        int visible = visibleLines();
        String pos = (lines.length == 0)
                ? "empty"
                : ("" + (top + 1) + "-" + Math.min(top + visible, lines.length) + "/" + lines.length);
        UiChrome.background(g, theme, metrics);
        UiChrome.header(g, theme, metrics, font, title, pos);

        g.setColor(theme.text);
        g.setFont(font);
        int first = top;
        int y = metrics.bodyTop + touchPaintOffsetY;
        if (touchPaintOffsetY > 0 && first > 0)
        {
            first--;
            y -= lineHeight;
        }
        int clipX = g.getClipX();
        int clipY = g.getClipY();
        int clipWidth = g.getClipWidth();
        int clipHeight = g.getClipHeight();
        g.clipRect(0, metrics.bodyTop, metrics.width, metrics.bodyHeight);
        for (int i = 0; i < visible + 2; i++)
        {
            int idx = first + i;
            if (idx >= lines.length) { break; }
            if (y >= metrics.bodyBottom) { break; }
            String s = lines[idx];
            if (s != null)
            {
                EmojiText.drawString(g, s, 2, y, font);
            }
            y += lineHeight;
        }
        g.setClip(clipX, clipY, clipWidth, clipHeight);
    }

    protected void sizeChanged(int width, int height)
    {
        metrics.update(width, height, font, font);
        wrappedForWidth = -1;
        if (top > maxTop()) { top = maxTop(); }
    }

    protected void keyPressed(int keyCode)
    {
        int action = 0;
        try { action = getGameAction(keyCode); }
        catch (Throwable ignored) { /* some handsets throw on unmapped keys */ }

        if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2)
        {
            scroll(-1);
        }
        else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8)
        {
            scroll(1);
        }
        else if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4)
        {
            scroll(-visibleLines());
        }
        else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6)
        {
            scroll(visibleLines());
        }
    }

    // Holding a direction should keep scrolling on devices that repeat.
    protected void keyRepeated(int keyCode)
    {
        keyPressed(keyCode);
    }

    protected void pointerPressed(int x, int y)
    {
        touch.press(x, y);
        touchPaintOffsetY = 0;
    }

    protected void pointerDragged(int x, int y)
    {
        if (!touch.drag(x, y)) { return; }
        int delta = touch.deltaY();
        if (delta == 0) { return; }
        touchPaintOffsetY += delta;
        int unit = Math.max(1, lineHeight);
        int steps = touchPaintOffsetY / unit;
        if (steps != 0)
        {
            touchPaintOffsetY -= steps * unit;
            scrollTouch(-steps);
        }
        if ((top == 0 && touchPaintOffsetY > 0)
                || (top == maxTop() && touchPaintOffsetY < 0))
        {
            touchPaintOffsetY = 0;
        }
        touch.repaintFrame(this, 0, metrics.bodyTop,
                metrics.width, metrics.bodyHeight);
    }

    protected void pointerReleased(int x, int y)
    {
        if (touch.isPressed()) { pointerDragged(x, y); }
        boolean dragged = touch.isDragging();
        int result = touch.release(x, y);
        if (dragged)
        {
            settleTouchScroll();
            return;
        }
        if (result == TouchGesture.LONG_PRESS)
        {
            TouchContextListener listener = touchContextListener;
            if (listener != null) { listener.onTouchContextRequested(this); }
        }
    }

    private void settleTouchScroll()
    {
        int unit = Math.max(1, lineHeight);
        if (touchPaintOffsetY <= -unit / 2) { scrollTouch(1); }
        else if (touchPaintOffsetY >= unit / 2) { scrollTouch(-1); }
        touchPaintOffsetY = 0;
        repaint();
    }

    public int topLine() { return top; }

    private void scroll(int delta)
    {
        scrollTouch(delta);
        repaint();
    }

    private void scrollTouch(int delta)
    {
        int max = maxTop();
        top += delta;
        if (top > max) { top = max; }
        if (top < 0) { top = 0; }
    }

    private int visibleLines()
    {
        updateMetrics();
        return metrics.visibleLines();
    }

    private int maxTop()
    {
        int max = lines.length - visibleLines();
        return max < 0 ? 0 : max;
    }

    /** Convenience so callers do not repeat the same wiring. */
    public void withBack(Command back, javax.microedition.lcdui.CommandListener l)
    {
        addCommand(back);
        setCommandListener(l);
    }

    public Displayable asDisplayable()
    {
        return this;
    }

    private void updateMetrics()
    {
        metrics.update(getWidth(), getHeight(), font, font);
    }
}
