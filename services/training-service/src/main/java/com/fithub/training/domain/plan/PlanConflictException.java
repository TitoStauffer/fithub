package com.fithub.training.domain.plan;

/** Outra edição da ficha chegou antes: a versão em que esta se baseou não é mais a atual (DEV-0061). */
public final class PlanConflictException extends RuntimeException {

    public PlanConflictException() {
        super("A ficha mudou desde que você a abriu. Recarregue antes de editar.");
    }
}
