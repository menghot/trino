package io.trino.plugin.postgresql;

import io.trino.plugin.jdbc.ConnectionFactory;
import io.trino.spi.connector.ConnectorSession;

import java.sql.Connection;
import java.sql.SQLException;

public class PostgreSqlPoolConnectionFactory implements ConnectionFactory {


    @Override
    public Connection openConnection(ConnectorSession session) throws SQLException {
        return null;
    }
}
