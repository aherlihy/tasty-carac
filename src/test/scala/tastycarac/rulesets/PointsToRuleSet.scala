package tastycarac.rulesets

import datalog.dsl.Program
import tastycarac.rulesets.RuleSet

object PointsToRuleSet extends RuleSet {
  def defineRules(program: Program) = {
    val ActualArg = program.relation[String]("ActualArg")
    val ActualReturn = program.relation[String]("ActualReturn")
    val Alloc = program.relation[String]("Alloc")
    val Move = program.relation[String]("Move")
    val FormalArg = program.relation[String]("FormalArg")
    val FormalReturn = program.relation[String]("FormalReturn")
    val HeapType = program.relation[String]("HeapType")
    val LookUp = program.relation[String]("LookUp")
    val ThisVar = program.relation[String]("ThisVar")
    val VCall = program.relation[String]("VCall")
    val Store = program.relation[String]("Store")
    val Load = program.relation[String]("Load")

    val StaticCall = program.relation[String]("StaticCall")
    val StaticLookUp = program.relation[String]("StaticLookUp")

    val VarPointsTo = program.relation[String]("VarPointsTo")
    val CallGraph = program.relation[String]()
    val FldPointsTo = program.relation[String]()
    val InterProcAssign = program.relation[String]()
    val Reachable = program.relation[String]("Reachable")

    val varr, heap, meth, to, from, base, baseH, fld, ref = program.variable()
    val toMeth, thiss, invo, sig, inMeth, heapT, n = program.variable()

    VarPointsTo(varr, heap) :- (Reachable(meth), Alloc(varr, heap, meth))
    VarPointsTo(to, heap) :- (Move(to, from), VarPointsTo(from, heap))
    FldPointsTo(baseH, fld, heap) :- (Store(base, fld, from), VarPointsTo(from, heap), VarPointsTo(base, baseH))
    VarPointsTo(to, heap) :- (Load(to, base, fld), VarPointsTo(base, baseH), FldPointsTo(baseH, fld, heap))

    Reachable(toMeth) :-
      (VCall(base, sig, invo, inMeth), Reachable(inMeth),
        VarPointsTo(base, heap),
        HeapType(heap, heapT), LookUp(heapT, sig, toMeth),
        ThisVar(toMeth, thiss))

    VarPointsTo(thiss, heap) :-
      (VCall(base, sig, invo, inMeth), Reachable(inMeth),
        VarPointsTo(base, heap),
        HeapType(heap, heapT), LookUp(heapT, sig, toMeth),
        ThisVar(toMeth, thiss))

    CallGraph(invo, toMeth) :-
      (VCall(base, sig, invo, inMeth), Reachable(inMeth),
        VarPointsTo(base, heap),
        HeapType(heap, heapT), LookUp(heapT, sig, toMeth),
        ThisVar(toMeth, thiss))

    InterProcAssign(to, from) :- (CallGraph(invo, meth), FormalArg(meth, n, to), ActualArg(invo, n, from))

    InterProcAssign(to, from) :- (CallGraph(invo, meth), FormalReturn(meth, from), ActualReturn(invo, to))

    VarPointsTo(to, heap) :- (InterProcAssign(to, from), VarPointsTo(from, heap))

    Reachable(toMeth) :- (StaticCall(toMeth, invo, inMeth), Reachable(inMeth), StaticLookUp(toMeth))

    CallGraph(invo, toMeth) :- (StaticCall(toMeth, invo, inMeth), Reachable(inMeth), StaticLookUp(toMeth))

    VarPointsTo
  }
}
