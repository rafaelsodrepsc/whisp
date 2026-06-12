package com.whisp.chat.config;

import java.security.Principal;

public record StompPrincipal(String name, String username) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}