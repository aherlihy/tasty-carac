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
import tastyquery.Types.TypeLambda
import tastyquery.Types.AppliedType
import tastyquery.Flags

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
    
    case cs@ClassDef(name, template, symbol) =>
      val initSymbol = table.getSymbolId(template.constr.symbol)
      val initThis = ThisSymbolId(initSymbol)
      val initContext = (context._1 :+ template.constr.symbol, Some(initThis))

      def forInstanceMethod(d: DefDef) =
        val thisId = ThisSymbolId(table.getSymbolId(d.symbol))
        ThisVar(table.getSymbolId(d.symbol), thisId) +:
        breakDefDef(d)(using (context._1 :+ d.symbol, Some(thisId)))

      classStructure.definitionFacts(symbol) ++:
      forInstanceMethod(template.constr) ++:
      template.parents.flatMap {
        case call:Apply =>
          val (fun, argLists) = unfoldCall(call)
          val instruction = getInstruction()
          val argsFacts = argLists.zipWithIndex.flatMap((args, i) =>
            args.zipWithIndex.flatMap((arg, j) =>
              val (name, argIntermediate) = exprAsRef(arg)
              ActualArg(instruction, f"list$i", f"arg$j", name) +: argIntermediate  
            )
          )
          VCall(initThis, table.getSymbolId(fun.asInstanceOf[Select].symbol), instruction, initSymbol) +: argsFacts
          
        case t: TypeTree =>
          typeToClassDef(t.toType).map(classDef =>
            VCall(initThis, table.getSymbolId(classDef.rhs.constr.symbol), getInstruction(), initSymbol)  
          )
          
        case _ => ???
      } ++:
      template.body.flatMap {
        case d:DefDef =>
          forInstanceMethod(d)

        // field declaration
        case ValDef(name, tpt, rhs, symbol) =>
          rhs match {
            case None =>
              template.constr.paramLists.collect { case Left(a) => a }.flatten.find(a => a.name.toString == name.toString)
                .map(from => Seq(
                  Move(table.getSymbolId(symbol), table.getSymbolId(from.symbol)),
                  FieldValDef(table.getSymbolId(symbol), table.getSymbolId(symbol))
                )).getOrElse(Seq.empty)
            case Some(e) =>
              val (rName, rIntermediate) = exprAsRef(e)(using initContext)
              Move(table.getSymbolId(symbol), rName) +:
              FieldValDef(table.getSymbolId(symbol), table.getSymbolId(symbol)) +: rIntermediate
          }

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
    d.paramLists.zipWithIndex.flatMap {
      case (Left(args), i) => args.zipWithIndex.map((vd, j) =>
        FormalArg(table.getSymbolId(d.symbol), f"list$i", f"arg$j", table.getSymbolId(vd.symbol))
      )
      case _ => Nil
    } ++: d.rhs.map(r => {
      val (retName, retIntermediate) = exprAsRef(r) // TODO assumption: there is a return value
      FormalReturn(table.getSymbolId(d.symbol), retName) +:
      retIntermediate
    }).getOrElse(Seq.empty)

  // current assumption: all (interesting?) method calls are of the form base.sig(...)
  private def breakExpr(e: TermTree, to: Option[Variable])(using context: ContextInfo): Seq[Fact] = e match {
    case v: Ident =>
      if v.symbol.flags.is(Flags.Method) then handleCall(v, to)
      else to.map(Move(_, table.getSymbolId(v.symbol))).toSeq
    
    case This(tpe) => to.map(Move(_, context._2.get)).toSeq

    case sel@Select(base, fld) =>
      if sel.symbol.flags.is(Flags.Method) then
        handleCall(sel, to)
      else
        val (baseName, baseIntermediate) = exprAsRef(base)
        baseIntermediate ++ to.map(t => Load(t, baseName, table.getSymbolId(sel.symbol)))
    
    // TODO is it the same for TypeApply? What exactly happens in this case?
    case call@Apply(fun, args) => handleCall(call, to)

    // ... := ...
    case Assign(lhs, rhs) =>
      lhs match {
        // this case is equivalent to the ValDef case
        case v: Ident => breakExpr(rhs, Some(table.getSymbolId(v.symbol)))

        // base.fld := ... (base can be any expression!)
        case sel@Select(base, fld) =>
          val (rName, rIntermediate) = exprAsRef(rhs)
          val (baseName, baseIntermediate) = exprAsRef(base)
          Store(baseName, table.getSymbolId(sel.symbol), rName) +: rIntermediate ++: baseIntermediate

        // TODO can this even happen?
        case _ => ???
      }
    
    // allocation site
    case New(tpt) => throw Error("Allocation should always be followed directly by a <init> calls")
    
    // { stats; expr }
    // TODO what about scope of blocks? we cannot simply use methods
    case Block(stats, expr) =>
      stats.flatMap(breakTree) ++ breakExpr(expr, to)
    case If(cond, thenPart, elsePart) =>
      breakExpr(cond, None) ++ breakExpr(thenPart, to) ++ breakExpr(elsePart, to)
    case InlineIf(cond, thenPart, elsePart) =>
      breakExpr(cond, None) ++ breakExpr(thenPart, to) ++ breakExpr(elsePart, to)
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
    case TypeApply(fun, args) => println(fun); breakExpr(fun, to)
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
      val temp = tempVar
      (temp, breakExpr(other, Some(temp))) // this call does not require the Ident case
  }

  private def tempVar(using context: ContextInfo): SymbolId =
    table.getSymbolIdFromPath(fullPath(context._1.last) :+ SimpleName("temp"))

  private def localName(s: Symbol, context: Seq[Symbol]) =
    f"${context.last.fullName}.${s.name}"

  private def typeToClassSymbol(t: Type): ClassSymbol = t match {
    case ref: TypeRef => ref.optSymbol.get match {
      case c: ClassSymbol => c
      case t: TypeMemberSymbol => typeToClassSymbol(t.aliasedType)
      case _ => ???
    }
    case lambda: TypeLambda => lambda.resultType match {
      case a: AppliedType => typeToClassSymbol(a.tycon)
      case _ => ???
    }
    case _ => ???
  }

  private def typeToClassDef(t: Type): Option[ClassDef] = typeToClassSymbol(t).tree

  private def handleCall(call: TermTree, to: Option[Variable])(using context: ContextInfo): Seq[Fact] =
    val (fun, argLists) = unfoldCall(call)
    val instruction = getInstruction()

    val callFacts = fun match {
      case sel@Select(Super(_, _), methName) =>
        SuperCall(table.getSymbolId(sel.symbol), instruction, table.getSymbolId(context._1.last)) +: Nil

      case sel@Select(base@New(tpt), init) =>
        val name = to.getOrElse(tempVar)
        val allocationSite = f"new[${table.getSymbolId(typeToClassSymbol(tpt.toType))}]#${getAllocation()}"
        HeapType(allocationSite, table.getSymbolId(typeToClassSymbol(tpt.toType))) +:
        Alloc(name, allocationSite, table.getSymbolId(context._1.last)) +:
        VCall(name, table.getSymbolId(sel.symbol), instruction, table.getSymbolId(context._1.last)) +: Nil

      case sel@Select(base, methName) =>
        val (baseName, baseIntermediate) = exprAsRef(base)
        VCall(baseName, table.getSymbolId(sel.symbol), instruction, table.getSymbolId(context._1.last)) +: baseIntermediate

      case id: Ident =>
        StaticCall(table.getSymbolId(id.symbol), instruction, table.getSymbolId(context._1.last)) +: Nil

      // are there other cases?
      case _ => ???
    }

    val assign = fun match {
      case Select(New(_), _) => None
      case other => to.map(ActualReturn(instruction, _))
    }

    val argsFacts = argLists.zipWithIndex.flatMap((args, i) =>
      args.zipWithIndex.flatMap((arg, j) =>
        val (name, argIntermediate) = exprAsRef(arg)
        ActualArg(instruction, f"list$i", f"arg$j", name) +: argIntermediate  
      )
    )

    callFacts ++: assign ++: argsFacts

  private def unfoldCall(call: TermTree, acc: List[List[TermTree]] = Nil): (TermTree, List[List[TermTree]]) =
    call match {
      case Apply(fun, args) => unfoldCall(fun, args :: acc)
      case TypeApply(fun, args) => unfoldCall(fun, Nil :: acc)
      case term => (term, acc)
    }
}
