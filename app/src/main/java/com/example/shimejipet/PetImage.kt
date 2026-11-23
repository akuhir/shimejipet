package com.example.shimejipet

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PetImage {
    fun path(context:Context)=File(context.filesDir,"pet.png").absolutePath
    fun exists(context:Context)=File(path(context)).exists()
    fun import(context:Context,uri:Uri,round:Boolean) {
        val source=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}
            ?: throw IllegalStateException("Image could not be opened")
        var bitmap=source
        try {
            val exif=context.contentResolver.openInputStream(uri)?.use{ExifInterface(it)}
            val orientation=exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
            val matrix=Matrix()
            when(orientation) {
                ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f)
            }
            if(!matrix.isIdentity) bitmap=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            val scale=min(1f,700f/max(bitmap.width,bitmap.height))
            if(scale<1f) bitmap=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
            val subject=if(round) {
                val side=min(bitmap.width,bitmap.height)
                Bitmap.createBitmap(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side)
            } else bitmap
            val canvasSide=max(subject.width,subject.height).coerceAtLeast(1)
            val output=Bitmap.createBitmap(canvasSide,canvasSide,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(output)
            if(round) {
                val shader=BitmapShader(subject,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
                canvas.drawCircle(canvasSide/2f,canvasSide/2f,canvasSide/2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply{this.shader=shader})
            } else {
                canvas.drawBitmap(subject,(canvasSide-subject.width)/2f,(canvasSide-subject.height).toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            FileOutputStream(path(context)).use{output.compress(Bitmap.CompressFormat.PNG,100,it)}
            if(subject!==bitmap && subject!==source) subject.recycle()
            if(bitmap!==source) bitmap.recycle()
            source.recycle(); output.recycle()
        } catch(e:Exception) {
            if(!source.isRecycled) source.recycle()
            if(bitmap!==source && !bitmap.isRecycled) bitmap.recycle()
            throw e
        }
    }
}
