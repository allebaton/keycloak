package org.keycloak.authentication.authenticators.browser.telegram;

import org.junit.Assert;
import org.junit.Test;

public class TelegramPushStatusStoreTest {

    private static final long TTL = 60_000L; // 60 s

    @Test
    public void testAddGetRemove() {
        String token = "abc123";
        String root = "root-1";
        String tab = "tab-2";
        String bot = "bot-token";
        TelegramPushStatusStore.add(token, root, tab, bot, TTL);
        TelegramPushStatusStore.PushInfo info = TelegramPushStatusStore.get(token);
        Assert.assertNotNull(info);
        Assert.assertEquals(root, info.rootId);
        Assert.assertEquals(tab, info.tabId);
        Assert.assertEquals(bot, info.botToken);

        TelegramPushStatusStore.PushInfo removed = TelegramPushStatusStore.remove(token);
        Assert.assertEquals(root, removed.rootId);
        Assert.assertNull(TelegramPushStatusStore.get(token));
    }

    @Test
    public void testOverwrite() {
        String token = "tok";
        TelegramPushStatusStore.add(token, "a", "1", "botA", TTL);
        TelegramPushStatusStore.add(token, "b", "2", "botB", TTL);
        TelegramPushStatusStore.PushInfo latest = TelegramPushStatusStore.get(token);
        Assert.assertEquals("b", latest.rootId);
        Assert.assertEquals("2", latest.tabId);
        Assert.assertEquals("botB", latest.botToken);
        TelegramPushStatusStore.remove(token);
    }

    @Test
    public void testExpiredEntryReturnsNull() {
        String token = "expired-tok";
        // add with already-elapsed TTL
        TelegramPushStatusStore.add(token, "r", "t", "b", -1L);
        Assert.assertNull("expired entry must not be returned", TelegramPushStatusStore.get(token));
        Assert.assertNull("expired entry must not be returned on remove", TelegramPushStatusStore.remove(token));
    }
}
