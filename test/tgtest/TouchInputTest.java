package tgtest;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;

import org.microemu.device.DeviceFactory;
import org.microemu.device.j2se.J2SEMutableImage;

import tg.api.Dialog;
import tg.api.ForumTopic;
import tg.api.Message;
import tg.api.Peer;
import tg.api.Poll;
import tg.api.PollOption;
import tg.ui.ChatScreen;
import tg.ui.DialogListScreen;
import tg.ui.Metrics;
import tg.ui.PhotoScreen;
import tg.ui.PollScreen;
import tg.ui.ReactionScreen;
import tg.ui.TextScreen;
import tg.ui.TouchContextListener;
import tg.ui.TouchGesture;
import tg.ui.TopicListScreen;

/** Tap, long-press and drag parity for the application-drawn screens. */
public final class TouchInputTest implements Test
{
    public String name() { return "ui/touch-input"; }

    public void run() throws Exception
    {
        DeviceFactory.setDevice(new UiTestDevice("touch", 320, 240));
        gestureBoundaries();
        dialogTapLongPressSwipeAndDpad();
        topicTapOpensTouchedTopic();
        pickersTapTheirTouchedRow();
        chatTapAndSwipe();
        textSwipeAndPhotoTap();
    }

    private static void topicTapOpensTouchedTopic()
    {
        ForumTopic first = new ForumTopic();
        first.id = 10;
        first.title = "First";
        ForumTopic second = new ForumTopic();
        second.id = 20;
        second.title = "Second";
        ExposedTopics topics = new ExposedTopics();
        topics.setTopics(new ForumTopic[] { first, second }, 0, 2, 0);
        final int[] activated = new int[1];
        topics.setActivationListener(new TopicListScreen.ActivationListener()
        {
            public void onTopicActivated(ForumTopic topic)
            {
                activated[0] = topic.id;
            }
        });
        Metrics metrics = metrics();
        int secondRow = metrics.bodyTop + metrics.rowHeight
                + metrics.rowHeight / 2;
        topics.tap(20, secondRow);
        Assert.equal("topic tap focuses its row", 1, topics.selectedIndex());
        Assert.equal("topic tap invokes FIRE open", 20, activated[0]);
    }

    private static void gestureBoundaries()
    {
        TouchGesture touch = new TouchGesture();
        touch.press(10, 10, 1000L);
        Assert.equal("short hold is a tap", TouchGesture.TAP,
                touch.release(12, 12, 1000L + TouchGesture.LONG_PRESS_MS - 1));

        touch.press(10, 10, 2000L);
        Assert.equal("threshold hold is a long press", TouchGesture.LONG_PRESS,
                touch.release(10, 10, 2000L + TouchGesture.LONG_PRESS_MS));

        touch.press(10, 10, 3000L);
        Assert.isTrue("motion beyond slop starts drag", touch.drag(18, 10));
        Assert.equal("drag reports incremental x", 8, touch.deltaX());
        Assert.equal("drag is never also a long press", TouchGesture.NONE,
                touch.release(18, 10, 5000L));

    }

    private static void dialogTapLongPressSwipeAndDpad() throws Exception
    {
        final ExposedDialogs screen = new ExposedDialogs();
        screen.setDialogs(dialogs(6), 0, 6, null);
        final long[] activated = new long[1];
        screen.setActivationListener(new DialogListScreen.ActivationListener()
        {
            public void onDialogActivated(Peer peer) { activated[0] = peer.id; }
        });

        Metrics metrics = metrics();
        int second = metrics.bodyTop + metrics.rowHeight
                + metrics.rowHeight / 2;
        screen.tap(20, second);
        Assert.equal("tap selects the row under the finger", 1,
                screen.selectedIndex());
        Assert.equal("tap invokes the FIRE action", 2, activated[0]);

        // The old route remains live after pointer input.
        screen.press(Canvas.KEY_NUM8);
        screen.press(Canvas.KEY_NUM5);
        Assert.equal("d-pad navigation and FIRE still work", 3, activated[0]);

        int third = metrics.bodyTop + metrics.rowHeight * 2
                + metrics.rowHeight / 2;
        screen.pointerDown(20, third);
        screen.pointerMove(20, third - metrics.rowHeight * 2 - 2);
        screen.pointerUp(20, third - metrics.rowHeight * 2 - 2);
        Assert.isTrue("upward swipe moves the live viewport",
                screen.topIndex() > 0);

        final int[] contexts = new int[1];
        screen.setTouchContextListener(new TouchContextListener()
        {
            public void onTouchContextRequested(Canvas source) { contexts[0]++; }
        });
        int firstVisible = metrics.bodyTop + metrics.rowHeight / 2;
        screen.pointerDown(20, firstVisible);
        Thread.sleep(TouchGesture.LONG_PRESS_MS + 30L);
        screen.pointerUp(20, firstVisible);
        Assert.equal("long press requests item options without opening it",
                1, contexts[0]);
        Assert.equal("long press does not invoke FIRE", 3, activated[0]);
    }

