package com.snapbase;

import com.snapbase.auth.JwtUtils;
import com.snapbase.auth.Role;
import com.snapbase.controllers.AuthController;
import com.snapbase.controllers.CollectionController;
import com.snapbase.db.Database;
import com.snapbase.factories.AuthFactory;
import com.snapbase.factories.CollectionFactory;
import com.snapbase.services.AdminSetupService;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.json.JavalinJackson;
import io.jsonwebtoken.JwtException;

import java.util.function.Consumer;

import static io.javalin.apibuilder.ApiBuilder.*;

public class SnapbaseApp {
    private final AuthController authController;
    private final CollectionController collectionController;

    public SnapbaseApp(){
        var jdbi = Database.getInstance();
        this.authController = AuthFactory.create(jdbi);
        var setupService = AuthFactory.createSetupService(jdbi);
        this.collectionController = CollectionFactory.create(jdbi);
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

    }

    public Javalin start(int port, String host) {
        return start(port, host, config -> {});
    }

    public Javalin start(int port, String host, Consumer<JavalinConfig> extraConfig) {
        return Javalin.create(config -> {
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
                    it.allowHost(host);
                    it.allowCredentials = true;
                });
            });
            config.routes.apiBuilder(() -> {
                get("/admin/signup", ctx -> ctx.redirect("/admin/signup.html"), Role.ANYONE);
                get("/admin/login", ctx -> ctx.redirect("/admin/login.html"), Role.ANYONE);
                get("/admin/dashboard", ctx -> ctx.redirect("/admin/dashboard.html"), Role.ADMIN);
                get("/admin/setup", this.authController::getSetup, Role.ANYONE);
                post("/admin/setup", this.authController::postSetup, Role.ANYONE);
                post("/admin/login", this.authController::loginAdmin, Role.ANYONE);
                post("/auth/login", this.authController::login, Role.ANYONE);
                post("/auth/signup", this.authController::signup, Role.ANYONE);
                get("/collections", this.collectionController::listCollections, Role.ADMIN);
                get("/collections/{name}/schema", this.collectionController::getSchema, Role.ADMIN);
                post("/collections", this.collectionController::create, Role.ADMIN);
                patch("/collections", this.collectionController::update, Role.ADMIN);
                delete("/collections/{name}", this.collectionController::delete, Role.ADMIN);
                get("/collections/{collection}/records", this.collectionController::findRecords, Role.USER);
                delete("/collections/{collection}/records", this.collectionController::deleteRecords, Role.USER);
                patch("/collections/{collection}/records", this.collectionController::updateRecord, Role.USER);
                post("/collections/{collection}/records", this.collectionController::insertRecord, Role.USER);
            });
            extraConfig.accept(config);
        }).start(port);
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7070;
        String host = args.length > 1 ? args[1] : "http://localhost:5173";
        new SnapbaseApp().start(port, host);
    }

}
