package com.dqs.db

import java.util.Properties

class PostgresWriter(host: String, port: Int, db: String, user: String, pass: String) {
  val jdbcUrl: String = s"jdbc:postgresql://$host:$port/$db"

  def connectionProperties(): Properties = {
    val props = new Properties()
    props.setProperty("user", user)
    props.setProperty("password", pass)
    props.setProperty("driver", "org.postgresql.Driver")
    props
  }
}
