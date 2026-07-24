package com.seabaas.repositories;

import com.seabaas.dtos.UserSignupDto;
import com.seabaas.models.UserModel;
import com.seabaas.utils.SqlUtils;
import org.jdbi.v3.core.Jdbi;
import org.mindrot.jbcrypt.BCrypt;

public class UserRepository {
    private final Jdbi jdbi;

    public UserRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void createUser(UserSignupDto user){
        String passwordHash = BCrypt.hashpw(user.password(), BCrypt.gensalt(12));
        String id = SqlUtils.generateId();
        this.jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO users ('id', 'name', 'email', 'password') VALUES (:id, :name, :email, :password)")
                .bind("id", id)
                .bind("name", user.name())
                .bind("email", user.email())
                .bind("password", passwordHash)
                .execute()
        );
    }

    public UserModel findByEmail(String email){
        return this.jdbi.withHandle(handle -> {
            return handle.createQuery("SELECT * FROM users WHERE email = :email")
                    .bind("email", email)
                    .mapToBean(UserModel.class)
                    .one();
        });
    }
}
