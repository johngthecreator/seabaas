package com.snapbase.repositories;

import com.snapbase.models.SuperUserModel;
import org.jdbi.v3.core.Jdbi;
import java.util.Optional;

public class SuperUserRepository {
    private final Jdbi jdbi;

    public SuperUserRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public int count() {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT COUNT(*) FROM superusers")
                  .mapTo(Integer.TYPE)
                  .one()
        );
    }

    public Optional<SuperUserModel> findByEmail(String email) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM superusers WHERE email = :email")
                  .bind("email", email)
                  .mapToBean(SuperUserModel.class)
                  .findOne()
        );
    }

    public void save(SuperUserModel superUser) {
        jdbi.withHandle(handle ->
            handle.createUpdate("INSERT INTO superusers (id, name, email, password) VALUES (:id, :name, :email, :password)")
                  .bindBean(superUser)
                  .execute()
        );
    }
}
