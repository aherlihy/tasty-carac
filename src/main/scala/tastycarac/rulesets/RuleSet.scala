package tastycarac.rulesets

import datalog.dsl.Program
import datalog.dsl.Relation

trait RuleSet {
  def defineRules(program: Program): Relation[String]
}
