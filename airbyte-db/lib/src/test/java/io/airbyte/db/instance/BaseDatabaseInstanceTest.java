/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.airbyte.db.Database;
import io.airbyte.db.factory.DSLContextFactory;
import io.airbyte.db.factory.DataSourceFactory;
import io.airbyte.test.utils.DatabaseConnectionHelper;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class BaseDatabaseInstanceTest {

  private static final String DATABASE_NAME = "airbyte_test_database";
  private static final String TABLE_NAME = "test_table";
  private static final String INIT_SCHEMA = "CREATE TABLE IF NOT EXISTS TEST_TABLE(\n"
      + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,\n"
      + "value VARCHAR(50));";

  private static PostgreSQLContainer<?> container;

  @BeforeAll
  public static void dbSetup() {
    container = new PostgreSQLContainer<>("postgres:13-alpine")
        .withDatabaseName(DATABASE_NAME)
        .withUsername("docker")
        .withPassword("docker");
    container.start();
  }

  @AfterAll
  public static void dbDown() {
    container.close();
  }

  private Database database;
  private DataSource dataSource;
  private DSLContext dslContext;

  @BeforeEach
  void createDatabase() {
    dataSource = DatabaseConnectionHelper.createDataSource(container);
    dslContext = DSLContextFactory.create(dataSource, SQLDialect.POSTGRES);
    database = Database.createWithRetry(dslContext, BaseDatabaseInstance.isDatabaseConnected(DATABASE_NAME));
  }

  @AfterEach
  void tearDown() throws Exception {
    database.transaction(ctx -> ctx.execute(String.format("DROP TABLE IF EXISTS %s;", TABLE_NAME)));
    dslContext.close();
    DataSourceFactory.close(dataSource);
  }

  @Test
  public void testIsDatabaseConnected() {
    assertTrue(BaseDatabaseInstance.isDatabaseConnected(DATABASE_NAME).apply(database));
  }

  @Test
  public void testHasTable() throws Exception {
    // Table does not exist
    assertFalse(database.query(ctx -> BaseDatabaseInstance.hasTable(ctx, TABLE_NAME)).booleanValue());

    // Create table
    database.transaction(ctx -> ctx.execute(INIT_SCHEMA));

    // Table exists
    assertTrue(database.query(ctx -> BaseDatabaseInstance.hasTable(ctx, TABLE_NAME)).booleanValue());
  }

  @Test
  public void testHasData() throws Exception {
    database.transaction(ctx -> ctx.execute(INIT_SCHEMA));

    // Table is empty
    assertFalse(database.query(ctx -> BaseDatabaseInstance.hasData(ctx, TABLE_NAME)).booleanValue());

    // Write to table
    database.transaction(ctx -> ctx.execute(String.format("INSERT INTO %s (value) values ('test_value')", TABLE_NAME)));

    // Table is no longer empty
    assertTrue(database.query(ctx -> BaseDatabaseInstance.hasData(ctx, TABLE_NAME)).booleanValue());
  }

}
