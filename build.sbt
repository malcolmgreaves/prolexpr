name := "prolexpr"

version := "1.0"

organization := "edu.cmu.ml"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Akka Repository" at "http://repo.akka.io/releases/",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "14.0.1",
  "de.bwaldvogel" % "liblinear" % "1.92",
  //
  "edu.stanford.nlp" % "stanford-corenlp" % "3.3.1",
  // 
  "log4j" % "log4j" % "1.2.16", 
  //
  "org.scalanlp" % "breeze-core_2.10" % "0.4",
  "org.scalanlp" % "breeze-math_2.10" % "0.4",
  "org.scalanlp" % "nak" % "1.1.3",
  "org.scalanlp" % "chalk" % "1.2.0",
  //
  "com.github.scala-incubator.io" % "scala-io_2.10.2" % "0.4.2",
  "org.scala-lang" % "scala-pickling_2.10" % "0.8.0",
  //
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.1",
  //
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