    private static void pickersTapTheirTouchedRow()
    {
        Metrics metrics = metrics();
        int compactRow = Math.max(1,
                metrics.lineHeight + metrics.padding * 2);

        Poll poll = new Poll();
        poll.question = "Choose";
        poll.multipleChoice = true;
        poll.options = new PollOption[] { option("A", 1), option("B", 2) };
        ExposedPoll picker = new ExposedPoll();
        picker.setPoll(poll);
        int secondOption = metrics.bodyTop + compactRow
                + compactRow + compactRow / 2;
        picker.tap(20, secondOption);
        Assert.equal("poll tap focuses second option", 1,
                picker.selectedIndex());
        Assert.isTrue("poll tap performs the FIRE toggle", picker.isSelected(1));

        final int[] reaction = new int[] { -1 };
        ExposedReactions reactions = new ExposedReactions();
        reactions.setReactions(new String[] { "+", "-" },
                new String[] { "Plus", "Minus" }, null);
        reactions.setActivationListener(new ReactionScreen.ActivationListener()
        {
            public void onReactionSelected(int index) { reaction[0] = index; }
            public void onRemoveAll() { }
            public void onViewReactions() { }
            public void onViewSource() { }
        });
        int secondReaction = metrics.bodyTop + compactRow + compactRow / 2;
        reactions.tap(20, secondReaction);
        Assert.equal("reaction tap selects its row", 1,
                reactions.selectedIndex());
        Assert.equal("reaction tap invokes selection", 1, reaction[0]);
    }

    private static void chatTapAndSwipe()
    {
        ExposedChat chat = new ExposedChat();
        Message[] messages = new Message[30];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = new Message();
            messages[i].id = i + 1;
            messages[i].text = "message " + (i + 1);
        }
        chat.resetMessages(messages);
        final int[] activated = new int[1];
        chat.setActivationListener(new ChatScreen.ActivationListener()
        {
            public void onMessageActivated(int messageId)
            {
                activated[0] = messageId;
            }
        });

        Metrics metrics = metrics();
        for (int y = metrics.bodyTop; y < 240 && activated[0] == 0;
                y += Math.max(1, metrics.lineHeight))
        {
            chat.tap(20, y + metrics.lineHeight / 2);
        }
        Assert.isTrue("a visible message can be tapped", activated[0] != 0);
        Assert.equal("chat tap fires the message it focused", activated[0],
                chat.focusedMessageId());

