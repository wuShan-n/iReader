package com.ireader.core.work.enrich

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object BitmapIO {
    fun savePng(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
    }
}
