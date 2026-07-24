package com.seabaas.services;

import com.seabaas.dtos.LoginDto;
import com.seabaas.dtos.UserSignupDto;
import com.seabaas.models.SuperUserModel;
import com.seabaas.models.UserModel;
import com.seabaas.repositories.SuperUserRepository;
import com.seabaas.repositories.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final SuperUserRepository superUserRepository;

    public AuthService(UserRepository userRepository, SuperUserRepository superUserRepository){
        this.userRepository = userRepository;
        this.superUserRepository = superUserRepository;
    }

    public String login(LoginDto userLogin){
        UserModel user = this.userRepository.findByEmail(userLogin.email());
        var isCorrectPassword = BCrypt.checkpw(userLogin.password(), user.getPassword());
        if(isCorrectPassword){
            return user.getId();
        }
        return null;
    }

    public String loginAdmin(LoginDto adminLogin){
        Optional<SuperUserModel> admin = this.superUserRepository.findByEmail(adminLogin.email());
        if (admin.isEmpty()) return null;
        if (BCrypt.checkpw(adminLogin.password(), admin.get().getPassword())){
            return admin.get().getId();
        }
        return null;
    }

    public void signup(UserSignupDto userSignup){
        this.userRepository.createUser(userSignup);
    }
}
