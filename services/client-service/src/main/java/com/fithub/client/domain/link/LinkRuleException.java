package com.fithub.client.domain.link;

/** Regra do vínculo que impede o aceite (BR-07, vínculo consigo mesmo). A mensagem orienta a pessoa. */
public final class LinkRuleException extends RuntimeException {

    public LinkRuleException(String message) {
        super(message);
    }
}
