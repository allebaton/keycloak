<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout section>
    <#if section=="header">
        ${msg("doLogIn")}
    <#elseif section=="form">
        <p>${msg("telegramPushInstructions")!"A push notification has been sent to your Telegram. Approve it to continue."}</p>
        <#-- hidden fields to carry token if necessary -->
        <form id="kc-telegram-push-form" action="${url.loginAction}" method="post">
            <@buttons.loginButton value="${msg("loginContinue")}" />
        </form>
        <p>${msg("telegramPushNotice")!"After approving the request in Telegram click Continue."}</p>
    </#if>
</@layout.registrationLayout>