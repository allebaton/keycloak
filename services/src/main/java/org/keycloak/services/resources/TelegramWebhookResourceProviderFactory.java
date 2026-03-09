package org.keycloak.services.resources;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Registers the Telegram webhook endpoint at /realms/{realm}/telegram-webhook.
 * The path segment is determined by {@link #getId()}.
 */
public class TelegramWebhookResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "telegram-webhook";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new TelegramWebhookResource(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
