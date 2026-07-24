package com.seabaas.services;

import com.seabaas.dtos.LoginDTO;
import com.seabaas.dtos.SignupDTO;
import com.seabaas.models.SuperUserModel;
import com.seabaas.models.UserModel;
import com.seabaas.repositories.SuperUserRepository;
import com.seabaas.repositories.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final SuperUserRepository superUserRepository;

    public AuthService(UserRepository userRepository, SuperUserRepository superUserRepository) {
        this.userRepository = userRepository;
        this.superUserRepository = superUserRepository;
    }

    public String login(LoginDTO login) {
        Optional<UserModel> user = this.userRepository.findByEmail(login.email());
        if (user.isEmpty()) return null;
        if (BCrypt.checkpw(login.password(), user.get().getPassword())) {
            return user.get().getId();
        }
        return null;
    }

    public String loginAdmin(LoginDTO login) {
        Optional<SuperUserModel> admin = this.superUserRepository.findByEmail(login.email());
        if (admin.isEmpty()) return null;
        if (BCrypt.checkpw(login.password(), admin.get().getPassword())) {
            return admin.get().getId();
        }
        return null;
    }

    public void signup(SignupDTO signup) {
        this.userRepository.createUser(signup);
    }
}
