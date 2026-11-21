package com.fithub.identity.infrastructure.event;

import com.fithub.identity.domain.service.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implementação de DomainEventPublisher usando Spring's ApplicationEventPublisher.
 * Publicador de eventos de domínio para listeners (ex: auditoria, cache invalidation).
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
