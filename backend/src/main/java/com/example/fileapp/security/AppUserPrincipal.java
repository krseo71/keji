package com.example.fileapp.security;

import com.example.fileapp.user.domain.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;

@Getter
@RequiredArgsConstructor
public class AppUserPrincipal implements Principal {
    private final Long id;
    private final String username;
    private final Role role;

    @Override
    public String getName() {
        return username;
    }
}
