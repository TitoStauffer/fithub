package com.fithub.jpaindomain.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Violação: domínio mapeado com JPA. */
@Entity
public class MappedEntity {
    @Id
    private Long id;
}
