package com.avolut.prasi

import Decompress
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import java.io.File


object Bundle {
  private lateinit var db: BundleDBHelper

  val version: String?
    get() {
      val list = listIn(arrayOf("version"))
      if (list.size > 0) return list[0].text
      return ""
    }

  val siteId: String?
    get() {
      val list = listIn(arrayOf("site_id"))
      if (list.size > 0) return list[0].text
      return ""
    }

  val baseUrl: String?
    get() {
      val list = listIn(arrayOf("base_url"))
      if (list.size > 0) return list[0].text
      return ""
    }

  fun set(key: String, value: String) {
    val db = this.db.writableDatabase

    val values = ContentValues().apply {
      put(BundleEntry.PATH, key)
      put(BundleEntry.CONTENT, value)
      put(BundleEntry.TYPE, "")
    }

    if (get(key) == null) {
      db.insert("files", null, values)
    } else {
      db.update("files", values, " path = ?", arrayOf(key))
    }
  }

  fun get(key: String): String? {
    val result = listExact(key)
    if (result.size > 0) return result[0].text.toString()
    return null
  }

  fun listIn(path: Array<String>): MutableList<BundleItem> {
    val selection = "${BundleEntry.PATH} in (${path.joinToString { e -> "?" }})"
    return executeList(selection, path)
  }

//  fun listLikeOr(paths: Array<String>): MutableList<BundleItem> {
//    val selection = paths.joinToString(" OR ") { "${BundleEntry.PATH} like ?" }
//    return executeList(selection, paths)
//  }
//
//  fun listLike(path: String): MutableList<BundleItem> {
//
//    val selection = "${BundleEntry.PATH} like ?"
//    val selectionArgs = arrayOf(path)
//
//    return executeList(selection, selectionArgs)
//  }

  fun listExact(path: String): MutableList<BundleItem> {

    val selection = "${BundleEntry.PATH} = ?"
    val selectionArgs = arrayOf(path)

    return executeList(selection, selectionArgs)
  }

  suspend fun respondItem(call: ApplicationCall, item: BundleItem) {
    if (item.blob != null) {
      call.respondBytes(
        contentType = ContentType.parse(item.type), null
      ) {
        if (item.blob != null) return@respondBytes item.blob!!
        return@respondBytes ByteArray(0)
      }
    } else {
      (item.text)?.let {
        call.respondText(
          contentType = ContentType.parse(item.type),
          text = it
        )
      }
    }
  }

  suspend fun respondFile(call: ApplicationCall, filename: String, type: String) {
    val res = this.get(filename)
    if (res != null) {
      call.respondText(
        text = res, contentType = ContentType.parse(type),
      )
    }
  }

  fun init(ctx: Context) {
    val bundle = File(ctx.filesDir, "bundle.sqlite")
    val zipName = ctx.assets.list("")?.find { e -> e.startsWith("bundle") }

    if (zipName != null) {
      if (!bundle.exists()) {
        Decompress.unzipFromAssets(ctx, zipName, "")
        this.db = BundleDBHelper(ctx)
        set("bundle_name", zipName)
      } else {
        val bundleSize = bundle.length()
        if (bundleSize < 1_000_000) {
          bundle.delete()
          Decompress.unzipFromAssets(ctx, zipName, "")
          this.db = BundleDBHelper(ctx)
          set("bundle_name", zipName)
        } else {
          this.db = BundleDBHelper(ctx)
          val currentZipName = get("bundle_name")
          if (currentZipName != zipName) {
            this.db.close()
            bundle.delete()
            Decompress.unzipFromAssets(ctx, zipName, "")
            this.db = BundleDBHelper(ctx)
            set("bundle_name", zipName)
          }
        }
      }
    }
  }

  private fun executeList(
    selection: String,
    selectionArgs: Array<String>,
  ): MutableList<BundleItem> {

    val projection = arrayOf(
      BundleEntry.PATH, BundleEntry.TYPE,
      BundleEntry.CONTENT,
    )

    val db = this.db.readableDatabase

    val cursor = db.query(
      "files",          // The table to query
      projection,             // The array of columns to return (pass null to get all)
      selection,              // The columns for the WHERE clause
      selectionArgs,          // The values for the WHERE clause
      null,          // don't group the rows
      null,           // don't filter by row groups
      null           // The sort order
    )
    val items = mutableListOf<BundleItem>()
    with(cursor) {
      while (moveToNext()) {
        val path = getString(getColumnIndexOrThrow(BundleEntry.PATH))
        val ext = path.substringAfterLast('.', "")
        if (BINARY_EXTENSION.contains(ext)) {
          items.add(
            BundleItem(
              path,
              type = getString(getColumnIndexOrThrow(com.avolut.prasi.Bundle.BundleEntry.TYPE)),
              blob = getBlob(getColumnIndexOrThrow(com.avolut.prasi.Bundle.BundleEntry.CONTENT)),
              text = null
            )
          )
        } else {
          items.add(
            BundleItem(
              path,
              type = getString(getColumnIndexOrThrow(com.avolut.prasi.Bundle.BundleEntry.TYPE)),
              blob = null,
              text = getString(getColumnIndexOrThrow(com.avolut.prasi.Bundle.BundleEntry.CONTENT))
            )
          )
        }
      }
    }
    cursor.close()

    return items
  }

  object BundleEntry : BaseColumns {
    const val PATH = "path"
    const val CONTENT = "content"
    const val TYPE = "type"
  }
}


