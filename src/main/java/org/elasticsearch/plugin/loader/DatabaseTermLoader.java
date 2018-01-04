package org.elasticsearch.plugin.loader;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.index.IndexSettings;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseTermLoader implements TermLoader {

    private static final String[] REQUIRED_SETTINGS_ARRAY = {"jdbc.driver", "jdbc.url"};
    private static final HashSet<String> REQUIRED_SETTINGS = Sets.newHashSet(REQUIRED_SETTINGS_ARRAY);
    private static boolean LOADED = false;
    private static final Logger CLASS_LOGGER = Loggers.getLogger(DatabaseTermLoader.class);

    private final Logger logger;

    // General plugin settings
    private final String driver;
    private final String url;

    // TODO: use user/password when required/defined
    private final String user;
    private final String password;
    private final int fetchsize;

    // filter level settings
    private final String query;
    private final String[] params;

    public DatabaseTermLoader(IndexSettings indexSettings, Settings settings, String prefix) {
        logger = Loggers.getLogger(getClass(), settings);

        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }

        Settings pluginSettings = indexSettings.getSettings().getByPrefix(prefix);
        if (!pluginSettings.isEmpty() && pluginSettings.keySet().containsAll(REQUIRED_SETTINGS)) {
            driver = pluginSettings.get("jdbc.driver");
            url = pluginSettings.get("jdbc.url");
            user = pluginSettings.get("jdbc.user");
            password = pluginSettings.get("jdbc.password");
            fetchsize = pluginSettings.getAsInt("jdbc.fetchsize", 100);

            // filter level settings
            query = settings.get("query", null);
            params = settings.getAsArray("params");

            logger.info("load with driver:{} url:{} user:{} password:{}  query:{}", driver, url, user,
                    password, query);
        } else {
            Set<String> expected = new HashSet<>(REQUIRED_SETTINGS);
            expected.removeAll(pluginSettings.keySet());
            String error = "Required " + prefix + " settings are not defined: " + expected;
            throw new IllegalArgumentException(error);
        }
    }

    public Collection<String> loadTerms() {
        List<String> termList = new ArrayList<>();

        SecurityManager sm = System.getSecurityManager();

        if (sm != null) {
            // unprivileged code such as scripts do not have SpecialPermission
            sm.checkPermission(new SpecialPermission());
        }
        AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
            try {
                loadDriver(driver);

                Connection connection = null;
                PreparedStatement statement = null;
                ResultSet resultSet = null;

                try {
                    connection = DriverManager.getConnection(url);
                    statement = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

                    for (int i = 0; i < params.length; i++) {
                        statement.setString(i + 1, params[i]);
                    }

                    logger.debug("query: {} ", query);

                    statement.setFetchSize(fetchsize);
                    statement.setQueryTimeout(60 * 20);
                    statement.executeQuery();
                    resultSet = statement.getResultSet();

                    if (resultSet != null) {
                        while (resultSet.next()) {
                            String term = resultSet.getString(1).trim();
                            logger.debug("next term: {}", term);
                            if ((term.length() == 0) || term.charAt(0) == '#') {
                                continue;
                            }
                            termList.add(term);
                        }

                    }
                } finally {
                    if (resultSet != null) {
                        try {
                            resultSet.close();
                        } catch (SQLException e) { /* ignored */}
                    }
                    if (statement != null) {
                        try {
                            statement.close();
                        } catch (SQLException e) { /* ignored */}
                    }
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException e) { /* ignored */}
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });


        logger.debug("loaded {} terms", termList.size());
        return termList;
    }

    private static synchronized void loadDriver(String driver) throws ClassNotFoundException {
        if (!LOADED) {
            CLASS_LOGGER.debug("Load driver {}", driver);
            Class.forName(driver);
            LOADED = true;
        } else {
            CLASS_LOGGER.debug("Driver {} already loaded", driver);
        }
    }
}