        int before = chat.topVisibleMessageId();
        int middle = metrics.bodyTop + metrics.bodyHeight / 2;
        chat.pointerDown(20, middle);
        chat.pointerMove(20, middle + metrics.lineHeight * 8);
        chat.pointerUp(20, middle + metrics.lineHeight * 8);
        Assert.isTrue("downward swipe reveals older transcript",
                before != chat.topVisibleMessageId());
    }

    private static void textSwipeAndPhotoTap()
    {
        String[] lines = new String[60];
        for (int i = 0; i < lines.length; i++) { lines[i] = "line " + i; }
        Metrics metrics = metrics();
        int middle = metrics.bodyTop + metrics.bodyHeight / 2;

        ExposedText live = new ExposedText(lines);
        J2SEMutableImage beforeImage = new J2SEMutableImage(320, 240);
        live.render(beforeImage.getGraphics());
        live.pointerDown(20, middle);
        live.pointerMove(20, middle - metrics.lineHeight / 2 - 1);
        J2SEMutableImage duringImage = new J2SEMutableImage(320, 240);
        live.render(duringImage.getGraphics());
        Assert.equal("partial drag has not discretely changed the line yet",
                0, live.topLine());
        Assert.isTrue("partial drag visibly follows the pointer before release",
                imagesDiffer(beforeImage, duringImage, metrics.bodyTop));
        live.pointerUp(20, middle - metrics.lineHeight / 2 - 1);

        ExposedText text = new ExposedText(lines);
        text.pointerDown(20, middle);
        text.pointerMove(20, middle - metrics.lineHeight * 5);
        text.pointerUp(20, middle - metrics.lineHeight * 5);
        Assert.equal("text swipe scrolls five lines", 5, text.topLine());

        ExposedPhoto photo = new ExposedPhoto();
        photo.setImage(new J2SEMutableImage(320, 480));
        photo.tap(160, 120);
        Assert.equal("photo tap repeats FIRE zoom", 1, photo.zoomMode());
    }

    private static boolean imagesDiffer(J2SEMutableImage first,
                                        J2SEMutableImage second, int fromY)
    {
        int[] a = new int[320 * 240];
        int[] b = new int[320 * 240];
        first.getRGB(a, 0, 320, 0, 0, 320, 240);
        second.getRGB(b, 0, 320, 0, 0, 320, 240);
        for (int y = fromY; y < 240; y++)
        {
            for (int x = 0; x < 320; x++)
            {
                if (a[y * 320 + x] != b[y * 320 + x]) { return true; }
            }
        }
        return false;
    }

    private static Metrics metrics()
    {
        Font small = Font.getFont(Font.FACE_PROPORTIONAL,
                Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Metrics metrics = new Metrics();
        metrics.update(320, 240, small, small);
        return metrics;
    }

    private static Dialog[] dialogs(int count)
    {
        Dialog[] out = new Dialog[count];
        for (int i = 0; i < count; i++)
        {
            out[i] = new Dialog();
            out[i].peer = new Peer(Peer.USER, i + 1);
            out[i].peer.title = "chat " + (i + 1);
        }
        return out;
    }

    private static PollOption option(String text, int token)
    {
        PollOption out = new PollOption();
        out.text = text;
        out.option = new byte[] { (byte) token };
        return out;
    }

    private static final class ExposedDialogs extends DialogListScreen
    {
        ExposedDialogs() { super(null); }
        void press(int key) { keyPressed(key); }
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
        void pointerDown(int x, int y) { pointerPressed(x, y); }
        void pointerMove(int x, int y) { pointerDragged(x, y); }
        void pointerUp(int x, int y) { pointerReleased(x, y); }
    }

    private static final class ExposedPoll extends PollScreen
    {
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
    }

    private static final class ExposedTopics extends TopicListScreen
    {
        ExposedTopics() { super(null, new Peer(Peer.CHANNEL, 99)); }
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
    }

    private static final class ExposedReactions extends ReactionScreen
    {
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
    }

    private static final class ExposedChat extends ChatScreen
    {
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
        void pointerDown(int x, int y) { pointerPressed(x, y); }
        void pointerMove(int x, int y) { pointerDragged(x, y); }
        void pointerUp(int x, int y) { pointerReleased(x, y); }
    }

    private static final class ExposedText extends TextScreen
    {
        ExposedText(String[] lines) { super("Text", lines); }
        void render(javax.microedition.lcdui.Graphics graphics)
        {
            paint(graphics);
        }
        void pointerDown(int x, int y) { pointerPressed(x, y); }
        void pointerMove(int x, int y) { pointerDragged(x, y); }
        void pointerUp(int x, int y) { pointerReleased(x, y); }
    }

    private static final class ExposedPhoto extends PhotoScreen
    {
        void tap(int x, int y) { pointerPressed(x, y); pointerReleased(x, y); }
    }
}
