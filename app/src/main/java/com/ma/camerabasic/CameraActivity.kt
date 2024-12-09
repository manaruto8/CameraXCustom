package com.ma.camerabasic


import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager

import android.graphics.Rect
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size

import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ma.camerabasic.databinding.ActivityCameraBinding
import com.ma.camerabasic.utils.CameraConfig
import com.ma.camerabasic.utils.CameraUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class CameraActivity : BaseActivity<ActivityCameraBinding>(),Camera.PreviewCallback,Camera.PictureCallback,Camera.AutoFocusCallback {

    private var lensFacing: Int = Camera.CameraInfo.CAMERA_FACING_BACK
    private var flashMode: String =  Camera.Parameters.FLASH_MODE_OFF
    private var mSurfaceHolder: SurfaceHolder?=null
    private var mCamera: Camera?=null
    private var mParameters: Camera.Parameters?=null
    private var mediaRecorder:MediaRecorder?=null
    private var videoStatus = CameraConfig.VideoState.PREVIEW
    private var mode = CameraConfig.CameraMode.PHOTO
    private var ratio = CameraConfig.CameraRatio.RECTANGLE_4_3
    private var relative:Int=0
    private var file: File? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }

    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            if(mode == CameraConfig.CameraMode.PHOTO) {
                mCamera?.takePicture(null,null,this)
            } else if (mode == CameraConfig.CameraMode.VIDEO
                && videoStatus == CameraConfig.VideoState.RECORDING){
                stopRecording()
            }

        }
        mBinding.ivCamera.setOnLongClickListener {
            if(videoStatus == CameraConfig.VideoState.PREVIEW) {
                startRecording()
            } else {
                stopRecording()
            }
            true
        }

        mBinding.ivAlbum.setOnClickListener {
            if (file != null) {
                CameraUtils.showResult(this, file)
            } else {

            }
        }
        mBinding.ivSwitch.setOnClickListener {
            releaseCamera()
            lensFacing = if(lensFacing==Camera.CameraInfo.CAMERA_FACING_BACK){
                Camera.CameraInfo.CAMERA_FACING_FRONT
            }else{
                Camera.CameraInfo.CAMERA_FACING_BACK
            }
            openCamera()
        }
        mBinding.ivFlash.setOnClickListener {
            flashMode = if(flashMode==Camera.Parameters.FLASH_MODE_OFF){
                Camera.Parameters.FLASH_MODE_TORCH
            }else{
                Camera.Parameters.FLASH_MODE_OFF
            }
            mParameters=mCamera?.parameters
            mParameters?.flashMode=flashMode
            mCamera?.parameters=mParameters
        }


        mBinding.surfaceView.setOnTouchListener { _, motionEvent ->
            val areaX = (motionEvent.x / mBinding.surfaceView.width * 2000) - 1000
            val areaY = (motionEvent.y / mBinding.surfaceView.height * 2000) - 1000

            val focusArea = Rect()
            focusArea.left = (areaX.toInt() - 100).coerceAtLeast(-1000)
            focusArea.top = (areaY.toInt() - 100).coerceAtLeast(-1000)
            focusArea.right = (areaX.toInt() + 100).coerceAtMost(1000)
            focusArea.bottom = (areaY.toInt() + 100).coerceAtMost(1000)

            val cameraArea = Camera.Area(focusArea, 1000)
            val meteringAreas: MutableList<Camera.Area> = ArrayList()
            val focusAreas: MutableList<Camera.Area> = ArrayList()
            mParameters?.let {
                if (it.maxNumMeteringAreas > 0) {
                    meteringAreas.add(cameraArea)
                    focusAreas.add(cameraArea)
                }
                it.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                it.focusAreas = focusAreas
                it.meteringAreas = meteringAreas
            }
            mCamera?.let {
                try {
                    it.cancelAutoFocus()
                    it.parameters = mParameters
                    it.autoFocus(this)
                } catch (e:Exception) {
                }
            }
            false
        }
        checkPremission()
    }


    private fun checkPremission() {
        if ( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            premissionLuncher.launch(arrayOf( Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        } else {
            setHolder()
        }
    }

    private val premissionLuncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        setHolder()
    }

    private fun setHolder(){
        mSurfaceHolder= mBinding.surfaceView.holder
        mSurfaceHolder?.addCallback(object :SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceCreated" )
                openCamera()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.e(TAG, "surfaceChanged $p2 - $p3" )
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceDestroyed" )
            }

        })
    }

    override fun onAutoFocus(p0: Boolean, p1: Camera?) {

    }


    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {

    }


    @SuppressLint("ClickableViewAccessibility")
    private fun openCamera() {
        if (!hasCamera(lensFacing)) return
        try {
            Log.e(TAG, "initCamera:select id ${lensFacing}" )
            mCamera=Camera.open(lensFacing)
            mParameters= mCamera?.parameters

            val previewSizes = ArrayList<Size> ()
            mParameters?.supportedPreviewSizes?.forEach{
                    previewSizes.add(Size(it.width, it.height))
                    Log.e(TAG, "startPreview preview: ${it.width} * ${it.height}" )
                }

            val pictureSizes = ArrayList<Size> ()
            mParameters?.supportedPictureSizes?.forEach{
                pictureSizes.add(Size(it.width, it.height))
                Log.e(TAG, "startPreview jpeg: ${it.width} * ${it.height}" )
            }

            val previewSize= CameraUtils.chooseSize(previewSizes.toTypedArray(),windowManager.defaultDisplay.height, windowManager.defaultDisplay.width, ratio.size, true)
            val pictureSize= CameraUtils.chooseSize(pictureSizes.toTypedArray(),windowManager.defaultDisplay.height, windowManager.defaultDisplay.width, ratio.size, false)
            mParameters?.setPictureSize(pictureSize.width,pictureSize.height)
            mParameters?.setPreviewSize(previewSize.width,previewSize.height)
            mBinding.surfaceView.setAspectRatio(previewSize.width,previewSize.height)

            Log.e(TAG, "startPreview bestPreviewSize: ${previewSize.width} * ${previewSize.height}" )
            Log.e(TAG, "startPreview bestPictureSize: ${pictureSize.width} * ${pictureSize.height}" )

            mParameters?.setRotation(getCameraDisplayOrientation())
            mCamera?.parameters=mParameters
            mCamera?.setPreviewCallback(this)
            mCamera?.setPreviewDisplay(mSurfaceHolder)
            mCamera?.setDisplayOrientation(getCameraDisplayOrientation())
            mCamera?.startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "open fail ${e.message}" )
        }
    }


    private fun hasCamera(lensFacing: Int): Boolean {
        var info = Camera.CameraInfo()
        var cameraNum=0
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            Log.e(TAG, "initCamera:id $i" )
            Log.e(TAG, "initCamera:facing ${info.facing}" )
            if (info.facing ==lensFacing){
                cameraNum++
            }
        }
        return cameraNum>0
    }


    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        p1?.startPreview()

        file = getOutputMediaFile("jpg")

        try {
            val fos = FileOutputStream(file)
            fos.write(p0)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: ${e.message}")
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: ${e.message}")
        }

        CameraUtils.showThumbnail(this, file, mBinding.ivAlbum)

        CameraUtils.showThumbnail(this, file, mBinding.ivAlbum)
    }



    private fun startRecording() {

        mediaRecorder = MediaRecorder()
        mCamera?.let{
            it.unlock()
            mediaRecorder?.run {
                setCamera(mCamera)
                mParameters?.supportedVideoSizes?.forEach {
                    Log.e(TAG, "startRecording media: ${it.width} * ${it.height}" )
                }

                file = getOutputMediaFile("mp4")

                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFile(file)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
                setOrientationHint(getCameraDisplayOrientation())
                setPreviewDisplay(mBinding.surfaceView.holder.surface)
                try {
                    prepare()
                    start()

                    mBinding.tvTips.visibility= View.VISIBLE
                    mBinding.ivAlbum.visibility= View.GONE
                    mBinding.ivSwitch.visibility= View.GONE
                    mode = CameraConfig.CameraMode.VIDEO
                    videoStatus=CameraConfig.VideoState.RECORDING

                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException preparing MediaRecorder: " + e.message)
                    releaseMediaRecorder()
                    return
                } catch (e: IOException) {
                    Log.e(TAG, "IOException preparing MediaRecorder: " + e.message)
                    releaseMediaRecorder()
                    return
                }
            }
        }

    }

    private fun stopRecording() {
        mediaRecorder?.stop()
        releaseMediaRecorder()

        mBinding.tvTips.visibility= View.GONE
        mBinding.ivAlbum.visibility= View.VISIBLE
        mBinding.ivSwitch.visibility= View.VISIBLE
        mode = CameraConfig.CameraMode.PHOTO
        videoStatus=CameraConfig.VideoState.PREVIEW

        CameraUtils.showThumbnail(this, file, mBinding.ivAlbum)
    }


    private fun getOutputMediaFile(format: String): File{
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CameraBasic" + File.separator + "Camera"
        )
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.e(TAG, "failed to create directory")
                    return mediaStorageDir
                }
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return File(
            mediaStorageDir.path + File.separator +
                    "camera_${format}_${timeStamp}.${format}"
        )
    }



    private fun getCameraDisplayOrientation():Int{
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(lensFacing, info)
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info .facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        Log.e(TAG, "setCameraDisplayOrientation: degrees：" + degrees + "---rotation：" + info.orientation + "---result：" + result)
        return result
    }

    private fun releaseCamera() {
        mCamera?.setPreviewCallback(null)
        mCamera?.stopPreview()
        mCamera?.release()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mCamera?.lock()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaRecorder()
        releaseCamera()
    }
}