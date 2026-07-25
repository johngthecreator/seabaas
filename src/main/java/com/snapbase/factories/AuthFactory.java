package com.snapbase.factories;

import com.snapbase.controllers.AuthController;
import com.snapbase.repositories.SuperUserRepository;
import com.snapbase.repositories.UserRepository;
import com.snapbase.services.AdminSetupService;
import com.snapbase.services.AuthService;
import org.jdbi.v3.core.Jdbi;


public class AuthFactory {
    public static AuthController create(Jdbi jdbi){
        var userRepo = new UserRepository(jdbi);
        var superUserRepo = new SuperUserRepository(jdbi);
        var authService = new AuthService(userRepo, superUserRepo);
        var setupService = new AdminSetupService(superUserRepo);

        return new AuthController(authService, setupService);
    }

    public static AdminSetupService createSetupService(Jdbi jdbi) {
        var superUserRepo = new SuperUserRepository(jdbi);
        return new AdminSetupService(superUserRepo);
    }
}