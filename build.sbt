name := "flume-http-sink"

organization := "uk.gov.hmrc"

version := "1.0-SNAPSHOT"

description := "Flume HTTP Sink"

publishMavenStyle := true

crossPaths := false

autoScalaLibrary := false

libraryDependencies ++= Seq(
    "org.apache.flume" % "flume-ng-core" % "1.7.0"
      exclude("org.apache.httpcomponents", "httpclient")
      exclude("commons-lang", "commons-lang")
      exclude("org.slf4j", "slf4j-api")
      exclude("com.google.guava", "guava"),
    "org.apache.flume" % "flume-ng-sdk" % "1.7.0"
      exclude("org.apache.thrift", "libthrift"),
    "com.fasterxml.jackson.core" % "jackson-core" % "2.3.1",
    "junit" % "junit-dep" % "4.10" % "test",
    "com.github.tomakehurst" % "wiremock" % "1.56" % "test",
    "com.google.collections" % "google-collections" % "1.0",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

resolvers += Resolver.mavenLocal
