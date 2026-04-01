package com.dqs.db

import org.scalatest.funsuite.AnyFunSuite

class PostgresWriterTest extends AnyFunSuite {
  test("builds correct JDBC URL and connection properties") {
    val writer = new PostgresWriter("localhost", 5433, "postgres", "postgres", "localdev")
    val props = writer.connectionProperties()

    assert(writer.jdbcUrl == "jdbc:postgresql://localhost:5433/postgres")
    assert(props.getProperty("user") == "postgres")
    assert(props.getProperty("password") == "localdev")
    assert(props.getProperty("driver") == "org.postgresql.Driver")
  }

  test("rejects invalid constructor arguments") {
    assertThrows[IllegalArgumentException] { new PostgresWriter("", 5433, "postgres", "postgres", "pw") }
    assertThrows[IllegalArgumentException] { new PostgresWriter("localhost", 0, "postgres", "postgres", "pw") }
    assertThrows[IllegalArgumentException] { new PostgresWriter("localhost", 5433, "", "postgres", "pw") }
  }
}
