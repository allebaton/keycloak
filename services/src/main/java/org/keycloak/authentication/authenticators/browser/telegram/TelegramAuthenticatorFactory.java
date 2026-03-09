package org.keycloak.authentication.authenticators.browser.telegram;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class TelegramAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "auth-telegram";
    public static final TelegramAuthenticator SINGLETON = new TelegramAuthenticator();

    // config keys
    public static final String BOT_TOKEN = "telegramBotToken";
    public static final String MODE = "telegramMode"; // otp or push
    public static final String OTP_LENGTH = "otpLength";
    public static final String OTP_TIMEOUT = "otpTimeout";
    public static final String PUSH_TIMEOUT = "pushTimeout";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String BOT_USERNAME = "telegramBotUsername";
    public static final String FALLBACK_MODE = "fallbackMode";
    public static final String WEBHOOK_ENABLED = "webhookEnabled";

    // user attribute key
    public static final String USER_TELEGRAM_ID = "telegram_user_id";

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
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

    @Override
    public String getDisplayType() {
        return "Telegram Authenticator";
    }

    @Override
    public String getReferenceCategory() {
        // this authenticator is not part of a credential category
        return null;
    }

    @Override
    public String getHelpText() {
        return "Second-factor authentication using Telegram bot (OTP or push confirmation).";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false; // setup happens via required action
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property().name(BOT_TOKEN)
                    .label("Telegram Bot Token")
                    .type(ProviderConfigProperty.PASSWORD)
                    .helpText("Token provided by @BotFather for the realm-specific bot. Stored encrypted.")
                    .add()
                .property().name(BOT_USERNAME)
                    .label("Telegram Bot Username")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .helpText("Username of the bot (without @). Used to build the deep-link on the setup page.")
                    .add()
                .property().name(MODE)
                    .label("Mode")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .helpText("OTP: send code to user. PUSH: send approval request with buttons.")
                    .options("OTP", "PUSH")
                    .defaultValue("OTP")
                    .add()
                .property().name(OTP_LENGTH)
                    .label("OTP Length")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("6")
                    .helpText("Number of digits in the generated OTP code.")
                    .add()
                .property().name(OTP_TIMEOUT)
                    .label("OTP Timeout (sec)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("300")
                    .helpText("How long the OTP is valid, in seconds.")
                    .add()
                .property().name(PUSH_TIMEOUT)
                    .label("Push Timeout (sec)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("60")
                    .helpText("How long the push approval request is valid, in seconds.")
                    .add()
                .property().name(MAX_RETRIES)
                    .label("Max Retries")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("3")
                    .helpText("How many times user can retry entering OTP before failure.")
                    .add()
                .property().name(FALLBACK_MODE)
                    .label("Fallback Mode")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .helpText("Behavior when Telegram is unavailable.")
                    .options("NONE", "RECOVERY_CODES", "TOTP")
                    .defaultValue("RECOVERY_CODES")
                    .add()
                .property().name(WEBHOOK_ENABLED)
                    .label("Enable Webhook")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue(false)
                    .helpText("Use webhook callbacks instead of polling (requires public endpoint).")
                    .add()
                .build();
    }
}
