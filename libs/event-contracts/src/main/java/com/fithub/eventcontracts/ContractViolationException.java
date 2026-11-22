package com.fithub.eventcontracts;

import java.util.List;

/** O payload não cumpre o contrato do evento. A mensagem lista cada violação. */
public final class ContractViolationException extends RuntimeException {

    private final transient EventContract contract;
    private final List<String> violations;

    public ContractViolationException(EventContract contract, List<String> violations) {
        super("Payload fora do contrato " + contract.routingKey() + ":\n- " + String.join("\n- ", violations));
        this.contract = contract;
        this.violations = List.copyOf(violations);
    }

    public EventContract contract() {
        return contract;
    }

    public List<String> violations() {
        return violations;
    }
}
