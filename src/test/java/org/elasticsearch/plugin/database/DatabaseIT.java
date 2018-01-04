package org.elasticsearch.plugin.database;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.test.ESIntegTestCase;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseIT extends ESIntegTestCase {

    private static Logger logger = Loggers.getLogger(DatabaseIT.class);

    private static final int DERBY_PORT = 1527;

    public void testConnection() {

        SecurityManager sm = System.getSecurityManager();

        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }

        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            String connectionUrl = createConnectionUrl(DERBY_PORT);

            try {
                Connection connection = createConnection(connectionUrl);
                assertNotNull("returns a valid connection for " + connectionUrl, connection);
            } catch (Exception e) {
                fail("Error creating connection " + e.getMessage());
            }

            return null;
        });
    }

    private String createConnectionUrl(int port) {
        return "jdbc:derby://localhost:" + port + "/derbyDB;create=true";
    }

    private Connection createConnection(String connectionUrl) throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }
}