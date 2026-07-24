package com.seabaas.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;

public class Database {
    private static final Jdbi jdbi;
    static {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:seabaas.db");
        var ds = new HikariDataSource(config);
        jdbi = Jdbi.create(ds);
    }
    public static Jdbi getInstance(){
        return jdbi;
    }
}
