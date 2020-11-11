/*
 * Copyright (c) 2014-2019 by The Minitest Project Developers.
 * Some rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import com.typesafe.sbt.GitVersioning

addCommandAlias("ci-all",  ";+clean ;+test:compile ;+test ; +package")
addCommandAlias("release", ";+clean ;+package ;+publishSigned")

val Scala211 = "2.11.12"
val Scala212 = "2.12.10"
val Scala213 = "2.13.1"

ThisBuild / scalaVersion := Scala212
ThisBuild / crossScalaVersions := Seq(Scala211, Scala212, Scala213)

def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)
lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc).value.map { dir =>
        scalaPartV.value match {
          case Some((major, minor)) =>
            new File(dir.getPath + s"_$major.$minor")
          case None =>
            throw new NoSuchElementException("Scala version")
        }
      }
    }
  }

lazy val scalaLinterOptions =
  Seq(
    // Enables linter options
    "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
    "-Xlint:nullary-unit", // warn when nullary methods return Unit
    "-Xlint:inaccessible", // warn about inaccessible types in method signatures
    "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
    "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
    "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
    "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
    "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
    "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
    "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
    "-Xlint:option-implicit", // Option.apply used implicit view
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit
    "-Xlint:package-object-classes", // Class or object defined in package object
  )

lazy val scalaTwoTwelveDeprecatedOptions =
  Seq(
    // Deprecated in 2.12, removed in 2.13
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit"
  )

lazy val sharedSettings = Seq(
  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  scalacOptions ++= Seq(
    "-unchecked", "-deprecation", "-feature", "-Xlint",
    "-Ywarn-dead-code",
    "-Xlog-free-terms"
  ),

  // Version specific options
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v >= 12 =>
      scalaLinterOptions
    case Some((2, 12)) =>
      scalaLinterOptions ++ scalaTwoTwelveDeprecatedOptions
    case Some((2, 11)) =>
      scalaLinterOptions ++ Seq("-target:jvm-1.6") ++ scalaTwoTwelveDeprecatedOptions
    case _ =>
      Seq("-target:jvm-1.6")
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11 | 12)) =>
      Seq(
        "-Xlint:unsound-match", // Pattern match may not be typesafe
        "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
        "-Ywarn-adapted-args"
      )
    case _ =>
      Nil
  }),

  resolvers ++= Seq(
    "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases",
    Resolver.sonatypeRepo("releases")
  ),

  testFrameworks := Seq(new TestFramework("minitest.runner.Framework"))
)

lazy val scalaJSSettings = Seq(
  scalaJSStage in Test := FastOptStage
)

lazy val nativeSettings = Seq(
  scalaVersion := Scala211,
  crossScalaVersions := Seq(Scala211),
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
)

lazy val minitestRoot = project.in(file("."))
  .aggregate(minitestJVM, minitestJS, lawsJVM, lawsJS)
  .settings(
    name := "minitest root",
    Compile / sources := Nil,
    skip in publish := true,
  )

lazy val minitest = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .settings(
    name := "minitest",
    sharedSettings,
    crossVersionSharedSources,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Compile,
      "org.portable-scala" %%% "portable-scala-reflect" % "1.0.0"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.scala-sbt" % "test-interface" % "1.0"
    ),
  )
  .jsSettings(
    scalaJSSettings,
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
  )

lazy val minitestJVM    = minitest.jvm
lazy val minitestJS     = minitest.js

lazy val laws = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("laws"))
  .dependsOn(minitest)
  .settings(
    name := "minitest-laws",
    sharedSettings,
    crossVersionSharedSources,
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.14.3"
    )
  )
  .jsSettings(
    scalaJSSettings
  )

lazy val lawsJVM    = laws.jvm
lazy val lawsJS     = laws.js
