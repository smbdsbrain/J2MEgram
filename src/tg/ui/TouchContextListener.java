package tg.ui;

import javax.microedition.lcdui.Canvas;

/** Receives a long press after its Canvas has focused the item under it. */
public interface TouchContextListener
{
    void onTouchContextRequested(Canvas source);
}
