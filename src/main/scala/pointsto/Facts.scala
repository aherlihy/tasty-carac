package pointsto

import tastyquery.Symbols.TermSymbol

import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import scala.util.Using
import java.io.FileWriter
import java.nio.file.Path
import java.io.BufferedWriter

type Variable = String // variable
type Heap = String // allocation site (== memory location, kind of)
type Method = String
type Signature = String
type Field = String
type Instruction =
  String // TODO instruction does not make much sense in Scala???
type Type = String
type Index = String

object Facts {
  abstract class Fact extends Product

  // val v = new ... (heap is the allocation site, inMeth is the method)
  case class Alloc(varr: String, heap: String, inMeth: String) extends Fact

  // val to = from (from is a variable)
  case class Move(to: String, from: String) extends Fact

  // to := base.fld
  case class Load(to: String, base: String, fld: Field) extends Fact

  // base.fld = from
  case class Store(base: Variable, fld: Field, from: Variable) extends Fact

  // base.sig(...) at #invo inside inMeth
  case class VCall(
      base: Variable,
      sig: Signature,
      invo: Instruction,
      inMeth: Method
  ) extends Fact

  // def meth(..., arg, ...) where arg is the n-th argument
  case class FormalArg(meth: Method, n: Index, arg: Variable) extends Fact

  // meth(..., arg, ...) #invo where arg is the n-th argument
  case class ActualArg(invo: Instruction, n: Index, arg: Variable) extends Fact

  // meth returns variable arg at the end of its body
  case class FormalReturn(meth: Method, arg: Variable) extends Fact

  // val varr = somemethod(...) at #invo (there must be a matching vcall)
  case class ActualReturn(invo: Instruction, varr: Variable) extends Fact

  // ???
  case class ThisVar(meth: Method, thiss: Variable) extends Fact

  // allocation site heap has type typee
  case class HeapType(heap: Heap, typee: Type) extends Fact

  // link between a method signature and an actual method definition
  case class LookUp(typee: Type, sig: Signature, meth: Method) extends Fact

  // TODO VarType
  // TODO InMethod
  // TODO SubType

  // INTERMEDIATE RELATIONS

  // final result
  case class VarPointsTo(varr: Variable, heap: Heap) extends Fact

  // instruction #invo calls meth
  case class CallGraph(invo: Instruction, meth: Method) extends Fact

  // baseH.fld points to heap
  case class FieldPointsTo(baseH: Heap, fld: Field, heap: Heap) extends Fact

  // to gets assigned from (variable from another method via args)
  case class InterProcAssign(to: Variable, from: Variable) extends Fact

  // meth is reachable (we should always have Reachable(@main))
  case class Reachable(meth: Method) extends Fact

  def save(facts: Seq[Fact], output: Path): Unit = {
    val grouped = facts.groupBy(_.productPrefix)

    for ((relation, facts) <- grouped) {
      for (f <- facts) println(f)

      val path = output.resolve(f"${relation}.csv")
      Using(Files.newBufferedWriter(path)) { writer =>
        writer.write(facts.head.productIterator.map(_ => "String").mkString("\t"))
        writer.write("\n")

        for (f <- facts) {
          writer.write(f.productIterator.mkString("\t"))
          writer.write("\n")
        }
      }
    }
  }
}
