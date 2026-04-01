package com.dqs.config

import org.scalatest.funsuite.AnyFunSuite

class RuleConfigTest extends AnyFunSuite {
  test("parse rule config json") {
    val json = """{"dataset": "UET0", "checks": [{"checkType": "rowCount", "min": [100]}]}"""
    val config = RuleConfig.fromJson(json)

    assert(config.dataset == "UET0")
    assert(config.checks.size == 1)
    assert(config.checks.head.checkType == "rowCount")
    assert(config.checks.head.min.contains(100))
  }
}
