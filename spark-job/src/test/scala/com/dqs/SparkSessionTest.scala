package com.dqs

import org.scalatest.funsuite.AnyFunSuite
import org.apache.spark.sql.SparkSession

class SparkSessionTest extends AnyFunSuite {
  test("SparkSession can be initialized locally") {
    val spark = SparkSession.builder()
      .master("local[*]")
      .appName("DqsTest")
      .getOrCreate()

    assert(spark.version.startsWith("3.5"))
    spark.stop()
  }
}
