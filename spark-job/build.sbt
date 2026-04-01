name := "dqs-spark-job"
version := "1.0"
scalaVersion := "2.13.12"

val sparkVersion = "3.5.0"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided,test",
  "org.postgresql" % "postgresql" % "42.7.2",
  "com.lihaoyi" %% "upickle" % "3.2.0",
  "org.scalatest" %% "scalatest" % "3.2.18" % "test"
)

// Fork testing to prevent JVM Spark context overlap
Test / fork := true

// Use Java 21 for the forked test JVM — Spark 3.5.x officially supports Java 17/21.
// The system default Java 25 removed Subject.getSubject() (used by Hadoop UGI),
// which breaks SparkContext initialisation.
Test / javaHome := Some(file("/usr/lib/jvm/java-21-openjdk-amd64"))

// Java 17+ module opens required by Spark/Hadoop internals
Test / javaOptions ++= Seq(
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
  "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
  "--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED"
)
