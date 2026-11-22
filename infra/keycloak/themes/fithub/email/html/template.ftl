<#--
  Moldura dos e-mails do FitHub (DEV-0026). Estilos em linha e nenhuma imagem ou recurso externo:
  clientes de e-mail bloqueiam conteúdo remoto, e nada pode rastrear a abertura da mensagem.
-->
<#macro emailLayout>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<body style="margin:0;padding:0;background-color:#f7f7f7;font-family:-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;color:#0a0a0a;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f7f7f7;padding:24px 12px;">
        <tr>
            <td align="center">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;">
                    <tr>
                        <td style="padding:0 4px 16px 4px;font-size:22px;font-weight:700;letter-spacing:-0.02em;">FitHub</td>
                    </tr>
                    <tr>
                        <td style="background-color:#ffffff;border:1px solid #e5e5e5;border-radius:10px;padding:24px;font-size:15px;line-height:1.6;">
                            <#nested>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:16px 4px 0 4px;font-size:12px;line-height:1.5;color:#737373;">
                            ${msg("fithubEmailFooter")}
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
</#macro>
