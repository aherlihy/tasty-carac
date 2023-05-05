package tastycarac

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
import scala.util.Failure
import scala.util.Success
import tastycarac.PointsTo

abstract class Test
abstract class T2 extends Test
case class T1() extends T2

object Main {
  case class Config(
      classPath: Option[Path] = None,
      output: Option[Path] = Some(Path.of("./output/")),
      mainMethod: String = "Main.main",
      help: Boolean = false
  )

  val usage = "Usage: tastycarac [-h] [-m main] [-o output] classpath"

  def main(args: Array[String]): Unit = {
    val argsList = args.toList
    val config = parseArgs(argsList)

    if config.help then println(usage)
    else inspect(config.classPath.get, config.output.get, config.mainMethod)
  }

  def parseArgs(argsList: List[String], acc: Config = Config()): Config =
    argsList match {
      case ("-o" | "--output") :: value :: tail =>
        parseArgs(tail, acc.copy(output = Some(Path.of(value))))
      case ("-m" | "--main") :: value :: tail =>
        parseArgs(tail, acc.copy(mainMethod = value))
      case ("-h" | "--help") :: tail => Config(help = true)
      case classPath :: tail =>
        parseArgs(tail, acc.copy(classPath = Some(Path.of(classPath))))
      case Nil => acc
    }

  def inspect(input: Path, output: Path, mainMethod: String) = {
    // val stdLibPaths = FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("modules", "java.base")
    val classpath = ClasspathLoaders.read(List(input))
    given Context = Contexts.init(classpath)
    val myLibSyms = ctx.findSymbolsByClasspathEntry(classpath.entries.head)
    val trees = myLibSyms.collect { case cs: ClassSymbol => cs }
    val facts = PointsTo(trees).generateFacts(mainMethod)

    Facts.exportFacts(Facts.Reachable(mainMethod) +: facts, output) match {
      case Failure(e) =>
        println(f"Something went wrong while saving the facts: ${e}")
      case Success(v) =>
        println(
          f"Facts saved successfully in ${output.toAbsolutePath().toString()}"
        )
    }
  }
}
