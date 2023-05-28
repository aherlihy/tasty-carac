package tastycarac

import tastyquery.Contexts.Context
import tastyquery.Symbols.ClassSymbol
import tastycarac.Facts.Fact
import tastyquery.Trees.ValOrDefDef
import tastyquery.Symbols.TermSymbol
import tastyquery.Symbols.TermOrTypeSymbol
import tastycarac.Facts.*
import tastycarac.Symbols.Table

/* When we have a ClassSymbol class, we want to be able to know all the members
- generate a MemberDef(class, member) if there is a definition (overriden or not)
- generate a NotMemberDef(class, member) if it is inherited

Alternatively, we could also directly generate LookUp facts
- However, if negation works, it is much easier to only generate MemberDef
- Probably best to implement the closest thing to negative facts */

class ClassStructure(table: Table)(using Context) {
  // all (non-private) members of a class
  def inherited(cs: ClassSymbol): List[TermOrTypeSymbol] =
    cs.parentClasses.flatMap(p =>
      // we filter out override definitions, they do not introduce new members
      p.declarations.filter(_.allOverriddenSymbols.size == 0) ++
        inherited(p)
    )

  // all top-level symbols that s overrides
  def findRootSymbols(s: TermOrTypeSymbol): Seq[TermOrTypeSymbol] =
    s.allOverriddenSymbols.toSeq match {
      case Seq() => Seq(s) // s is the top-level symbol
      case overriden =>
        overriden.flatMap(s =>
          findRootSymbols(s.asInstanceOf[TermOrTypeSymbol])
        )
    }

  // generates all Extends/Defines/NotDefines for cs
  def definitionFacts(cs: ClassSymbol): Seq[Fact] =
    val defined = cs.declarations.flatMap(s =>
      findRootSymbols(s).map(r =>
        Defines(
          table.getSymbolId(cs).toString,
          table.getSymbolId(r),
          table.getSymbolId(s)
        )
      )
    )

    val overriden = cs.declarations
      .filter(_.allOverriddenSymbols.size > 0)
      .flatMap(findRootSymbols)
      .toSet

    val directlyInherited = inherited(cs).toSet
      .diff(overriden)
      .map(s => NotDefines(table.getSymbolId(cs).toString, table.getSymbolId(s)))

    cs.parentClasses.map(c =>
      Extends(table.getSymbolId(cs).toString, table.getSymbolId(c).toString)
    ) ++ defined ++ directlyInherited
}
