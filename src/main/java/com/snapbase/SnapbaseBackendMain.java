package com.snapbase;

import com.snapbase.auth.JwtUtils;
import com.snapbase.auth.Role;
import com.snapbase.controllers.AuthController;
import com.snapbase.controllers.CollectionController;
import com.snapbase.db.Database;
import com.snapbase.factories.AuthFactory;
import com.snapbase.factories.CollectionFactory;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.json.JavalinJackson;
import io.jsonwebtoken.JwtException;

import static io.javalin.apibuilder.ApiBuilder.*;

public class SnapbaseBackendMain {
    public static void main(String[] args) {

        var jdbi = Database.getInstance();
        var authController = AuthFactory.create(jdbi);
        var setupService = AuthFactory.createSetupService(jdbi);
        var collectionController = CollectionFactory.create(jdbi);
        jdbi.withHandle(handle ->
            handle.createScript("""
                    CREATE TABLE IF NOT EXISTS collections ( id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', json_schema TEXT DEFAULT '{}', read_rule TEXT DEFAULT 'ALL', update_rule TEXT DEFAULT 'ALL'); 
                    CREATE TABLE IF NOT EXISTS users ( id TEXT PRIMARY KEY, name TEXT DEFAULT '', email TEXT UNIQUE DEFAULT '', password TEXT DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP);
                    CREATE TABLE IF NOT EXISTS superusers ( id TEXT PRIMARY KEY, name TEXT DEFAULT '', email TEXT UNIQUE DEFAULT '', password TEXT DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP);
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
                var required = ctx.routeRoles();
                if (required.isEmpty() || required.contains(Role.ANYONE))
                    return;

                String header = ctx.header("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    throw new UnauthorizedResponse();
                }

                try {
                    var userRoles = JwtUtils.validate(header.substring(7));

                    if (required.contains(Role.ALL))
                        return;

                    if (userRoles.stream().noneMatch(required::contains)) {
                        throw new ForbiddenResponse();
                    }
                } catch (JwtException e) {
                    throw new UnauthorizedResponse();
                }
            });
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.allowHost("http://localhost:5173", "https://your-frontend-domain.com");
                    it.allowCredentials = true;
                });
            });
            config.routes.apiBuilder(() -> {
                get("/admin/signup", ctx -> ctx.redirect("/admin/signup.html"), Role.ANYONE);
                get("/admin/login", ctx -> ctx.redirect("/admin/login.html"), Role.ANYONE);
                get("/admin/dashboard", ctx -> ctx.redirect("/admin/dashboard.html"), Role.ADMIN);
                get("/admin/setup", authController::getSetup, Role.ANYONE);
                post("/admin/setup", authController::postSetup, Role.ANYONE);
                post("/admin/login", authController::loginAdmin, Role.ANYONE);
                post("/auth/login", authController::login, Role.ANYONE);
                post("/auth/signup", authController::signup, Role.ANYONE);
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