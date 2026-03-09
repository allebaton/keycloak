<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout section>
    <#if section=="header">
        ${msg("configureTelegram")!"Configure Telegram"}
    <#elseif section=="form">
        <p>${msg("telegramSetupInstructions")!"Enter your Telegram chat ID or click the link below to open the bot and send the code shown."}</p>
        <form action="${url.loginAction}" method="post">
            <@field.input name="telegramId" label="${msg("telegramChatId")!"Telegram Chat ID"}" autofocus="true" />
            <@buttons.finishButton />
        </form>
        <p>${msg("telegramOrLink")!"Or click the link to open the bot:"}</p>
        <p><a href="${telegram.setupLink!"#"}" target="_blank">${msg("telegramOpenBot")!"Open Telegram bot"}</a></p>
    </#if>
</@layout.registrationLayout>