import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object Decompress {
  private const val BUFFER_SIZE = 1024 * 10
  private const val TAG = "Decompress"

  fun unzipFromAssets(context: Context, zipFile: String?, destination: String?) {
    var destination = destination
    try {
      if (destination == null || destination.length == 0) destination =
        context.getFilesDir().getAbsolutePath()
      if (zipFile != null) {
        val stream: InputStream = context.getAssets().open(zipFile)
        unzip(stream, destination)
      }
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  fun unzip(zipFile: String?, location: String?) {
    try {
      val fin = FileInputStream(zipFile)
      unzip(fin, location)
    } catch (e: FileNotFoundException) {
      e.printStackTrace()
    }
  }

  fun unzip(stream: InputStream?, destination: String?) {
    dirChecker(destination, "")
    val buffer = ByteArray(BUFFER_SIZE)
    try {
      val zin = ZipInputStream(stream)
      var ze: ZipEntry? = null

      while ((zin.nextEntry.also { ze = it }) != null) {
        Log.v(TAG, "Unzipping " + ze!!.name)

        if (ze!!.isDirectory) {
          dirChecker(destination, ze!!.name)
        } else {
          val f = File(destination, ze!!.name)
          if (!f.exists()) {
            val success = f.createNewFile()
            if (!success) {
              Log.w(TAG, "Failed to create file " + f.name)
              continue
            }
            val fout = FileOutputStream(f)
            var count: Int
            while ((zin.read(buffer).also { count = it }) != -1) {
              fout.write(buffer, 0, count)
            }
            zin.closeEntry()
            fout.close()
          }
        }
      }
      zin.close()
    } catch (e: Exception) {
      Log.e(TAG, "unzip", e)
    }
  }

  private fun dirChecker(destination: String?, dir: String) {
    val f = File(destination, dir)

    if (!f.isDirectory) {
      val success = f.mkdirs()
      if (!success) {
        Log.w(TAG, "Failed to create folder " + f.name)
      }
    }
  }
}