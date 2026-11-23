package com.fithub.account.application;

import com.fithub.account.domain.professional.Organization;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.account.domain.professional.Registration;
import com.fithub.commons.ids.UuidV7;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Cadastro do profissional (DEV-0029, FR-03, FR-04). A conta autenticada informa o tipo e o registro;
 * o cadastro e a organização de uma pessoa são gravados juntos, e depois do commit o papel de
 * profissional é atribuído no Keycloak. Se o Keycloak falhar, o cadastro fica com o papel pendente e
 * {@link #assignPendingRoles(int)} tenta de novo.
 */
@Service
public class RegisterProfessional {

    private static final Logger log = LoggerFactory.getLogger(RegisterProfessional.class);

    private final ProfessionalRepository repository;
    private final ProfessionalRoles roles;
    private final TransactionOperations transaction;
    private final Clock clock;

    public RegisterProfessional(
            ProfessionalRepository repository,
            ProfessionalRoles roles,
            TransactionOperations transaction,
            Clock clock) {
        this.repository = repository;
        this.roles = roles;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** O que a pessoa pediu. {@code accountId} vem sempre do token, nunca do corpo da requisição. */
    public record Command(String accountId, Professional.Type type, String number, String category, String state) {}

    /** Cadastro resultante e se ele foi criado agora ou já existia igual. */
    public record Result(Professional professional, boolean created) {}

    /**
     * Cadastra a conta como profissional. Repetir o mesmo pedido devolve o cadastro existente; um pedido
     * diferente para uma conta já cadastrada lança {@link ConflictException}.
     */
    public Result register(Command command) {
        Registration registration =
                Registration.of(command.type().council(), command.number(), command.category(), command.state());
        Optional<Professional> existing = repository.findByAccountId(command.accountId());
        if (existing.isPresent()) {
            return repeated(existing.get(), command.type(), registration);
        }

        var now = clock.instant();
        var organization = new Organization(UuidV7.next(clock), command.accountId(), now);
        var professional = Professional.register(
                UuidV7.next(clock), command.accountId(), command.type(), registration, organization.id(), now);
        try {
            transaction.executeWithoutResult(status -> repository.saveNew(professional, organization));
        } catch (ProfessionalRepository.AlreadyRegisteredException race) {
            // Outro pedido da mesma conta gravou primeiro: vale o que ficou no banco.
            Professional winner =
                    repository.findByAccountId(command.accountId()).orElseThrow(() -> race);
            return repeated(winner, command.type(), registration);
        }
        return new Result(assignRole(professional), true);
    }

    public Optional<Professional> findMine(String accountId) {
        return repository.findByAccountId(accountId);
    }

    /** Tenta de novo a atribuição dos papéis que ficaram pendentes. Devolve quantos foram atribuídos. */
    public int assignPendingRoles(int limit) {
        List<Professional> pending = repository.findPendingRoles(limit);
        return (int) pending.stream()
                .map(this::assignRole)
                .filter(professional -> professional.roleStatus() == Professional.RoleStatus.ASSIGNED)
                .count();
    }

    private Result repeated(Professional existing, Professional.Type type, Registration registration) {
        if (!existing.sameAs(type, registration)) {
            throw new ConflictException();
        }
        // Pedido repetido com o papel ainda pendente: aproveita para tentar atribuir.
        Professional current =
                existing.roleStatus() == Professional.RoleStatus.PENDING ? assignRole(existing) : existing;
        return new Result(current, false);
    }

    private Professional assignRole(Professional professional) {
        try {
            roles.assign(professional.accountId(), professional.type());
        } catch (RuntimeException failure) {
            // Sem dado pessoal no log: só o id interno do cadastro.
            log.warn(
                    "Papel de profissional pendente para o cadastro {}: {}",
                    professional.id(),
                    failure.getClass().getSimpleName());
            return professional;
        }
        repository.markRoleAssigned(professional.id());
        return professional.withRoleAssigned();
    }

    /** A conta já tem um cadastro de profissional diferente do pedido. */
    public static final class ConflictException extends RuntimeException {

        public ConflictException() {
            super("Esta conta já tem um cadastro de profissional com outros dados.");
        }
    }
}
