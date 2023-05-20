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
import tastyquery.Contexts.ctx
import tastyquery.Symbols.ClassSymbol
import java.io.File


abstract class AbstractAnalysisSuite(file: String, mainMethod: String) extends munit.FunSuite  {
  val facts = new Fixture[Seq[Fact]]("facts") {
    var dir: Path = null
    var facts: Seq[Fact] = null

    def apply() = facts

    override def beforeAll(): Unit = {
      dir = Files.createTempDirectory("tmp")
      val inputPath = Path.of(getClass.getResource("/" + file).getPath)

      val module = Dependency(Module(Organization("org.scala-lang"), ModuleName("scala3-library_3"), Map.empty), "3.2.2")

      val lib = Fetch()
      .addDependencies(module)
      .run().map(_.toPath()).toSeq

      dotc.Main.process(Array(
        "-d", dir.toString,
        "-classpath", lib.mkString(File.pathSeparator),
        inputPath.toString, 
      ))      

      val classpath = ClasspathLoaders.read(List(dir))
      given Context = Contexts.init(classpath)
      val trees = ctx.findSymbolsByClasspathEntry(classpath.entries.head).collect { case cs: ClassSymbol => cs }
      facts = PointsTo(trees).generateFacts(mainMethod)
    }

    override def afterAll(): Unit = {
      def delete(path: Path): Unit = 
        if Files.isDirectory(path) then Files.list(path).forEach(delete(_))
        Files.delete(path)

      delete(dir)
    }
  }

  override def munitFixtures = List(facts)
}
