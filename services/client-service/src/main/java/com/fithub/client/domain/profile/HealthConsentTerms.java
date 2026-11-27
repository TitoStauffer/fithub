package com.fithub.client.domain.profile;

/**
 * Termo do perfil de saúde que o próprio cliente preenche (DEV-0179, decisão do Tito em 26/11/2026). O dado de saúde é
 * sensível, e a base legal dele é o consentimento específico do cliente (LGPD art. 11, I), colhido na primeira vez e
 * gravado com a versão e o momento. Mudar o texto exige uma versão nova.
 */
public final class HealthConsentTerms {

    public static final String CURRENT_VERSION = "v1";

    public static final String CURRENT_TEXT = """
            Ao salvar, você autoriza o FitHub a guardar as condições de saúde e as observações que você informar, para \
            mostrar a você e aos profissionais com quem você tiver vínculo ativo. Esses dados são seus: você pode \
            mudá-los, exportá-los ou excluí-los a qualquer momento, junto com a sua conta.""";

    private HealthConsentTerms() {}

    public static boolean isCurrent(String version) {
        return CURRENT_VERSION.equals(version);
    }
}
