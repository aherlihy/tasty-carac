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

  def generateFacts(cls: ClassSymbol): Seq[Fact] =
    cls.tree.map(breakTree(_)(using Seq.empty)).getOrElse(Seq.empty)

  private def breakTree(s: Tree)(using context: Seq[Symbol]): Seq[Fact] = s match {
    case v@ValDef(name, tpt, Some(Apply(Select(New(TypeIdent(qualifier)), name1), args)), symbol) =>
        val allocationSite = f"new[$qualifier]#${getAllocation()}"
        Seq(
          Alloc(localName(symbol, context), allocationSite, context.last.fullName.toString),
          HeapType(allocationSite, qualifier.toString)
        )

    // val a = ...
    case ValDef(name, tpt, Some(rhs), symbol) =>
      breakExpr(rhs, Some(localName(symbol, context)))

    // method definition
    case DefDef(name, params, tpt, rhs, symbol) =>
      val newCon = context :+ symbol
      val List(Left(args)) = params // TODO assumption: one single parameters list, no type params
      ThisVar(symbol.fullName.toString, f"${symbol.fullName}.this") +:
        args.zipWithIndex.map((vd, i) =>
          FormalArg(symbol.fullName.toString, f"arg$i", localName(vd.symbol, newCon)) // TODO is the context already correct?
        ) ++: rhs.map(r => {
          val (retName, retIntermediate) = exprAsRef(r)(using newCon) // TODO assumption: there is a return value
          FormalReturn(symbol.fullName.toString, retName) +:
          retIntermediate
        }).getOrElse(Seq.empty)
    
    case ClassDef(name, rhs, symbol) => rhs.body.flatMap {
      case d@DefDef(methName, params, tpt, rhs, methSymbol) =>
        val List(Left(args)) = params // TODO assumption: one single parameters list, no type params
        LookUp(name.toString, methName.toString + args.map(d => typeName(d.tpt)).mkString("(", ",", ")") + ":" + typeName(tpt), methSymbol.fullName.toString) +:
        breakTree(d)
      case other => breakTree(other)
    }
    
    // expression in statement position
    case e: TermTree =>
      breakExpr(e, None)

    case other =>
      Traversal.subtrees(other).flatMap(breakTree)
  }
  
  private def breakCase(c: CaseDef, to: Option[Variable])(using context: Seq[Symbol]): Seq[Fact] =
    breakPattern(c.pattern) ++ c.guard.map(breakExpr(_, None)).getOrElse(Seq.empty) ++ breakExpr(c.body, to)

  private def breakPattern(p: PatternTree)(using context: Seq[Symbol]): Seq[Fact] = p match {
    case Alternative(trees) => trees.flatMap(breakPattern)
    case Unapply(fun, implicits, patterns) => ???
    case Bind(name, body, symbol) => ???
    case ExprPattern(expr) => ???
    case TypeTest(body, tpt) => ???
    case WildcardPattern(tpe) => ???
  }

  // current assumption: all (interesting?) method calls are of the form base.sig(...)
  private def breakExpr(e: TermTree, to: Option[Variable])(using context: Seq[Symbol]): Seq[Fact] = e match {
    // TODO these 2 cases are slightly awkward, should be moved elsewhere
    case v: Ident => to.map(Move(_, localName(v.symbol, context))).toSeq
    case This(tpe) => to.map(Move(_, f"${context.last.fullName}.this")).toSeq

    case Select(base, fld) =>
      val (baseName, baseIntermediate) = exprAsRef(base)
      baseIntermediate ++ to.map(Load(_, baseName, fld.toString))

    case Apply(Select(base, methName), args) =>
      val (baseName, baseIntermediate) = exprAsRef(base)
      val instruction = getInstruction()
      val methSigName = methName.asInstanceOf[SignedName]
      VCall(baseName, methSigName.target.toString + methSigName.sig.toString, instruction, context.last.fullName.toString) +:
        to.map(ActualReturn(instruction, _)) ++:
        args.zipWithIndex.flatMap { (t, i) =>
          val (name, argIntermediate) = exprAsRef(t)
          ActualArg(instruction, f"arg$i", name) +: argIntermediate
        } ++: baseIntermediate

    // ... := ...
    case Assign(lhs, rhs) =>
      lhs match {
        // this case is equivalent to the ValDef case
        case v: Ident => breakExpr(rhs, Some(localName(v.symbol, context)))

        // base.fld := ... (base can be any expression!)
        case Select(base, fld) =>
          val (rName, rIntermediate) = exprAsRef(rhs)
          val (baseName, baseIntermediate) = exprAsRef(base)
          Store(baseName, fld.toString, rName) +: rIntermediate ++: baseIntermediate

        // TODO can this even happen?
        case _ => ???
      }
    
    // { stats; expr }
    // TODO what about scope of blocks? we cannot simply use methods
    case Block(stats, expr) =>
      stats.flatMap(breakTree) ++ breakExpr(expr, to)
    case If(cond, thenPart, elsePart) =>
      breakExpr(cond, None) ++ breakExpr(thenPart, to) ++ breakExpr(thenPart, to)
    case InlineIf(cond, thenPart, elsePart) =>
      breakExpr(cond, None) ++ breakExpr(thenPart, to) ++ breakExpr(thenPart, to)
    case Match(selector, cases) =>
      breakExpr(selector, None) ++ cases.flatMap(breakCase(_, to))
    case InlineMatch(selector, cases) => ???
      // breakExpr(selector, None) ++ cases.flatMap(breakCase(_, to))
    case Inlined(expr, caller, bindings) => ???
    case Lambda(meth, tpt) => ???
    case NamedArg(name, arg) => ???
    case New(tpt) => Seq.empty // TODO
    case Return(expr, from) => ???
    case SeqLiteral(elems, elemtpt) => ???
    case Super(qual, mix) => ???
    case Throw(expr) => ???
    case Try(expr, cases, finalizer) => ???
    case TypeApply(fun, args) => ???
    case Typed(expr, tpt) => breakExpr(expr, to)
    case While(cond, body) => breakTree(cond) ++ breakTree(body)
    case Literal(_) => Seq.empty
    case _ => ???
  }
  

  // we need to use this when a fact require a name but we might need intermediate facts
  private def exprAsRef(e: TermTree)(using context: Seq[Symbol]): (Variable, Seq[Fact]) = e match {
    case v: Ident => (localName(v.symbol, context), Seq.empty)
    case This(tpe) => (f"${context.last.fullName}.this", Seq.empty)
    case other =>
      val temp = getTempVar()
      (temp, breakExpr(other, Some(temp))) // this call does not require the Ident case
  }

  private def localName(s: Symbol, context: Seq[Symbol]) =
    f"${context.last.fullName}.${s.name}"

  private def typeName(t: TypeTree): String = t match {
    case TypeIdent(name) => name.toString
    case TypeWrapper(tpe) =>
      val ref = tpe.asInstanceOf[TypeRef]
      val prefix = ref.prefix.asInstanceOf[PackageRef]
      f"${prefix.fullyQualifiedName}.${ref.name}"
  }
}
