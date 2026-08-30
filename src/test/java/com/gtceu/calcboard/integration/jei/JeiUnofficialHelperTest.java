package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class JeiUnofficialHelperTest {

    // Dummy class simulating JEI Unofficial's BookmarkList
    public static class MockBookmarkList {
        public final List<Object> bookmarks = new ArrayList<>();
        public String lastCreatedGroupId = null;
        public String lastCreatedGroupTitle = null;
        public boolean craftingModeEnabled = false;

        public boolean add(Object bookmark) {
            bookmarks.add(bookmark);
            return true;
        }

        public String createGroupForBookmarks(String title, List<?> items) {
            this.lastCreatedGroupTitle = title;
            this.lastCreatedGroupId = "group_1234";
            return lastCreatedGroupId;
        }

        public void setGroupCraftingMode(String groupId, boolean mode) {
            this.craftingModeEnabled = mode;
        }
    }

    // Dummy class simulating JEI Unofficial's BookmarkOverlay
    public static class MockUnofficialOverlay {
        private final MockBookmarkList bookmarkList;
        public boolean panelShown = false;

        public MockUnofficialOverlay(MockBookmarkList bookmarkList) {
            this.bookmarkList = bookmarkList;
        }

        public MockBookmarkList getBookmarkList() {
            return bookmarkList;
        }

        public void showBookmarkPanel() {
            this.panelShown = true;
        }
    }

    public interface MockOverlayInterface extends IBookmarkOverlay {
        MockBookmarkList getBookmarkList();
        void showBookmarkPanel();
    }

    @Test
    public void testIsJeiUnofficialLoadedWithMock() {
        MockBookmarkList mockList = new MockBookmarkList();
        MockUnofficialOverlay mockOverlay = new MockUnofficialOverlay(mockList);

        // Proxy IBookmarkOverlay wrapping MockUnofficialOverlay
        IBookmarkOverlay proxyOverlay = (IBookmarkOverlay) java.lang.reflect.Proxy.newProxyInstance(
            IBookmarkOverlay.class.getClassLoader(),
            new Class<?>[]{IBookmarkOverlay.class, MockOverlayInterface.class},
            (proxy, method, args) -> {
                if ("getBookmarkList".equals(method.getName())) {
                    return mockOverlay.getBookmarkList();
                }
                if ("showBookmarkPanel".equals(method.getName())) {
                    mockOverlay.showBookmarkPanel();
                    return null;
                }
                return null;
            }
        );

        IJeiRuntime mockRuntime = (IJeiRuntime) java.lang.reflect.Proxy.newProxyInstance(
            IJeiRuntime.class.getClassLoader(),
            new Class<?>[]{IJeiRuntime.class},
            (proxy, method, args) -> {
                if ("getBookmarkOverlay".equals(method.getName())) {
                    return proxyOverlay;
                }
                return null;
            }
        );

        Assertions.assertTrue(JeiUnofficialHelper.isJeiUnofficialLoaded(mockRuntime));
    }

    @Test
    public void testIsJeiUnofficialLoadedWithPlainJei() {
        IBookmarkOverlay plainOverlay = (IBookmarkOverlay) java.lang.reflect.Proxy.newProxyInstance(
            IBookmarkOverlay.class.getClassLoader(),
            new Class<?>[]{IBookmarkOverlay.class},
            (proxy, method, args) -> null
        );

        IJeiRuntime mockRuntime = (IJeiRuntime) java.lang.reflect.Proxy.newProxyInstance(
            IJeiRuntime.class.getClassLoader(),
            new Class<?>[]{IJeiRuntime.class},
            (proxy, method, args) -> {
                if ("getBookmarkOverlay".equals(method.getName())) {
                    return plainOverlay;
                }
                return null;
            }
        );

        Assertions.assertFalse(JeiUnofficialHelper.isJeiUnofficialLoaded(mockRuntime));
    }

    @Test
    public void testRegisterBoMGroupNullSafety() {
        Assertions.assertFalse(JeiUnofficialHelper.registerBoMGroup(null, null));
    }
}
