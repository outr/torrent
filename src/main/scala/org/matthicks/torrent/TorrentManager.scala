package org.matthicks.torrent

import java.io.File

import com.frostwire.jlibtorrent.alerts._
import com.frostwire.jlibtorrent.swig.settings_pack.int_types.{proxy_port, proxy_type}
import com.frostwire.jlibtorrent.swig.settings_pack.proxy_type_t
import com.frostwire.jlibtorrent.swig.settings_pack.string_types.{proxy_hostname, proxy_password, proxy_username}
import com.frostwire.jlibtorrent.{AlertListener, SessionManager, SettingsPack, TorrentInfo}

object TorrentManager {
  private var directory: File = _
  lazy val saveDirectory = new File(directory, "torrent")

  lazy val sessionManager: SessionManager = new SessionManager

  private var torrents = Map.empty[String, RunningTorrent]

  def start(directory: File, host: String, port: Int, username: String, password: String): Unit = {
    this.directory = directory

    println(s"Monitoring ${directory.getCanonicalPath} for .torrent files...")

    System.loadLibrary("jlibtorrent")

    saveDirectory.mkdirs()

    val settings = new SettingsPack
    settings.setInteger(proxy_type.swigValue(), proxy_type_t.socks5_pw.swigValue())
    settings.setString(proxy_hostname.swigValue(), host)
    settings.setInteger(proxy_port.swigValue(), port)
    settings.setString(proxy_username.swigValue(), username)
    settings.setString(proxy_password.swigValue(), password)

    sessionManager.addListener(new AlertListener {
      override def types(): Array[Int] = null

      override def alert(alert: Alert[_]): Unit = alert match {
        case a: TorrentAddedAlert => {
          println(s"Torrent added!")
          TorrentManager.synchronized {
            val t = torrents(a.torrentName())
            torrents += a.torrentName() -> t.copy(handle = Some(a.handle()))
          }
          a.handle().resume()
        }
        case a: BlockFinishedAlert => {
          val progress = (a.handle().status().progress() * 100.0).toInt
          val torrent = torrents(a.torrentName())
          if (torrent.progress != progress) {
            println(s"${a.torrentName()}: $progress% (${a.handle().status().totalDownload()})")
            torrent.progress = progress
          }
        }
        case a: TorrentFinishedAlert => {
          println("Torrent finished!")
        }
        case a: StatsAlert => {
          val torrent = torrents(a.torrentName())
          val status = a.handle().status()
          torrent.downloadRate = status.downloadRate()
          torrent.numPeers = status.numPeers()
          torrent.numSeeds = status.numSeeds()
          torrent.numConnections = status.numConnections()
          torrent.totalUpload = status.totalUpload()
          torrent.isSeeding = status.isSeeding
        }
        case _ => //println(s"Unhandled alert: $alert (${alert.getClass.getName})")   // ignore others
      }
    })

    sessionManager.start()
    sessionManager.applySettings(settings)

    checkTorrents()

    while (true) {
      Thread.sleep(60000)
      checkTorrents()
    }
    //    sessionManager.stop()
  }

  private def checkTorrents(): Unit = {
    directory.listFiles().foreach { file =>
      if (file.getName.endsWith(".torrent")) {
        val torrent = torrents.values.find(_.file == file)
        if (torrent.isEmpty) {
          val info = new TorrentInfo(file)
          TorrentManager.synchronized {
            torrents += info.name() -> RunningTorrent(info.name(), file, info)
          }
          println(s"Adding torrent: ${file.getName} (${info.name()})...")
          sessionManager.download(info, saveDirectory)
        }
      }
    }

    // Stop torrents that have been removed
    torrents.values.foreach { torrent =>
      if (!torrent.file.exists()) {
        torrent.handle match {
          case Some(handle) => {
            sessionManager.remove(handle)
            println(s"Removed torrent ${torrent.info.name()}")
          }
          case None => println(s"Could not remove torrent ${torrent.info.name()} as no handle exists!")
        }
        TorrentManager.synchronized {
          torrents -= torrent.info.name()
        }
      }
    }

    val seeding = torrents.values.toList.filter(_.isSeeding)
    val downloading = torrents.values.toList.filter(!_.isSeeding)
    println(s"Seeding: ${seeding.size}:")
    seeding.foreach { torrent =>
      println(s"\t${torrent.name} - ${torrent.totalUpload}")
    }
    println(s"Downloading: ${downloading.size}:")
    downloading.foreach { torrent =>
      println(s"\t${torrent.name} - ${torrent.downloadRate} / Peers: ${torrent.numPeers}, Seeds: ${torrent.numSeeds}, Connections: ${torrent.numConnections}")
    }
  }
}
