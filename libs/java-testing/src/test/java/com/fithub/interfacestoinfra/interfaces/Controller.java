package com.fithub.interfacestoinfra.interfaces;

import com.fithub.interfacestoinfra.infrastructure.Adapter;

/** Violação: controller pulando o caso de uso e indo direto ao adaptador. */
public class Controller {
    private final Adapter adapter = new Adapter();

    public String load() {
        return adapter.load();
    }
}
