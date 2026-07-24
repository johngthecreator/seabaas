package com.seabaas.controllers;

import com.seabaas.auth.JwtUtils;
import com.seabaas.auth.Role;
import com.seabaas.dtos.CollectionDto;
import com.seabaas.dtos.LoginDto;
import com.seabaas.dtos.UserSignupDto;
import com.seabaas.services.AdminSetupService;
import com.seabaas.services.AuthService;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Set;

public class AuthController {
    private final AuthService authService;
    private final AdminSetupService setupService;

    public AuthController(AuthService authService, AdminSetupService setupService){
        this.authService = authService;
        this.setupService = setupService;
    }

    public void signup(Context ctx){
        var dto = ctx.bodyValidator(UserSignupDto.class).get();
        authService.signup(dto);

        ctx.status(200).json(
                Map.of("data", Map.of(
                        "status", "success",
                        "message", "Account Created"
                ))
        );
    }

    public void login(Context ctx){
        var dto = ctx.bodyValidator(LoginDto.class).get();
        var result = authService.login(dto);
        if (result == null){
            ctx.status(200).json(
                    Map.of("data", Map.of(
                            "status", "failure",
                            "message",  "Authentication Failed"
                    ))
            );
        }else{
            ctx.status(200).json(
                    Map.of("data", Map.of(
                            "status", "success",
                            "token", JwtUtils.generate(result, Set.of(Role.USER))
                            ))
            );
        }
    }

    public void getSetup(Context ctx) {
        String code = setupService.getSetupCode();
        ctx.status(200).json(Map.of("code", code != null ? code : ""));
    }

    public void postSetup(Context ctx) {
        var body = ctx.bodyAsClass(Map.class);
        String email = (String) body.get("email");
        String name = (String) body.get("name");
        String password = (String) body.get("password");
        String code = (String) body.get("code");

        try {
            var admin = setupService.setup(email, name, password, code);
            ctx.status(201).json(Map.of(
                "status", "success",
                "data", Map.of("id", admin.getId(), "email", admin.getEmail()),
                "token", JwtUtils.generate(admin.getId(), Set.of(Role.ADMIN, Role.USER))
            ));
        } catch (IllegalArgumentException e) {
            ctx.status(403).json(Map.of("status", "failure", "message", e.getMessage()));
        }
    }

    public void loginAdmin(Context ctx) {
        var dto = ctx.bodyValidator(LoginDto.class).get();
        var result = authService.loginAdmin(dto);
        if (result == null){
            ctx.status(401).json(Map.of("status", "failure", "message", "Invalid credentials"));
        }else{
            ctx.status(200).json(Map.of(
                "status", "success",
                "token", JwtUtils.generate(result, Set.of(Role.ADMIN, Role.USER)),
                "data", Map.of("id", result)
            ));
        }
    }
}
