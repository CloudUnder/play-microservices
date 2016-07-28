logLevel := Level.Warn

// Scala coverage reports
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")

// For publishing to Maven Central
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

// PGP signing
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
