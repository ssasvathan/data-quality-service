package com.dqs.config

import upickle.default._

case class CheckConfig(checkType: String, min: Option[Int] = None)
object CheckConfig {
  implicit val rw: ReadWriter[CheckConfig] = macroRW
}

case class RuleConfig(dataset: String, checks: Seq[CheckConfig])
object RuleConfig {
  implicit val rw: ReadWriter[RuleConfig] = macroRW

  def fromJson(json: String): RuleConfig = read[RuleConfig](json)
}
