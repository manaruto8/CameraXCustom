/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ma.cameraxcustom.utils

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * A [SurfaceView] that can be adjusted to a specified aspect ratio and
 * performs center-crop transformation of input frames.
 */
class AutoFitTextureView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    private var aspectRatio = 0f


    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)
        } else {

            // Performs center-crop transformation of the camera frames
            val newWidth: Int
            val newHeight: Int
            val actualRatio = if (width < height) aspectRatio else 1f / aspectRatio
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }

            Log.e(TAG, "Measured dimensions set: $newWidth x $newHeight")
        }


    }

    fun computeTransformationMatrix(
        textureView: TextureView,
        characteristics: CameraCharacteristics,
        previewSize: Size,
        surfaceRotation: Int
    ): Matrix {
        val matrix = Matrix()

        val surfaceRotationDegrees = when (surfaceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        /* Rotation required to transform from the camera sensor orientation to the
         * device's current orientation in degrees. */
        val relativeRotation = computeRelativeRotation(characteristics, surfaceRotationDegrees)

        /* Scale factor required to scale the preview to its original size on the x-axis. */
        val scaleX =
            if (relativeRotation % 180 == 0) {
                textureView.width.toFloat() / previewSize.width
            } else {
                textureView.width.toFloat() / previewSize.height
            }
        /* Scale factor required to scale the preview to its original size on the y-axis. */
        val scaleY =
            if (relativeRotation % 180 == 0) {
                textureView.height.toFloat() / previewSize.height
            } else {
                textureView.height.toFloat() / previewSize.width
            }

        /* Scale factor required to fit the preview to the TextureView size. */
        val finalScale = min(scaleX, scaleY)

        /* The scale will be different if the buffer has been rotated. */
        if (relativeRotation % 180 == 0) {
            matrix.setScale(
                textureView.height / textureView.width.toFloat() / scaleY * finalScale,
                textureView.width / textureView.height.toFloat() / scaleX * finalScale,
                textureView.width / 2f,
                textureView.height / 2f
            )
        } else {
            matrix.setScale(
                1 / scaleX * finalScale,
                1 / scaleY * finalScale,
                textureView.width / 2f,
                textureView.height / 2f
            )
        }

        // Rotate the TextureView to compensate for the Surface's rotation.
        matrix.postRotate(
            -surfaceRotationDegrees.toFloat(),
            textureView.width / 2f,
            textureView.height / 2f
        )

        return matrix
    }


    public fun computeRelativeRotation(
        characteristics: CameraCharacteristics,
        surfaceRotationDegrees: Int
    ): Int {
        val sensorOrientationDegrees =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        // Reverse device orientation for back-facing cameras.
        val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT
        ) 1 else -1

        // Calculate desired orientation relative to camera orientation to make
        // the image upright relative to the device orientation.
        return (sensorOrientationDegrees - surfaceRotationDegrees * sign + 360) % 360
    }

    companion object {
        private val TAG = AutoFitTextureView::class.java.simpleName
    }
}
