package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.client.gui.BoardScreen;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LeftActivityBarWidgetTest {

    @Test
    public void testCommunityPopupStateLifecycle() {
        BoardScreen screen = new BoardScreen();
        LeftActivityBarWidget widget = new LeftActivityBarWidget(screen);

        Assertions.assertFalse(widget.isCommunityPopupOpen());

        widget.closeCommunityPopup();
        Assertions.assertFalse(widget.isCommunityPopupOpen());

        Assertions.assertFalse(widget.mouseClicked(-10, 50, 0));
        Assertions.assertFalse(widget.mouseClicked(100, 50, 0));
        Assertions.assertFalse(widget.mouseClicked(10, -50, 0));
    }
}
