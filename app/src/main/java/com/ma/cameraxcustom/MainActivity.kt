package com.ma.cameraxcustom

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ma.cameraxcustom.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val TAG="abc"

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var imageCapture: ImageCapture? =null
    private var videoCapture: VideoCapture<Recorder>?=null
    private var currentRecording: Recording? = null
    private var videoStatus: Boolean = false
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var mBinding:ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater);
        setContentView(mBinding.root)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        actionBar?.hide()
        initView()
    }

    private fun initView() {
        mBinding.rlCompoCamera.visibility=View.VISIBLE
        mBinding.rlCompoPhoto.visibility=View.INVISIBLE
        mBinding.ivPhoto.visibility=View.INVISIBLE
        mBinding.videoView.visibility=View.GONE
        mBinding.ivCamera.setOnClickListener {
            Log.e(TAG, "initView: 拍照" )
            takePicture()
        }
        mBinding.ivCamera.setOnLongClickListener() {
            Log.e(TAG, "initView: 视频" )
            if(!videoStatus) {
                startRecording()
            }
            true
        }
        mBinding.ivCamera.setOnTouchListener(){view,event->
            when(event.action){
                MotionEvent.ACTION_UP->{
                    Log.e(TAG, "initView: 视频停止" )
                    if(videoStatus){
                        currentRecording?.stop()
                    }
                }

            }
            false
        }
        mBinding.ivAlbum.setOnClickListener {
            openAlbum()
        }
        mBinding.ivSwitch.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            openCamera()
        }
        mBinding.ivFlash.setOnClickListener {
            camera?.cameraControl?.enableTorch(camera?.cameraInfo?.torchState?.value != TorchState.ON)
        }
        mBinding.tvAgain.setOnClickListener {
            mBinding.rlCompoCamera.visibility=View.VISIBLE
            mBinding.rlCompoPhoto.visibility=View.INVISIBLE
            mBinding.ivPhoto.visibility=View.INVISIBLE
            mBinding.videoView.visibility=View.INVISIBLE
        }
        mBinding.tvUse.setOnClickListener {

        }
        checkPremission()
    }

    /**
     * 检查权限
     */
    private fun checkPremission() {
        if ( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ||ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            premissionLuncher.launch(arrayOf( Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        } else {
            openCamera()
        }
    }

    private val premissionLuncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        openCamera()
    }

    /**
     * 拍照
     */
    private fun takePicture() {
        val sdf = SimpleDateFormat("_yyyyMMdd_hhmmss")
        val currentDate = sdf.format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Images.Media.DISPLAY_NAME,"camera${currentDate}")
        contentValue.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        contentValue.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraX")
        contentValue.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
        val outputFileOptions = ImageCapture
            .OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValue)
            .build()
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(error: ImageCaptureException) {

                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    showPhoto(outputFileResults.savedUri)
                }
            })
    }

    /**
     * 显示照片
     */
    private fun showPhoto(uri: Uri?) {
        uri?:return
        mBinding.ivPhoto.setImageURI(uri)
        mBinding.rlCompoCamera.visibility=View.INVISIBLE
        mBinding.rlCompoPhoto.visibility=View.VISIBLE
        mBinding.ivPhoto.visibility=View.VISIBLE
        mBinding.videoView.visibility=View.INVISIBLE
    }



    private fun openAlbum() {
        val intent = Intent(Intent.ACTION_PICK ,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//        intent.addCategory(Intent.CATEGORY_OPENABLE)
//        intent.type = "image/*"

        activityLuncher.launch(intent)
    }

    private val activityLuncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        //此处是跳转的result回调方法
        if (it.data != null && it.resultCode == Activity.RESULT_OK) {
            showPhoto(it.data?.data)
        } else {

        }
    }

    /**
     * 视频
     */
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val sdf = SimpleDateFormat("_yyyyMMdd_hhmmss")
        val currentDate = sdf.format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Video.Media.DISPLAY_NAME,"videoo${currentDate}")
        contentValue.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        contentValue.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraX")
        contentValue.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis())
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValue)
            .build()
        currentRecording = videoCapture?.output
            ?.prepareRecording(this, mediaStoreOutput)
            ?.apply { withAudioEnabled() }
            ?.start(ContextCompat.getMainExecutor(this),captureListener)
        Log.e(TAG, "initView: 视频开始" )
    }

    private val captureListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Status -> {
                Log.e(TAG, "VideoRecordEvent.Status" )
            }
            is VideoRecordEvent.Start -> {
                videoStatus=true
                Log.e(TAG, "VideoRecordEvent.Start" )
            }
            is VideoRecordEvent.Finalize-> {
                videoStatus=false
                Log.e(TAG, "VideoRecordEvent.Finalize" )
                showVedio(event.outputResults.outputUri)
            }
            is VideoRecordEvent.Pause -> {
                Log.e(TAG, "VideoRecordEvent.Pause" )
            }
            is VideoRecordEvent.Resume -> {
                Log.e(TAG, "VideoRecordEvent.Resume" )
            }
        }

    }

    /**
     * 显示视频
     */
    private fun showVedio(uri: Uri?) {
        uri?:return
        val mc = MediaController(this)
        mBinding.videoView.apply {
            setVideoURI(uri)
            setMediaController(mc)
            requestFocus()
        }.start()
        mc.show(0)
        mBinding.rlCompoCamera.visibility=View.INVISIBLE
        mBinding.rlCompoPhoto.visibility=View.VISIBLE
        mBinding.ivPhoto.visibility=View.INVISIBLE
        mBinding.videoView.visibility=View.VISIBLE
    }

    private fun openCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 相机参数设置
     */
    private fun bindPreview() {
        val d=resources.displayMetrics

        //预览设置
        val preview = Preview.Builder()
            .setTargetRotation(mBinding.cameraPreview.display.rotation)
            .build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        //拍照设置
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(d.widthPixels,d.heightPixels))
            .setTargetRotation(mBinding.cameraPreview.display.rotation)
            .build()

        //视频设置  如果list中所有请求分辨率都不受支持  会通过FallbackStrategy的设置选择最接近该分辨率的
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor).setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        //拍摄旋转角度监听
        val orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation : Int) {
                val rotation : Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation=rotation
            }
        }
        orientationEventListener.enable()

        //监听点击事件进行手动对焦
        mBinding.cameraPreview.setOnTouchListener { _, motionEvent ->
            val meteringPoint = mBinding.cameraPreview.meteringPointFactory
                .createPoint(motionEvent.x, motionEvent.y)
            val action = FocusMeteringAction.Builder(meteringPoint)
                .addPoint(meteringPoint, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)
            false
        }

        preview.setSurfaceProvider(mBinding.cameraPreview.surfaceProvider)
        cameraProvider?.unbindAll()
        camera=cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageCapture,videoCapture, preview)

        mBinding.ivSwitch.isEnabled = hasBackCamera() && hasFrontCamera()
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
