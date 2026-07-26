package com.snapbase.auth;
import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    ANYONE,
    ALL,
    USER,
    ADMIN
}
