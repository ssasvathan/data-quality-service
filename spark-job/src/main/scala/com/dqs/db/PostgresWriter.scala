package com.dqs.db

import java.util.Properties

class PostgresWriter(host: String, port: Int, db: String, user: String, pass: String) {
  require(host.nonEmpty, "host must not be empty")
  require(db.nonEmpty, "db must not be empty")
  require(port > 0 && port <= 65535, s"port must be 1-65535, got: $port")

  val jdbcUrl: String = s"jdbc:postgresql://$host:$port/$db"

  def connectionProperties(): Properties = {
    val props = new Properties()
    props.setProperty("user", user)
    props.setProperty("password", pass)
    props.setProperty("driver", "org.postgresql.Driver")
    props
  }
}
