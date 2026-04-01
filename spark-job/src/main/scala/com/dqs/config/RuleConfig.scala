package com.dqs.config

import upickle.default._

case class CheckConfig(checkType: String, min: Option[Int] = None)
object CheckConfig {
  // uPickle 3.x encodes Option[T] as arrays by default; use a plain number/null codec instead
  implicit val optIntRw: ReadWriter[Option[Int]] = readwriter[ujson.Value].bimap[Option[Int]](
    { case Some(n) => ujson.Num(n.toDouble); case None => ujson.Null },
    { case ujson.Num(n) => Some(n.toInt); case _ => None }
  )
  implicit val rw: ReadWriter[CheckConfig] = macroRW
}

case class RuleConfig(dataset: String, checks: Seq[CheckConfig])
object RuleConfig {
  implicit val rw: ReadWriter[RuleConfig] = macroRW

  def fromJson(json: String): RuleConfig = read[RuleConfig](json)
}
