package com.fithub.domaintoapplication.domain;

import com.fithub.domaintoapplication.application.UseCase;

/** Violação: domínio conhecendo o caso de uso. */
public class Aggregate {
    public void run() {
        new UseCase().execute();
    }
}
