package org.keycloak.authentication.authenticators.browser.telegram;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.forms.login.LoginFormsPages;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Optional;

public class TelegramSetupRequiredAction implements RequiredActionProvider, RequiredActionFactory {
    public static final String PROVIDER_ID = "telegram-setup";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // not used; action is triggered explicitly when user has no telegram id
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        context.challenge(context.form()
                .setAttribute("telegram", new TelegramFormAttributes(resolveBotUsername(context)))
                .createForm(LoginFormsPages.LOGIN_TELEGRAM_SETUP.name()));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        String telegramId = context.getHttpRequest().getDecodedFormParameters().getFirst("telegramId");
        if (telegramId == null || telegramId.trim().isEmpty()) {
            context.challenge(context.form()
                    .setError("telegramId_required")
                    .setAttribute("telegram", new TelegramFormAttributes(resolveBotUsername(context)))
                    .createForm(LoginFormsPages.LOGIN_TELEGRAM_SETUP.name()));
            return;
        }
        context.getUser().setSingleAttribute(TelegramAuthenticatorFactory.USER_TELEGRAM_ID, telegramId.trim());
        context.success();
    }

    /**
     * Looks up the first Telegram authenticator config in the realm to obtain the bot username.
     * Returns null if no config with a username is found.
     */
    private String resolveBotUsername(RequiredActionContext context) {
        Optional<AuthenticatorConfigModel> found = context.getRealm()
                .getAuthenticatorConfigsStream()
                .filter(cfg -> cfg.getConfig() != null
                        && cfg.getConfig().containsKey(TelegramAuthenticatorFactory.BOT_USERNAME)
                        && !cfg.getConfig().get(TelegramAuthenticatorFactory.BOT_USERNAME).isEmpty())
                .findFirst();
        return found.map(cfg -> cfg.getConfig().get(TelegramAuthenticatorFactory.BOT_USERNAME)).orElse(null);
    }

    /**
     * Simple value holder passed to the FreeMarker template as the {@code telegram} attribute.
     * Provides {@code setupLink} via the public field so FTL can access {@code telegram.setupLink}.
     */
    public static class TelegramFormAttributes {
        public final String setupLink;

        public TelegramFormAttributes(String botUsername) {
            this.setupLink = botUsername != null ? "https://t.me/" + botUsername : "#";
        }
    }

    @Override
    public void close() {
    }

    // RequiredActionFactory methods

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Configure Telegram for 2FA";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }
}
