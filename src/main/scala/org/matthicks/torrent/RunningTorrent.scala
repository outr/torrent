package org.matthicks.torrent

import java.io.File

import com.frostwire.jlibtorrent.{TorrentHandle, TorrentInfo}

case class RunningTorrent(name: String,
                          file: File,
                          info: TorrentInfo,
                          handle: Option[TorrentHandle] = None,
                          var downloadRate: Int = 0,
                          var numPeers: Int = 0,
                          var numSeeds: Int = 0,
                          var numConnections: Int = 0,
                          var totalUpload: Long = 0L,
                          var isSeeding: Boolean = false,
                          var progress: Int = -1)
