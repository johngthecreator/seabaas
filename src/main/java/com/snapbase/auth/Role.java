package com.snapbase.auth;
import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    ALL,
    USER,
    ADMIN
}
