name := "hello-libtorrent"
organization := "org.matthicks"
version := "1.0.0"

scalaVersion := "2.12.1"
sbtVersion := "0.13.13"
fork := true

assemblyJarName in assembly := "torrent-downloader.jar"

libraryDependencies += "com.frostwire" % "jlibtorrent" % "1.2.0.6-RC4"
libraryDependencies += "com.frostwire" % "jlibtorrent-linux" % "1.2.0.6-RC4"