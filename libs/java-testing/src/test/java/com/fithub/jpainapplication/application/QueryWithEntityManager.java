package com.fithub.jpainapplication.application;

import jakarta.persistence.EntityManager;

/** Violação: caso de uso falando direto com JPA. */
public class QueryWithEntityManager {
    private EntityManager entityManager;

    public Object find(long id) {
        return entityManager.find(Object.class, id);
    }
}