class BundleDBHelper(context: Context) :
  SQLiteOpenHelper(DatabaseContext(context), DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // This database is only a cache for online data, so its upgrade policy is
    // to simply to discard the data and start over
    onCreate(db)
  }

  override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    onUpgrade(db, oldVersion, newVersion)
  }

  companion object {
    // If you change the database schema, you must increment the database version.
    const val DATABASE_VERSION = 1
    const val DATABASE_NAME = "bundle.sqlite"
  }
}

internal class DatabaseContext(base: Context?) : ContextWrapper(base) {
  override fun getDatabasePath(name: String): File {
    return File(baseContext.filesDir, name)
  }

  override fun openOrCreateDatabase(
    name: String,
    mode: Int,
    factory: CursorFactory,
    errorHandler: DatabaseErrorHandler?,
  ): SQLiteDatabase {
    return openOrCreateDatabase(name, mode, factory)
  }

  override fun openOrCreateDatabase(
    name: String,
    mode: Int,
    factory: CursorFactory,
  ): SQLiteDatabase {
    val result = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), null)
    return result
  }
}

class BundleItem(var path: String, var type: String, var blob: ByteArray?, var text: String?) {

}


val BINARY_EXTENSION = arrayOf(
  "3dm",
  "3ds",
  "3g2",
  "3gp",
  "7z",
  "a",
  "aac",
  "adp",
  "afdesign",
  "afphoto",
  "afpub",
  "ai",
  "aif",
  "aiff",
  "alz",
  "ape",
  "apk",
  "appimage",
  "ar",
  "arj",
  "asf",
  "au",
  "avi",
  "bak",
  "baml",
  "bh",
  "bin",
  "bk",
  "bmp",
  "btif",
  "bz2",
  "bzip2",
  "cab",
  "caf",
  "cgm",
  "class",
  "cmx",
  "cpio",
  "cr2",
  "cur",
  "dat",
  "dcm",
  "deb",
  "dex",
  "djvu",
  "dll",
  "dmg",
  "dng",
  "doc",
  "docm",
  "docx",
  "dot",
  "dotm",
  "dra",
  "DS_Store",
  "dsk",
  "dts",
  "dtshd",
  "dvb",
  "dwg",
  "dxf",
  "ecelp4800",
  "ecelp7470",
  "ecelp9600",
  "egg",
  "eol",
  "eot",
  "epub",
  "exe",
  "f4v",
  "fbs",
  "fh",
  "fla",
  "flac",
  "flatpak",
  "fli",
  "flv",
  "fpx",
  "fst",
  "fvt",
  "g3",
  "gh",
  "gif",
  "graffle",
  "gz",
  "gzip",
  "h261",
  "h263",
  "h264",
  "icns",
  "ico",
  "ief",
  "img",
  "ipa",
  "iso",
  "jar",
  "jpeg",
  "jpg",
  "jpgv",
  "jpm",
  "jxr",
  "key",
  "ktx",
  "lha",
  "lib",
  "lvp",
  "lz",
  "lzh",
  "lzma",
  "lzo",
  "m3u",
  "m4a",
  "m4v",
  "mar",
  "mdi",
  "mht",
  "mid",
  "midi",
  "mj2",
  "mka",
  "mkv",
  "mmr",
  "mng",
  "mobi",
  "mov",
  "movie",
  "mp3",
  "mp4",
  "mp4a",
  "mpeg",
  "mpg",
  "mpga",
  "mxu",
  "nef",
  "npx",
  "numbers",
  "nupkg",
  "o",
  "odp",
  "ods",
  "odt",
  "oga",
  "ogg",
  "ogv",
  "otf",
  "ott",
  "pages",
  "pbm",
  "pcx",
  "pdb",
  "pdf",
  "pea",
  "pgm",
  "pic",
  "png",
  "pnm",
  "pot",
  "potm",
  "potx",
  "ppa",
  "ppam",
  "ppm",
  "pps",
  "ppsm",
  "ppsx",
  "ppt",
  "pptm",
  "pptx",
  "psd",
  "pya",
  "pyc",
  "pyo",
  "pyv",
  "qt",
  "rar",
  "ras",
  "raw",
  "resources",
  "rgb",
  "rip",
  "rlc",
  "rmf",
  "rmvb",
  "rpm",
  "rtf",
  "rz",
  "s3m",
  "s7z",
  "scpt",
  "sgi",
  "shar",
  "snap",
  "sil",
  "sketch",
  "slk",
  "smv",
  "snk",
  "so",
  "stl",
  "suo",
  "sub",
  "swf",
  "tar",
  "tbz",
  "tbz2",
  "tga",
  "tgz",
  "thmx",
  "tif",
  "tiff",
  "tlz",
  "ttc",
  "ttf",
  "txz",
  "udf",
  "uvh",
  "uvi",
  "uvm",
  "uvp",
  "uvs",
  "uvu",
  "viv",
  "vob",
  "war",
  "wav",
  "wax",
  "wbmp",
  "wdp",
  "weba",
  "webm",
  "webp",
  "whl",
  "wim",
  "wm",
  "wma",
  "wmv",
  "wmx",
  "woff",
  "woff2",
  "wrm",
  "wvx",
  "xbm",
  "xif",
  "xla",
  "xlam",
  "xls",
  "xlsb",
  "xlsm",
  "xlsx",
  "xlt",
  "xltm",
  "xltx",
  "xm",
  "xmind",
  "xpi",
  "xpm",
  "xwd",
  "xz",
  "z",
  "zip",
  "zipx"
)