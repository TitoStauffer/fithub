package com.fithub.training.domain.plan;

/** A operação não cabe no estado atual da ficha (por exemplo, publicar a que já foi publicada). */
public final class PlanStateException extends RuntimeException {

    public PlanStateException(String message) {
        super(message);
    }
}
