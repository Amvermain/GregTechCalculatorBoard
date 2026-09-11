package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.client.gui.BoardScreen;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FavoritesDockWidgetTest {

    @Test
    public void testFavoritesDockLifecycleAndSafety() {
        BoardScreen screen = new BoardScreen();
        FavoritesDockWidget widget = new FavoritesDockWidget(screen);

        boolean initial = widget.isExpanded();
        widget.toggle();
        Assertions.assertEquals(!initial, widget.isExpanded());

        widget.setExpanded(false);
        Assertions.assertFalse(widget.isExpanded());

        Assertions.assertDoesNotThrow(widget::closeFlyout);
        Assertions.assertDoesNotThrow(FavoritesDockWidget::clearCache);

        Assertions.assertFalse(widget.mouseClicked(100, 100, 0));
        Assertions.assertFalse(widget.mouseDragged(100, 100, 0, 5, 5));
        Assertions.assertFalse(widget.mouseReleased(100, 100, 0));
        Assertions.assertFalse(widget.mouseScrolled(100, 100, 1.0));
        Assertions.assertFalse(widget.keyPressed(256, 0, 0));
    }
}
