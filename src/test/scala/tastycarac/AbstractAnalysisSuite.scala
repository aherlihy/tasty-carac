package tastycarac

import dotty.tools.dotc
import java.nio.file.Path
import java.nio.file.Files
import scala.io.Source
import tastycarac.Facts.Fact
import coursier.Fetch
import coursier.Dependency
import coursier.core.Organization
import coursier.core.ModuleName
import coursier.core.Module
import tastyquery.jdk.ClasspathLoaders
import tastyquery.Contexts
import tastyquery.Contexts.Context
import tastyquery.Symbols.ClassSymbol
import java.io.File
import datalog.execution.SemiNaiveExecutionEngine
import datalog.storage.DefaultStorageManager
import datalog.dsl.Program
import tastycarac.rulesets.RuleSet
import java.nio.file.FileSystems
import tastyquery.Symbols.TermOrTypeSymbol


abstract class AbstractAnalysisSuite(file: String, mainMethod: String, ruleset: RuleSet) extends munit.FunSuite  {
  var dir: Path = null
  var facts: Seq[Fact] = null
  var program: Program = null

  override def beforeAll(): Unit = {
    dir = Files.createTempDirectory("tmp")
    val inputPath = Path.of(getClass.getResource("/" + file).getPath)

    val module = Dependency(Module(Organization("org.scala-lang"), ModuleName("scala3-library_3"), Map.empty), "3.2.2")

    val scalaStdLib = Fetch()
    .addDependencies(module)
    .run().map(_.toPath()).toList

    dotc.Main.process(Array(
      "-d", dir.toString,
      "-classpath", scalaStdLib.mkString(File.pathSeparator),
      inputPath.toString, 
    ))      
    
    val javaStdLib = FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("modules", "java.base")
    val classpath = ClasspathLoaders.read(dir :: javaStdLib :: scalaStdLib)
    val ctx = Contexts.init(classpath)
    val trees = ctx.findSymbolsByClasspathEntry(classpath.entries.head).collect { case cs: ClassSymbol => cs }
    facts = PointsTo(trees)(using ctx).generateFacts(mainMethod)

    val engine = SemiNaiveExecutionEngine(DefaultStorageManager())
    program = Program(engine)
    val toSolve = ruleset.defineRules(program)

    for (f <- facts) {
      program.namedRelation(f.productPrefix).apply(f.productIterator.map(_.toString).toSeq:_*) :- ()
    }

    toSolve.solve()
  }

  override def afterAll(): Unit = {
    def delete(path: Path): Unit = 
      if Files.isDirectory(path) then Files.list(path).forEach(delete(_))
      Files.delete(path)

    delete(dir)
  }
}
