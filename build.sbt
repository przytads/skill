name := "skill"

version := "0.1"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
	"com.novocode" % "junit-interface" % "0.10" % "test",
	"org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings
