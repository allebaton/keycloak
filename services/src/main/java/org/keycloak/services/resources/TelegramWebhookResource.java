package org.keycloak.services.resources;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;
import org.keycloak.authentication.authenticators.browser.telegram.TelegramAuthenticator;
import org.keycloak.authentication.authenticators.browser.telegram.TelegramBotClient;
import org.keycloak.authentication.authenticators.browser.telegram.TelegramPushStatusStore;
import org.keycloak.services.resource.RealmResourceProvider;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

/**
 * JAX-RS resource exposed at /realms/{realm}/telegram-webhook.
 * Receives Telegram Bot API callback_query callbacks for PUSH-mode confirmations.
 * Registered via {@link TelegramWebhookResourceProviderFactory}.
 */
public class TelegramWebhookResource implements RealmResourceProvider {

    private final KeycloakSession session;

    public TelegramWebhookResource(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receive(String body) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = JsonSerialization.readValue(body, Map.class);
            Object cb = payload.get("callback_query");
            if (cb instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> callback = (Map<String, Object>) cb;
                String data = (String) callback.get("data");
                String queryId = (String) callback.get("id");
                if (data != null && data.contains(":")) {
                    boolean approved = data.startsWith("APPROVE");
                    String token = data.substring(data.indexOf(':') + 1);
                    TelegramPushStatusStore.PushInfo info = TelegramPushStatusStore.remove(token);
                    if (info != null) {
                        AuthenticationSessionProvider authProvider =
                                session.getProvider(AuthenticationSessionProvider.class);
                        RootAuthenticationSessionModel root =
                                authProvider.getRootAuthenticationSession(realm, info.rootId);
                        if (root != null && info.tabId != null) {
                            org.keycloak.sessions.AuthenticationSessionModel authSession =
                                    root.getAuthenticationSessions().get(info.tabId);
                            if (authSession != null) {
                                authSession.setAuthNote(TelegramAuthenticator.NOTE_APPROVED,
                                        String.valueOf(approved));
                            }
                        }
                        try {
                            new TelegramBotClient(session, info.botToken)
                                    .answerCallbackQuery(queryId, approved);
                        } catch (TelegramBotClient.TelegramException ignored) {
                        }
                    }
                }
            }
            return Response.ok().build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
