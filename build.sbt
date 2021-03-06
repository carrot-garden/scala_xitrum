// To build Xitrum from source code:
// 1. From Xitrum source code directory, run SBT without any argument
// 2. From SBT prompt, run + publish-local (yes, with the plus sign)

organization := "tv.cntt"

name := "xitrum"

version := "1.9.3-SNAPSHOT"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked"
)

// Put config directory in classpath for easier development (sbt console etc.)
unmanagedBase in Runtime <<= baseDirectory { base => base / "config" }

// Hazelcast -------------------------------------------------------------------

// For distributed cache and Comet
// Infinispan is good but much heavier
libraryDependencies += "com.hazelcast" % "hazelcast" % "2.2"

// http://www.hazelcast.com/documentation.jsp#Clients
// Hazelcast can be configured in Xitrum as super client or native client
libraryDependencies += "com.hazelcast" % "hazelcast-client" % "2.2"

// Jerkson ---------------------------------------------------------------------

// https://github.com/codahale/jerkson
// lift-json does not generate correctly for:
//   List(Map("user" -> List("langtu"), "body" -> List("hello world")))
resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies += "com.codahale" % "jerkson_2.9.1" % "0.5.0"

// Scalate ---------------------------------------------------------------------

libraryDependencies += "org.fusesource.scalate" % "scalate-core" % "1.5.3"

// For Scalate to compile CoffeeScript to JavaScript
libraryDependencies += "org.mozilla" % "rhino" % "1.7R4"

// Other dependencies ----------------------------------------------------------

libraryDependencies += "io.netty" % "netty" % "3.5.3.Final"

libraryDependencies += "tv.cntt" %% "scaposer" % "1.1"

libraryDependencies += "tv.cntt" %% "sclasner" % "1.1"

libraryDependencies += "org.javassist" % "javassist" % "3.16.1-GA"

// Projects using Xitrum must provide a concrete implentation of SLF4J (Logback etc.)
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.6.6" % "provided"

// xitrum.imperatively uses Scala continuation, a compiler plugin --------------

autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.9.2")

scalacOptions += "-P:continuations:enable"

// https://github.com/harrah/xsbt/wiki/Cross-Build
//crossScalaVersions := Seq("2.9.1", "2.9.2")
scalaVersion := "2.9.2"

// Copy dev/build.sbt.end here when publishing to Sonatype
