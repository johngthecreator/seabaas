package com.seabaas.factories;

import com.seabaas.controllers.AuthController;
import com.seabaas.repositories.SuperUserRepository;
import com.seabaas.repositories.UserRepository;
import com.seabaas.services.AdminSetupService;
import com.seabaas.services.AuthService;
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