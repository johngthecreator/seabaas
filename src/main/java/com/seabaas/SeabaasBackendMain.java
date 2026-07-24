package com.seabaas;

import com.seabaas.auth.JwtUtils;
import com.seabaas.auth.Role;
import com.seabaas.controllers.AuthController;
import com.seabaas.db.Database;
import com.seabaas.factories.AuthFactory;
import com.seabaas.factories.CollectionFactory;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.json.JavalinJackson;
import io.jsonwebtoken.JwtException;

import static io.javalin.apibuilder.ApiBuilder.*;

public class SeabaasBackendMain {
    public static void main(String[] args) {

        var jdbi = Database.getInstance();
        var authController = AuthFactory.create(jdbi);
        var setupService = AuthFactory.createSetupService(jdbi);
        var collectionController = CollectionFactory.create(jdbi);
        jdbi.withHandle(handle ->
            handle.createScript("""
                    CREATE TABLE IF NOT EXISTS collections ( id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', json_schema TEXT DEFAULT '{}', read_rule TEXT DEFAULT 'ALL', update_rule TEXT DEFAULT 'ALL'); 
                    CREATE TABLE IF NOT EXISTS users ( id TEXT PRIMARY KEY, name TEXT DEFAULT '', email TEXT DEFAULT '', password TEXT DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP);
                    CREATE TABLE IF NOT EXISTS superusers ( id TEXT PRIMARY KEY, name TEXT DEFAULT '', email TEXT DEFAULT '', password TEXT DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP);
                    """
            ).execute()
        );

        var code = setupService.getSetupCode();
        if (code != null) {
            System.out.println("================================================");
            System.out.println("  Admin setup URL:");
            System.out.println("  http://localhost:7070/admin/signup.html?code=" + code);
            System.out.println("================================================");
        }
        Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
            config.routes.beforeMatched(ctx -> {
                // Look up what roles this route requires
                var required = ctx.routeRoles();
                if (required.isEmpty() || required.contains(Role.ALL))
                    return;

                // Protected route: expect "Authorization: Bearer <token>"
                String header = ctx.header("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    throw new UnauthorizedResponse();
                }

                try {
                    var userRoles = JwtUtils.validate(header.substring(7));
                    // User's roles must satisfy at least one required role
                    if (userRoles.stream().noneMatch(required::contains))
                    {
                        throw new ForbiddenResponse();
                    }
                } catch (JwtException e) {
                    // Bad signature or expired token -> 401, not 500
                    throw new UnauthorizedResponse();
                }
            });
            config.routes.apiBuilder(() -> {
                get("/admin/signup", ctx -> ctx.redirect("/admin/signup.html"), Role.ALL);
                get("/admin/login", ctx -> ctx.redirect("/admin/login.html"), Role.ALL);
                get("/admin/dashboard", ctx -> ctx.redirect("/admin/dashboard.html"), Role.ALL);
                get("/admin/setup", authController::getSetup, Role.ALL);
                post("/admin/setup", authController::postSetup, Role.ALL);
                post("/admin/login", authController::loginAdmin, Role.ALL);
                post("/auth/login", authController::login, Role.ALL);
                post("/auth/signup", authController::signup, Role.ALL);
                get("/collections", collectionController::listCollections, Role.ADMIN);
                get("/collections/{name}/schema", collectionController::getSchema, Role.ADMIN);
                post("/collections", collectionController::create, Role.ADMIN);
                get("/collections/{collection}/records", collectionController::findRecords, Role.USER);
                delete("/collections/{collection}/records", collectionController::deleteRecords, Role.USER);
                patch("/collections/{collection}/records", collectionController::updateRecord, Role.USER);
                post("/collections/{collection}/records", collectionController::insertRecord, Role.USER);
            });
        }).start(7070);
    }
}