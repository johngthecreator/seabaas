package com.snapbase.services;

import com.snapbase.models.SuperUserModel;
import com.snapbase.repositories.SuperUserRepository;
import com.snapbase.utils.SqlUtils;
import org.mindrot.jbcrypt.BCrypt;

public class AdminSetupService {
    private static String setupCode;
    private static final Object lock = new Object();

    private final SuperUserRepository repo;

    public AdminSetupService(SuperUserRepository repo) {
        this.repo = repo;
    }

    public String getSetupCode() {
        synchronized (lock) {
            if (setupCode == null && repo.count() == 0) {
                setupCode = SqlUtils.generateId() + SqlUtils.generateId() + "se";
            }
            return setupCode;
        }
    }

    public SuperUserModel setup(String email, String name, String password, String code) {
        synchronized (lock) {
            if (setupCode == null || !setupCode.equals(code)) {
                throw new IllegalArgumentException("Invalid or expired setup code");
            }
            if (repo.count() > 0) {
                throw new IllegalArgumentException("Super admin already configured");
            }

            var superUser = new SuperUserModel();
            superUser.setId(SqlUtils.generateId());
            superUser.setEmail(email);
            superUser.setName(name);
            superUser.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(12)));

            repo.save(superUser);
            setupCode = null;
            return superUser;
        }
    }
}
