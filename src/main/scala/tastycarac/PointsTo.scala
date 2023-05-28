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
import tastycarac.Symbols.Table
import scala.collection.mutable
import tastycarac.Symbols.fullPath
import tastycarac.Symbols.*
import tastyquery.Symbols
import tastyquery.Types.Type
import tastyquery.Names.TermName
import tastyquery.Signatures.ParamSig

class PointsTo(trees: Iterable[ClassSymbol])(using Context) {
  var instructionId = 0
  var tempVarId = 0
  var allocationId = 0
  val table = Table()
  val classStructure = ClassStructure(table)

  type ContextInfo = (Seq[Symbol], Option[ThisSymbolId])

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
    // TODO HANDLE ENTRY POINT
    val path = mainPath.split('.').toList
    val classSymbol = ctx.findTopLevelModuleClass(path.init.mkString("."))
    val mainMethod = classSymbol.declarations.find(_.name.toString == path.last).get
    Reachable(table.getSymbolId(mainMethod)) +: trees.map(generateFacts).reduce(_ ++ _)

  def generateFacts(cls: ClassSymbol): Seq[Fact] =
    cls.tree.map(breakTree(_)(using (Seq.empty, None))).getOrElse(Seq.empty)

  private def breakTree(s: Tree)(using context: ContextInfo): Seq[Fact] = s match {
    // val a = ...
    case ValDef(name, tpt, Some(rhs), symbol) =>
      breakExpr(rhs, Some(table.getSymbolId(symbol)))

    // (static) method definition
    case d@DefDef(name, params, tpt, rhs, symbol) =>
      StaticLookUp(table.getSymbolId(symbol)) +:
      breakDefDef(d)(using (context._1 :+ symbol, context._2))
    
    case cs@ClassDef(name, rhs, symbol) =>
      val initSymbol = table.getSymbolId(rhs.constr.symbol)
      val initThis = ThisSymbolId(initSymbol)
      val initContext = (context._1 :+ rhs.constr.symbol, Some(initThis))

      def forInstanceMethod(d: DefDef) =
        val thisId = ThisSymbolId(table.getSymbolId(d.symbol))
        val overridenSymbols = classStructure.findRootSymbols(d.symbol)
        overridenSymbols.map(s => LookUp(table.getSymbolId(symbol).toString, table.getSymbolId(s), table.getSymbolId(d.symbol))) ++:
        ThisVar(table.getSymbolId(d.symbol), thisId) +:
        breakDefDef(d)(using (context._1 :+ d.symbol, Some(thisId)))

      // values of arguments are assigned to instance fields
      val List(Left(args)) = rhs.constr.paramLists // TODO assumption: one single parameters list, no type params
      args.map(a => Store(initThis, a.name.toString, table.getSymbolId(a.symbol))) ++:
      forInstanceMethod(rhs.constr) ++:
      rhs.body.flatMap {
        case d:DefDef =>
          forInstanceMethod(d)

        // field declaration with a concrete value
        case ValDef(name, tpt, Some(rhs), symbol) =>
          val (rName, rIntermediate) = exprAsRef(rhs)(using initContext)
          Store(initThis, name.toString, rName) +: rIntermediate
        
        // other statements are handled as if they were inside the constructor
        case other => breakTree(other)(using initContext)
      }
    
    // expression in statement position
    case e: TermTree =>
      breakExpr(e, None)

    case other =>
      Traversal.subtrees(other).flatMap(breakTree)
  }
  
  private def breakCase(c: CaseDef, to: Option[Variable])(using context: ContextInfo): Seq[Fact] =
    breakPattern(c.pattern) ++ c.guard.map(breakExpr(_, None)).getOrElse(Seq.empty) ++ breakExpr(c.body, to)

  private def breakPattern(p: PatternTree)(using context: ContextInfo): Seq[Fact] = p match {
    case Alternative(trees) => trees.flatMap(breakPattern)
    case Unapply(fun, implicits, patterns) => ???
    case Bind(name, body, symbol) => ???
    case ExprPattern(expr) => ???
    case TypeTest(body, tpt) => ???
    case WildcardPattern(tpe) => ???
  }

