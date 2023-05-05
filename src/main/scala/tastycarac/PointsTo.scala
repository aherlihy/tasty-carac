package tastycarac

import tastyquery.Symbols.ClassSymbol
import tastyquery.Contexts.Context
import tastyquery.Trees.*
import tastyquery.Symbols.*
import scala.annotation.meta.getter
import tastyquery.Names.SignedName
import tastyquery.Signatures.Signature
import tastyquery.Types.TypeRef
import Facts.*
import tastyquery.Types.ErasedTypeRef
import tastyquery.Types.PackageRef
import tastyquery.Contexts.ctx
import tastyquery.Names.Name
import tastyquery.Names.SimpleName

class PointsTo(trees: Iterable[ClassSymbol])(using Context) {
  var instructionId = 0
  var tempVarId = 0
  var allocationId = 0

  private def getInstruction() = {
    val id = instructionId
    instructionId += 1
    f"instr#$id"
  }

  private def getTempVar() = {
    val id = tempVarId
    tempVarId += 1
    f"temp#$id"
  }

  private def getAllocation() = {
    val id = allocationId
    allocationId += 1
    id
  }

  def generateFacts(mainPath: String): Seq[Fact] =
    val path = mainPath.split('.').toList.map(p => SimpleName(p)).toSeq
    Reachable(mainPath) +: trees.map(generateFacts).reduce(_ ++ _)

  def generateFacts(cls: ClassSymbol) = {
    Traversal.walkTreeWithMethod[Seq[Fact]](cls.tree.get)((tree, context) => tree match {
      // val a = Constructor(...)
      case v@ValDef(name, tpt, Some(Apply(Select(New(TypeIdent(qualifier)), name1), args)), symbol) =>
        val allocationSite = f"new[$qualifier]#${getAllocation()}"
        Seq(
          Alloc(localName(symbol, context), allocationSite, context.last.fullName.toString),
          HeapType(allocationSite, qualifier.toString)
        )

      // val a = ...
      case ValDef(name, tpt, Some(rhs), symbol) =>
        breakExpr(localName(symbol, context), rhs, context)

      // ... := ...
      case Assign(lhs, rhs) =>
        lhs match {
          // this case is equivalent to the ValDef case
          case v: Ident => breakExpr(localName(v.symbol, context), rhs, context)

          // base.fld := ... (base can be any expression!)
          case Select(base, fld) =>
            val (rName, rIntermediate) = exprAsRef(rhs, context)
            val (baseName, baseIntermediate) = exprAsRef(base, context)
            Store(baseName, fld.toString, rName) +: rIntermediate ++: baseIntermediate

          // TODO can this even happen?
          case _ => ???
        }
      
      // method definition
      case DefDef(name, params, tpt, rhs, symbol) =>
        val newCon = context :+ symbol
        val List(Left(args)) = params // TODO assumption: one single parameters list, no type params
        ThisVar(symbol.fullName.toString, f"${symbol.fullName}.this") +:
          args.zipWithIndex.map((vd, i) =>
            FormalArg(symbol.fullName.toString, f"arg$i", localName(vd.symbol, newCon)) // TODO is the context already correct?
          ) ++: rhs.map(r => {
            val (retName, retIntermediate) = exprAsRef(r, newCon) // TODO assumption: there is a return value
            FormalReturn(symbol.fullName.toString, retName) +:
            retIntermediate
          }).getOrElse(Seq.empty)
      

      case ClassDef(name, rhs, symbol) => rhs.body.flatMap {
        case d@DefDef(methName, params, tpt, rhs, methSymbol) =>
          val List(Left(args)) = params // TODO assumption: one single parameters list, no type params
          Seq(LookUp(name.toString, methName.toString + args.map(d => typeName(d.tpt)).mkString("(", ",", ")") + ":" + typeName(tpt), methSymbol.fullName.toString))
        case _ => Seq.empty
      }
      
      case _ => Seq.empty

    })(_ ++ _, Seq.empty)
  }
  
  // current assumption: all (interesting?) method calls are of the form base.sig(...)
  private def breakExpr(to: Variable, e: TermTree, context: Seq[TermSymbol]): Seq[Fact] = e match {
    // TODO these 2 cases are slightly awkward, should be moved elsewhere
    case v: Ident => Seq(Move(to, localName(v.symbol, context)))
    case This(tpe) => Seq(Move(to, f"${context.last.fullName}.this"))

    case Select(base, fld) =>
      val (baseName, baseIntermediate) = exprAsRef(base, context)
      Load(to, baseName, fld.toString) +: baseIntermediate

    case Apply(Select(base, methName), args) =>
      val (baseName, baseIntermediate) = exprAsRef(base, context)
      val instruction = getInstruction()
      val methSigName = methName.asInstanceOf[SignedName]
      VCall(baseName, methSigName.target.toString + methSigName.sig.toString, instruction, context.last.fullName.toString) +:
        ActualReturn(instruction, to) +:
        args.zipWithIndex.flatMap { (t, i) =>
          val (name, argIntermediate) = exprAsRef(t, context)
          ActualArg(instruction, f"arg$i", name) +: argIntermediate
        } ++: baseIntermediate
    
    // { stats; expr }
    // TODO what about scope of blocks? we cannot simply use methods
    case Block(stats, expr) =>
      breakExpr(to, expr, context)

    case _ => Seq.empty
  }

  // we need to use this when a fact require a name but we might need intermediate facts
  private def exprAsRef(e: TermTree, context: Seq[TermSymbol]): (Variable, Seq[Fact]) = e match {
    case v: Ident => (localName(v.symbol, context), Seq.empty)
    case This(tpe) => (f"${context.last.fullName}.this", Seq.empty)
    case other =>
      val temp = getTempVar()
      (temp, breakExpr(temp, other, context)) // this call does not require the Ident case
  }

  private def localName(s: Symbol, context: Seq[TermSymbol]) =
    f"${context.last.fullName}.${s.name}"

  private def typeName(t: TypeTree): String = t match {
    case TypeIdent(name) => name.toString
    case TypeWrapper(tpe) =>
      val ref = tpe.asInstanceOf[TypeRef]
      val prefix = ref.prefix.asInstanceOf[PackageRef]
      f"${prefix.fullyQualifiedName}.${ref.name}"
  }
}
