package pointsto

import tastyquery.Classpaths.Classpath
import java.nio.file.FileSystems
import tastyquery.jdk.ClasspathLoaders
import tastyquery.Contexts
import tastyquery.Contexts.Context
import tastyquery.Contexts.ctx
import java.nio.file.Path
import tastyquery.Names.*
import tastyquery.Symbols.ClassSymbol
import tastyquery.Symbols.ClassTypeParamSymbol
import tastyquery.Symbols.TermSymbol
import tastyquery.Trees.*
import scala.annotation.meta.param
import pointsto.Facts.Reachable

object Main {
  @main def main = {
    val path = Path.of("/home/benoit/src/semester-project/TastyLib/target/scala-3.2.1/classes")
    inspect("PointsToFun", path)
  }

  def inspect(classname: String, path: Path) = {
    // val stdLibPaths = FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("modules", "java.base")
    val paths = List(path)

    val classpath = ClasspathLoaders.read(paths)
    given Context = Contexts.init(classpath)
    val myLibSyms = ctx.findSymbolsByClasspathEntry(classpath.entries.head)
    
    val analysis = Andersen()
    val facts = myLibSyms.flatMap {
      case cs:ClassSymbol /*if cs.name.toString == classname*/ =>
        analysis.generateFacts(cs)
      case other =>
        Seq.empty // TODO do we have to do something with this
    }.toList

    Facts.save(
      Reachable("Main.main") +:
        facts, Path.of("/home/benoit/src/semester-project/TastyTest/output"))

  }
}