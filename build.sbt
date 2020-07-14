import Dependencies._
import xerial.sbt.Sonatype.GitHubHosting

lazy val scala212 = "2.12.12"
lazy val scala211 = "2.11.12"
lazy val supportedScalaVersions = List(scala212, scala211)
val versionForDocs = "0.1.10" // TODO: Make this update automatically on release, currently it's manual

ThisBuild / scalaVersion := scala211
ThisBuild / organization := "com.github.timgent"
ThisBuild / organizationName := "timgent"

lazy val root = (project in file("."))
  .settings(
    name := "spark-data-quality",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= List(
      scalaTest,
      sparkTestingBase,
      scalaMock,
      sparkCore,
      sparkSql,
      elastic4s,
      elastic4sTestKit,
      elastic4sCirceJson,
      enumeratum,
      cats,
      spire,
      scalacheck,
      scalacheckToolboxDatetime,
      scalacheckToolboxMagic,
      scalacheckToolboxCombinators
    ),
    fork in Test := true,
    parallelExecution in Test := false,
    javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled"),
    assemblyShadeRules in assembly ++= Seq(
      // Required due to conflicting shapeless versions between circe and spark libraries
      ShadeRule
        .rename("com.chuusai.shapeless.**" -> "shapeless_new.@1")
        .inLibrary("com.chuusai" %% "shapeless" % "2.3.2")
        .inProject
    )
  )

lazy val docs = project // new documentation project
  .in(file("spark-data-quality-docs")) // important: it must not be docs/
  .dependsOn(root)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
  .settings(
    moduleName := "spark-data-quality-docs",
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(root),
    target in (ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in Compile).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value,
    mdocIn := new File("docs-source"),
    mdocVariables := Map("VERSION" -> versionForDocs)
  )

scalacOptions += "-Ypartial-unification"
developers := List(Developer("timgent", "Tim Gent", "tim.gent@gmail.com", url("https://github.com/timgent")))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/timgent/spark-data-quality.git"),
    "scm:git@github.com:timgent/spark-data-quality.git"
  )
)
homepage := Some(url("https://github.com/timgent/spark-data-quality"))
licenses := Seq("Apache License 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
publishTo := sonatypePublishToBundle.value
sonatypeProfileName := "com.github.timgent"
publishMavenStyle := true
sonatypeProjectHosting := Some(GitHubHosting("timgent", "spark-data-quality", "tim.gent@gmail.com"))

import ReleaseTransformations._

releaseCrossBuild := true // true if you cross-build the project for multiple Scala versions
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  // For non cross-build projects, use releaseStepCommand("publishSigned")
  // For cross-build projects, use releaseStepCommand("+publishSigned")
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