  // method arguments, body and return
  private def breakDefDef(d: DefDef)(using context: ContextInfo): Seq[Fact] =
    val List(Left(args)) = d.paramLists // TODO assumption: one single parameters list, no type params
    args.zipWithIndex.map((vd, i) =>
      FormalArg(table.getSymbolId(d.symbol), f"arg$i", table.getSymbolId(vd.symbol)) // TODO is the context already correct?
    ) ++: d.rhs.map(r => {
      val (retName, retIntermediate) = exprAsRef(r) // TODO assumption: there is a return value
      FormalReturn(table.getSymbolId(d.symbol), retName) +:
      retIntermediate
    }).getOrElse(Seq.empty)

  // current assumption: all (interesting?) method calls are of the form base.sig(...)
  private def breakExpr(e: TermTree, to: Option[Variable])(using context: ContextInfo): Seq[Fact] = e match {
    // TODO these 2 cases are slightly awkward, should be moved elsewhere
    case v: Ident => to.map(Move(_, table.getSymbolId(v.symbol))).toSeq
    case This(tpe) => to.map(Move(_, context._2.get)).toSeq

    case Select(base, fld) =>
      val (baseName, baseIntermediate) = exprAsRef(base)
      baseIntermediate ++ to.map(Load(_, baseName, fld.toString))

    // base.sig(...)
    case Apply(sel@Select(base, methName), args) =>
      val (baseName, baseIntermediate) = exprAsRef(base)
      val instruction = getInstruction()
      val overridenSymbols = classStructure.findRootSymbols(sel.symbol.asInstanceOf[TermSymbol])
      overridenSymbols.map(s => VCall(baseName, table.getSymbolId(s), instruction, table.getSymbolId(context._1.last))) ++:
        to.map(t => base match {
          // in the case of allocation 
          case New(tpt) => Move(t, baseName)
          case _ => ActualReturn(instruction, t)
        }) ++:
        args.zipWithIndex.flatMap { (t, i) =>
          val (name, argIntermediate) = exprAsRef(t)
          ActualArg(instruction, f"arg$i", name) +: argIntermediate
        } ++: baseIntermediate
    
    // static method call
    case Apply(fun: Ident, args) =>
      val instruction = getInstruction()
      StaticCall(table.getSymbolId(fun.symbol), instruction, table.getSymbolId(context._1.last)) +:
        to.map(ActualReturn(instruction, _)) ++:
        args.zipWithIndex.flatMap { (t, i) =>
          val (name, argIntermediate) = exprAsRef(t)
          ActualArg(instruction, f"arg$i", name) +: argIntermediate
        }
    
    // TODO can this case happen? If we have a lambda it would be l.apply(...)
    case Apply(fun, args) => ???

    // ... := ...
    case Assign(lhs, rhs) =>
      lhs match {
        // this case is equivalent to the ValDef case
        case v: Ident => breakExpr(rhs, Some(table.getSymbolId(v.symbol)))

        // base.fld := ... (base can be any expression!)
        case Select(base, fld) =>
          val (rName, rIntermediate) = exprAsRef(rhs)
          val (baseName, baseIntermediate) = exprAsRef(base)
          Store(baseName, fld.toString, rName) +: rIntermediate ++: baseIntermediate

        // TODO can this even happen?
        case _ => ???
      }
    
    // allocation site
    case New(tpt) =>
      val allocationSite = f"new[${typeName(tpt)}]#${getAllocation()}"
      HeapType(allocationSite, typeName(tpt)) +:
        to.map(Alloc(_, allocationSite, table.getSymbolId(context._1.last))).toSeq
    
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
  private def exprAsRef(e: TermTree)(using context: ContextInfo): (Variable, Seq[Fact]) = e match {
    case v: Ident => (table.getSymbolId(v.symbol), Seq.empty)
    case This(tpe) => (context._2.get, Seq.empty)
    case other =>
      val temp = table.getSymbolIdFromPath(fullPath(context._1.last) :+ SimpleName("temp"))
      (temp, breakExpr(other, Some(temp))) // this call does not require the Ident case
  }

  private def localName(s: Symbol, context: Seq[Symbol]) =
    f"${context.last.fullName}.${s.name}"

  private def typeName(t: TypeTree): String =
    typeName(t.toType)

  private def typeName(tpe: Type) =
    val ref = tpe.asInstanceOf[TypeRef]
    val prefix = ref.prefix.asInstanceOf[PackageRef]
    f"${prefix.fullyQualifiedName}.${ref.name}"

}
