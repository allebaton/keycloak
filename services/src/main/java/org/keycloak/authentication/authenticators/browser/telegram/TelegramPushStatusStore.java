package org.keycloak.authentication.authenticators.browser.telegram;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store mapping approval tokens to authentication session coordinates.
 * Entries are added when a PUSH challenge is issued and removed when the Telegram
 * webhook callback arrives or when the entry expires.
 */
public class TelegramPushStatusStore {

    private static final Map<String, PushInfo> map = new ConcurrentHashMap<>();

    public static class PushInfo {
        public final String rootId;
        public final String tabId;
        public final String botToken;
        /** Absolute epoch-millis after which this entry is considered stale. */
        public final long expiresAt;

        public PushInfo(String rootId, String tabId, String botToken, long expiresAt) {
            this.rootId = rootId;
            this.tabId = tabId;
            this.botToken = botToken;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * @param token     approval token stored in the auth session
     * @param rootId    root authentication session id
     * @param tabId     tab id of the authentication session
     * @param botToken  bot token needed to answer callback queries
     * @param ttlMillis time-to-live in milliseconds
     */
    public static void add(String token, String rootId, String tabId, String botToken, long ttlMillis) {
        evictExpired();
        map.put(token, new PushInfo(rootId, tabId, botToken, System.currentTimeMillis() + ttlMillis));
    }

    public static PushInfo remove(String token) {
        PushInfo info = map.remove(token);
        if (info != null && System.currentTimeMillis() > info.expiresAt) {
            return null; // treat expired as not found
        }
        return info;
    }

    public static PushInfo get(String token) {
        PushInfo info = map.get(token);
        if (info == null) return null;
        if (System.currentTimeMillis() > info.expiresAt) {
            map.remove(token);
            return null;
        }
        return info;
    }

    /** Removes all entries whose TTL has elapsed. */
    private static void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PushInfo>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }
    }
}
