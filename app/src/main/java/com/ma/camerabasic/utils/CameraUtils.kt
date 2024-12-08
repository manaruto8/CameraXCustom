package com.ma.camerabasic.utils

import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.util.Log
import android.util.Size
import android.view.Surface
import java.nio.ReadOnlyBufferException
import kotlin.experimental.inv
import kotlin.math.abs


class CameraUtils {


    companion object {


        private val TAG = javaClass.simpleName

        fun computeRelativeRotation(
            characteristics: CameraCharacteristics,
            surfaceRotation: Int
        ): Int {
            val sensorOrientationDegrees =
                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

            val deviceOrientationDegrees = when (surfaceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
            val degress = (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360

//            Log.e(TAG, "computeRelativeRotation sensorOrientationDegrees $sensorOrientationDegrees")
//            Log.e(TAG, "computeRelativeRotation deviceOrientationDegrees $deviceOrientationDegrees")
//            Log.e(TAG, "computeRelativeRotation degress $degress")

            return degress
        }


        fun chooseSize(
            choices: Array<Size>,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size,
            preview: Boolean
        ): Size {

            var size = aspectRatio
            var select = ArrayList<Size>()

            if (aspectRatio.width == 0 || aspectRatio.width == 0) {
                size = Size(maxWidth, maxHeight)
            }

            choices.forEach {
                if (preview) {
                    if (it.width <= maxWidth && it.height <= maxHeight ) {
                        select.add(it)
                    }
                } else {
                    select.add(it)
                }
            }

            select.sortBy { selectSize: Size ->
                abs(size.width.toFloat()/size.height - selectSize.width.toFloat()/selectSize.height)
            }

            select.forEach {
                Log.e(TAG, "choosePictureSize : ${size.width.toFloat()/size.height} --- ${it.width}*${it.height}--- ${it.width.toFloat()/it.height}" )
            }

            return select[0]
        }


        fun YUV_420_888toNV21(image: Image): ByteArray {
            val width = image.width
            val height = image.height
            val ySize = width * height
            val uvSize = width * height / 4

            val nv21 = ByteArray(ySize + uvSize * 2)

            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            var rowStride = image.planes[0].rowStride
            assert(image.planes[0].pixelStride == 1)

            var pos = 0

            if (rowStride == width) { // likely
                yBuffer[nv21, 0, ySize]
                pos += ySize
            } else {
                var yBufferPos = -rowStride.toLong() // not an actual position
                while (pos < ySize) {
                    yBufferPos += rowStride.toLong()
                    yBuffer.position(yBufferPos.toInt())
                    yBuffer[nv21, pos, width]
                    pos += width
                }
            }

            rowStride = image.planes[2].rowStride
            val pixelStride = image.planes[2].pixelStride

            assert(rowStride == image.planes[1].rowStride)
            assert(pixelStride == image.planes[1].pixelStride)

            if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
                // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
                val savePixel = vBuffer[1]
                try {
                    vBuffer.put(1, savePixel.inv() as Byte)
                    if (uBuffer[0] == savePixel.inv() as Byte) {
                        vBuffer.put(1, savePixel)
                        vBuffer.position(0)
                        uBuffer.position(0)
                        vBuffer[nv21, ySize, 1]
                        uBuffer[nv21, ySize + 1, uBuffer.remaining()]

                        return nv21 // shortcut
                    }
                } catch (ex: ReadOnlyBufferException) {
                    // unfortunately, we cannot check if vBuffer and uBuffer overlap
                }

                // unfortunately, the check failed. We must save U and V pixel by pixel
                vBuffer.put(1, savePixel)
            }

            // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
            // but performance gain would be less significant
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vuPos = col * pixelStride + row * rowStride
                    nv21[pos++] = vBuffer[vuPos]
                    nv21[pos++] = uBuffer[vuPos]
                }
            }

            return nv21
        }


    }


}