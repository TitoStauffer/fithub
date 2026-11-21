package com.fithub.identity.domain.service;

/**
 * Publicador de eventos de domínio.
 * Permite que agregados emitam eventos sem acoplamento a infraestrutura.
 */
public interface DomainEventPublisher {
    void publish(Object event);
}
