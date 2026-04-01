package com.dqs.db

import org.scalatest.funsuite.AnyFunSuite

class PostgresWriterTest extends AnyFunSuite {
  test("creates valid JDBC connection string from env vars") {
    val writer = new PostgresWriter("localhost", 5433, "postgres", "postgres", "localdev")
    val props = writer.connectionProperties()

    assert(writer.jdbcUrl == "jdbc:postgresql://localhost:5433/postgres")
    assert(props.getProperty("user") == "postgres")
    assert(props.getProperty("password") == "localdev")
    assert(props.getProperty("driver") == "org.postgresql.Driver")
  }
}
