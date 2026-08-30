package tg.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import tg.api.ChatParticipant;
import tg.api.Peer;

/** Adaptive two-line, windowed member list for groups and channels. */
public class ParticipantListScreen extends Canvas
{
    public interface ActivationListener
    {
        void onParticipantActivated(ChatParticipant participant);
    }

    public interface ViewportListener
    {
        void onParticipantViewportChanged();
    }

    private final Font titleFont = Font.getFont(Font.FACE_PROPORTIONAL,
            Font.STYLE_BOLD, Font.SIZE_SMALL);
    private final Font font = Font.getFont(Font.FACE_PROPORTIONAL,
            Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private final Font metaFont = Font.getFont(Font.FACE_PROPORTIONAL,
            Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private final Metrics metrics = new Metrics();
    private final Peer chat;
    private Theme theme;
    private ChatParticipant[] participants = new ChatParticipant[0];
    private int windowStart;
    private int totalCount;
    private int selected;
    private int top;
    private String title = "Members";
    private String connection = "";
    private String emptyText = "(no members)";
    private ActivationListener activationListener;
    private ViewportListener viewportListener;
    private final TouchGesture touch = new TouchGesture();
    private int touchPaintOffsetY;
    private boolean touchOnRow;

    public ParticipantListScreen(Theme theme, Peer chat)
    {
        this.theme = theme == null ? Theme.byId(Theme.LIGHT) : theme;
        this.chat = chat;
    }

    public Peer chat() { return chat; }

    public void setTheme(Theme value)
    {
        theme = value == null ? Theme.byId(Theme.LIGHT) : value;
        repaint();
    }

    public void setTitle(String value)
    {
        title = value == null ? "Members" : value;
        repaint();
    }

    public void setEmptyText(String value)
    {
        emptyText = value == null ? "(no members)" : value;
        repaint();
    }

    public void setStatus(String value)
    {
        connection = value == null ? "" : value;
        repaint();
    }

    public void setActivationListener(ActivationListener value)
    {
        activationListener = value;
    }

    public void setViewportListener(ViewportListener value)
    {
        viewportListener = value;
    }

    public void setParticipants(ChatParticipant[] values, int firstRow,
                                int allCount, Peer selectedPeer)
    {
        int anchorOffset = selected - top;
        participants = values == null ? new ChatParticipant[0] : values;
        windowStart = Math.max(0, firstRow);
        totalCount = Math.max(participants.length, allCount);
        int found = find(selectedPeer);
        if (found >= 0)
        {
            selected = found;
            if (anchorOffset >= 0) { top = selected - anchorOffset; }
        }
        else if (selected >= participants.length)
        {
            selected = participants.length - 1;
        }
        if (selected < 0) { selected = 0; }
        ensureVisible();
        repaint();
        viewportChanged();
    }

    public ChatParticipant selectedParticipant()
    {
        return selected >= 0 && selected < participants.length
                ? participants[selected] : null;
    }

    public int participantCount() { return participants.length; }
    public int windowStart() { return windowStart; }
    public int totalCount() { return totalCount; }
    public int selectedIndex() { return selected; }
    public int topIndex() { return top; }
    public int visibleRows() { return visibleRowsInternal(); }

    public int lastVisibleIndex()
    {
        updateMetrics();
        int last = top + metrics.visibleRows() - 1;
        return Math.min(participants.length - 1, last);
    }

    protected void sizeChanged(int width, int height)
    {
        metrics.update(width, height, font, metaFont);
        ensureVisible();
        viewportChanged();
    }

    protected void paint(Graphics g)
    {
        updateMetrics();
        UiChrome.background(g, theme, metrics);
        String count = participants.length == 0 ? "0/" + totalCount
                : (windowStart + selected + 1) + "/" + totalCount;
        UiChrome.header(g, theme, metrics, titleFont, title + " " + count,
                connection);
        if (!touch.isDragging()) { ensureVisible(); }
        int first = top;
        int y = metrics.bodyTop + touchPaintOffsetY;
        if (touchPaintOffsetY > 0 && first > 0)
        {
            first--;
            y -= metrics.rowHeight;
        }
        if (participants.length == 0)
        {
            g.setColor(theme.secondaryText);
            g.setFont(font);
            g.drawString(emptyText, metrics.padding, y + metrics.padding,
                    Graphics.TOP | Graphics.LEFT);
            return;
        }
        int clipX = g.getClipX();
        int clipY = g.getClipY();
        int clipW = g.getClipWidth();
        int clipH = g.getClipHeight();
        g.clipRect(0, metrics.bodyTop, metrics.width, metrics.bodyHeight);
        for (int row = 0; row < metrics.visibleRows() + 2; row++)
        {
            int index = first + row;
            if (index >= participants.length || y >= metrics.bodyBottom) { break; }
            ChatParticipant participant = participants[index];
            if (participant == null) { y += metrics.rowHeight; continue; }
            boolean focused = index == selected;
            if (focused)
            {
                g.setColor(theme.selection);
                g.fillRect(0, y, metrics.width, metrics.rowHeight);
            }
            int primary = focused ? theme.selectionText : theme.text;
            int secondary = focused ? theme.selectionText : theme.secondaryText;
            String name = participant.peer == null
                    || participant.peer.title.length() == 0
                            ? "Unknown participant" : participant.peer.title;
            g.setFont(titleFont);
            g.setColor(primary);
            g.drawString(UiChrome.clip(name, titleFont,
                    metrics.width - metrics.padding * 2), metrics.padding,
                    y + metrics.padding, Graphics.TOP | Graphics.LEFT);
            g.setFont(metaFont);
            g.setColor(secondary);
            g.drawString(UiChrome.clip(participant.subtitle(), metaFont,
                    metrics.width - metrics.padding * 2), metrics.padding,
                    y + metrics.padding * 2 + metrics.lineHeight,
                    Graphics.TOP | Graphics.LEFT);
            g.setColor(theme.border);
            g.drawLine(metrics.padding, y + metrics.rowHeight - 1,
                    metrics.width - 1, y + metrics.rowHeight - 1);
            y += metrics.rowHeight;
        }
        g.setClip(clipX, clipY, clipW, clipH);
    }

    protected void keyPressed(int keyCode)
    {
        int action = 0;
        try { action = getGameAction(keyCode); }
        catch (Throwable ignored) { }
        if (action == UP || keyCode == KEY_NUM2) { move(-1); }
        else if (action == DOWN || keyCode == KEY_NUM8) { move(1); }
        else if (action == LEFT || keyCode == KEY_NUM4) { move(-visibleRowsInternal()); }
        else if (action == RIGHT || keyCode == KEY_NUM6) { move(visibleRowsInternal()); }
        else if (action == FIRE || keyCode == KEY_NUM5) { activate(); }
    }

    protected void keyRepeated(int keyCode) { keyPressed(keyCode); }

    protected void pointerPressed(int x, int y)
    {
        touch.press(x, y);
        touchPaintOffsetY = 0;
        touchOnRow = selectAt(y);
    }

    protected void pointerDragged(int x, int y)
    {
        if (!touch.drag(x, y)) { return; }
        touchPaintOffsetY += touch.deltaY();
        updateMetrics();
        int unit = Math.max(1, metrics.rowHeight);
        int max = Math.max(0, participants.length - metrics.visibleRows());
        while (touchPaintOffsetY <= -unit && top < max)
        {
            top++;
            touchPaintOffsetY += unit;
        }
        while (touchPaintOffsetY >= unit && top > 0)
        {
            top--;
            touchPaintOffsetY -= unit;
        }
        repaint();
    }

    protected void pointerReleased(int x, int y)
    {
        if (touch.isPressed()) { pointerDragged(x, y); }
        boolean dragged = touch.isDragging();
        int result = touch.release(x, y);
        if (dragged)
        {
            touchPaintOffsetY = 0;
            clampSelectionToViewport();
            repaint();
            viewportChanged();
        }
        else if (touchOnRow && result != TouchGesture.NONE) { activate(); }
    }

    private int visibleRowsInternal()
    {
        updateMetrics();
        return metrics.visibleRows();
    }

    private void move(int delta)
    {
        if (participants.length == 0) { return; }
        selected += delta;
        if (selected < 0) { selected = 0; }
        if (selected >= participants.length) { selected = participants.length - 1; }
        ensureVisible();
        repaint();
        viewportChanged();
    }

    private void activate()
    {
        ChatParticipant row = selectedParticipant();
        if (activationListener != null && row != null)
        {
            activationListener.onParticipantActivated(row);
        }
    }

    private boolean selectAt(int y)
    {
        updateMetrics();
        if (y < metrics.bodyTop || y >= metrics.bodyBottom) { return false; }
        int index = top + (y - metrics.bodyTop) / metrics.rowHeight;
        if (index < 0 || index >= participants.length) { return false; }
        selected = index;
        repaint();
        viewportChanged();
        return true;
    }

    private void ensureVisible()
    {
        updateMetrics();
        int visible = metrics.visibleRows();
        if (selected < top) { top = selected; }
        if (selected >= top + visible) { top = selected - visible + 1; }
        int max = Math.max(0, participants.length - visible);
        if (top < 0) { top = 0; }
        if (top > max) { top = max; }
    }

    private void clampSelectionToViewport()
    {
        if (participants.length == 0) { selected = 0; return; }
        int last = Math.min(participants.length - 1,
                top + metrics.visibleRows() - 1);
        if (selected < top) { selected = top; }
        if (selected > last) { selected = last; }
    }

    private int find(Peer peer)
    {
        if (peer == null) { return -1; }
        for (int i = 0; i < participants.length; i++)
        {
            Peer candidate = participants[i] == null ? null
                    : participants[i].peer;
            if (candidate != null && candidate.kind == peer.kind
                    && candidate.id == peer.id) { return i; }
        }
        return -1;
    }

    private void updateMetrics()
    {
        metrics.update(getWidth(), getHeight(), font, metaFont);
    }

    private void viewportChanged()
    {
        if (viewportListener != null)
        {
            viewportListener.onParticipantViewportChanged();
        }
    }
}
