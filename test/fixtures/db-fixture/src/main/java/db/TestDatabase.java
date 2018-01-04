package db;

import org.apache.derby.drda.NetworkServerControl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

/**
 * Creates a local database server that accepts connections over the network
 *
 * Populates the database with values used for testing
 */
public class TestDatabase {

    private static final int DERBY_PORT = 1527;

    private static final String KEYWORD_MARKERS_FILENAME = "test-keywordmarkers.txt";
    private static final String STEMMER_OVERRIDES_FILENAME = "test-stemmeroverrides.txt";
    private static final String STOPWORD_FILENAME = "test-stopwords.txt";
    private static final String SYNONYMS_FILENAME = "test-synonyms.txt";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting TestDatabase with args " + Arrays.asList(args));
        System.out.println(new java.util.Date());

        if (args.length != 1) {
            throw new IllegalArgumentException("TestDatabase <logDirectory>");
        }

        Path dir = Paths.get(args[0]);
        writeRequiredTestFiles(dir);

        startDatabase();
        loadTestData();

        // the fixture thread is required to exist during the integration tests
        // wait forever, until you kill me
        Thread.sleep(Long.MAX_VALUE);
    }

    /*
     * Two files are required to exist for any AntFixture: pid and ports
     */
    private static void writeRequiredTestFiles(Path dir) throws Exception {
        // write pid file
        Path tmp = Files.createTempFile(dir, null, null);
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Files.write(tmp, Collections.singleton(pid));
        Files.move(tmp, dir.resolve("pid"), StandardCopyOption.ATOMIC_MOVE);

        // write port file
        tmp = Files.createTempFile(dir, null, null);
        InetSocketAddress bound = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        if (bound.getAddress() instanceof Inet6Address) {
            Files.write(tmp, Collections.singleton("[" + bound.getHostString() + "]:" + bound.getPort()));
        } else {
            Files.write(tmp, Collections.singleton(bound.getHostString() + ":" + bound.getPort()));
        }
        Files.move(tmp, dir.resolve("ports"), StandardCopyOption.ATOMIC_MOVE);
    }

    private static void startDatabase() throws Exception {
        System.out.println("Starting database");
        NetworkServerControl server = new NetworkServerControl(InetAddress.getByName("localhost"), DERBY_PORT);
        server.start(new PrintWriter(System.out, true)); // here we direct logging to stdout
        System.out.println("Database started");

        int maxRetries = 200;
        int sleepMs = 250;
        boolean success = false;

        for (int i = 0; i < maxRetries; ++i) {
            try {
                System.out.println("Attempting to ping...");
                server.ping();
                success = true;
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            Thread.sleep(sleepMs);
        }

        if (success) {
            System.out.println("Database connected");
        } else {
            System.err.println("Could not establish connection to the database server");
        }
    }

    private static Connection createConnection() {
        String connectionUrl = createConnectionUrl(DERBY_PORT);
        System.out.format("Connect to %s\n", connectionUrl);

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(connectionUrl);
        } catch (SQLException e) {
            System.err.format("Could not connect to database: [%s] Error code: %s\n",
                    e.getMessage(), e.getErrorCode());
        }

        return connection;
    }

    private static String createConnectionUrl(int port) {
        return "jdbc:derby://localhost:" + port + "/derbyDB;create=true";
    }

    private static void loadTestData() {
        try {
            Connection connection = createConnection();

            populateKeywordMarkers(connection);
            populateStemmerOverrides(connection);
            populateStopwords(connection);
            populateSynonyms(connection);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void populateKeywordMarkers(Connection connection) throws SQLException {
        String createTableSQL = "CREATE TABLE KEYWORD_MARKERS(\n" +
                "   ID INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),\n" +
                "   KEYWORD_MARKER VARCHAR(50) NOT NULL\n" +
                ")";

        String insertSqlPattern = "insert into keyword_markers (keyword_marker) values ('%s')";
        populate("keyword_markers", connection, createTableSQL, insertSqlPattern, KEYWORD_MARKERS_FILENAME);
    }

    private static void populateStemmerOverrides(Connection connection) throws SQLException {
        String createTableSQL = "CREATE TABLE STEMMER_OVERRIDES(\n" +
                "   ID INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),\n" +
                "   STEMMER_OVERRIDE VARCHAR(50) NOT NULL\n" +
                ")";

        String insertSqlPattern = "insert into stemmer_overrides (stemmer_override) values ('%s')";
        populate("stemmer_overrides", connection, createTableSQL, insertSqlPattern, STEMMER_OVERRIDES_FILENAME);
    }

    private static void populateStopwords(Connection connection) throws SQLException {
        String createTableSQL = "CREATE TABLE STOPWORDS(\n" +
                "   ID INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),\n" +
                "   STOPWORD VARCHAR(50) NOT NULL\n" +
                ")";

        String insertSqlPattern = "insert into stopwords (stopword) values ('%s')";
        populate("stopwords", connection, createTableSQL, insertSqlPattern, STOPWORD_FILENAME);
    }

    private static void populateSynonyms(Connection connection) throws SQLException {
        String createTableSQL = "CREATE TABLE SYNONYMS(\n" +
                "   ID INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),\n" +
                "   SYNONYM VARCHAR(50) NOT NULL\n" +
                ")";

        String insertSqlPattern = "insert into synonyms (synonym) values ('%s')";
        populate("synoynms", connection, createTableSQL, insertSqlPattern, SYNONYMS_FILENAME);
    }

    private static void populate(String type, Connection connection, String createTableSQL, String
            insertSqlPattern, String
            dataFilename)
            throws SQLException {
        System.out.format("populate %s\n", type);

        Statement statement = connection.createStatement();
        statement.execute(createTableSQL);

        InputStream terms = TestDatabase.class.getResourceAsStream(dataFilename);
        BufferedReader buffer = new BufferedReader(new InputStreamReader(terms));

        buffer.lines().forEach( line -> {
            try {
                System.out.format("%s: %s\n", type, line.trim());
                statement.addBatch(String.format(insertSqlPattern, line.trim()));
            } catch (SQLException e) {
                System.err.format("Could not update database: [%s] Error code: %s\n",
                        e.getMessage(), e.getErrorCode());
            }
        });

        statement.executeBatch();
        statement.close();
    }
}