name := "play-microservices"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

val playVersion = "2.5.4"

libraryDependencies ++= Seq(
	"com.typesafe.play" %% "play-ws" % playVersion % "provided",
	"com.typesafe.play" %% "play-json" % playVersion % "provided"
)

libraryDependencies ++= Seq(
	"com.typesafe.play" %% "play-test" % playVersion,
	"org.scalatest" %% "scalatest" % "2.2.6",
	"org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1",
	"de.leanovate.play-mockws" %% "play-mockws" % "2.5.0"
) map (_ % "test")

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
	val nexus = "https://oss.sonatype.org/"
	if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
	else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

organization := "io.cloudunder"

organizationName := "Cloud Under Ltd"

organizationHomepage := Some(url("https://cloudunder.io"))

description := "Simplified access to HTTP services in Play Framework applications"

licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

scmInfo := Some(ScmInfo(url("http://github.com/CloudUnder/play-microservices"), "scm:git:git@github.com:CloudUnder/play-microservices.git", Some("scm:git:ssh://github.com:CloudUnder/play-microservices.git")))

homepage := Some(url("https://github.com/CloudUnder/play-microservices"))

pomIncludeRepository := { _ => false }

pomExtra :=
	<developers>
		<developer>
			<name>Nick Zahn</name>
			<organization>Cloud Under Ltd</organization>
			<organizationUrl>https://cloudunder.io</organizationUrl>
		</developer>
	</developers>
