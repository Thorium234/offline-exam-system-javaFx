package com.zaraki.exams.database;

import com.zaraki.exams.TestDbConfig;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseEngineTest {

    @BeforeAll
    static void setup() {
        TestDbConfig.init();
    }

    @Test
    void getInstance_returnsSingleton() {
        DatabaseEngine instance1 = DatabaseEngine.getInstance();
        DatabaseEngine instance2 = DatabaseEngine.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void getConnection_returnsValidConnection() {
        DatabaseEngine db = DatabaseEngine.getInstance();
        try (var conn = db.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    void validateFilterColumn_allowsFormAndStream() {
        assertEquals("form", DatabaseEngine.validateFilterColumn("form"));
        assertEquals("stream", DatabaseEngine.validateFilterColumn("stream"));
        assertEquals("", DatabaseEngine.validateFilterColumn(""));
    }

    @Test
    void validateFilterColumn_rejectsInvalidColumn() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.validateFilterColumn("id"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.validateFilterColumn("password"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.validateFilterColumn("' OR 1=1 --"));
    }
}
