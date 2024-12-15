package com.ma.camerabasic

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.ma.camerabasic.databinding.ActivityCameraxBinding
import com.ma.camerabasic.utils.CameraConfig
import com.ma.camerabasic.utils.CameraUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class CameraXActivity : BaseActivity<ActivityCameraxBinding>() {

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var flashMode = FLASH_MODE_OFF
    private var preview: Preview? = null;
    private var imageCapture: ImageCapture? =null
    private var videoCapture: VideoCapture<Recorder>?=null
    private var currentRecording: Recording? = null
    private var camera: Camera? = null
    private var ratio = AspectRatio.RATIO_4_3
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoStatus = CameraConfig.VideoState.PREVIEW
    private var mode = CameraConfig.CameraMode.PHOTO
    private var type: Int = 0
    private var uri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCameraxBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            if(mode == CameraConfig.CameraMode.PHOTO) {
                takePicture()
            } else if (mode == CameraConfig.CameraMode.VIDEO){
                if(videoStatus == CameraConfig.VideoState.PREVIEW) {
                    startRecording()
                } else {
                    stopRecording()
                }
            }
        }
        mBinding.ivAlbum.setOnClickListener {
            if (uri != null) {
                CameraUtils.showResult(this@CameraXActivity, uri.toString(), type)
            } else {

            }
        }
        mBinding.ivSwitch.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            openCamera()
        }
        mBinding.tvFlash.setOnClickListener {
            flashMode = if (flashMode == FLASH_MODE_OFF) {
                if (mode == CameraConfig.CameraMode.PHOTO) {
                    mBinding.tvFlash.text = "自动"
                    setFlashDrawable(R.drawable.ic_flash_auto)
                    FLASH_MODE_AUTO
                } else {
                    mBinding.tvFlash.text = "常亮"
                    setFlashDrawable(R.drawable.ic_flash_light)
                    camera?.cameraControl?.enableTorch(true)
                    4
                }

            } else if (flashMode == FLASH_MODE_AUTO) {
                mBinding.tvFlash.text = "开启"
                setFlashDrawable(R.drawable.ic_flash_on)
                FLASH_MODE_ON
            } else if (flashMode == FLASH_MODE_ON) {
                mBinding.tvFlash.text = "常亮"
                setFlashDrawable(R.drawable.ic_flash_light)
                camera?.cameraControl?.enableTorch(true)
                -1
            } else {
                mBinding.tvFlash.text = "关闭"
                setFlashDrawable(R.drawable.ic_flash_off)
                camera?.cameraControl?.enableTorch(false)
                FLASH_MODE_OFF
            }
            if (flashMode != -1) {
                imageCapture?.flashMode = flashMode
            }
        }
        mBinding.tvRatio.setOnClickListener {
            ratio = if (ratio == AspectRatio.RATIO_4_3) {
                mBinding.tvRatio.text = "16:9"
                AspectRatio.RATIO_16_9
            } else if (ratio == AspectRatio.RATIO_16_9) {
                mBinding.tvRatio.text = "全屏"
                -1
            }else {
                mBinding.tvRatio.text = "4:3"
                AspectRatio.RATIO_4_3
            }
            bindPreview()
        }
        mBinding.tlMode.addOnTabSelectedListener(object: OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position
                if (position == 0) {
                    mode = CameraConfig.CameraMode.PHOTO
                    mBinding.ivCamera.setImageResource(R.drawable.ic_camera)



                } else {
                    mode = CameraConfig.CameraMode.VIDEO
                    mBinding.ivCamera.setImageResource(R.drawable.ic_video)


                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {

            }

        } )
        mBinding.cameraPreview.setOnTouchListener { _, motionEvent ->

            runBlocking {
                showFocus(motionEvent.x, motionEvent.y)
            }

            val meteringPoint = mBinding.cameraPreview.meteringPointFactory
                .createPoint(motionEvent.x, motionEvent.y)
            val action = FocusMeteringAction.Builder(meteringPoint)
                .addPoint(meteringPoint, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)
            false
        }
        checkPremission()
    }


    private fun checkPremission() {
        if ( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ||ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            premissionLuncher.launch(arrayOf( Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE))
        } else {
            openCamera()
        }
    }

    private val premissionLuncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        openCamera()
    }

    private fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            mBinding.cameraPreview.post {
                bindPreview()
            }
        }, ContextCompat.getMainExecutor(this))
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview() {
        val d=resources.displayMetrics

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val selector = selectExternalOrBestCamera(cameraProvider)

        if (ratio != -1 ) {
            preview = Preview.Builder()
                .setTargetRotation(mBinding.cameraPreview.display.rotation)
                .setTargetAspectRatio(ratio)
                .build()

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(mBinding.cameraPreview.display.rotation)
                .setTargetAspectRatio(ratio)
                .setFlashMode(flashMode)
                .build()
        } else {
            preview = Preview.Builder()
                .setTargetRotation(mBinding.cameraPreview.display.rotation)
                .setTargetResolution(Size(d.widthPixels,d.heightPixels))
                .build()

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(mBinding.cameraPreview.display.rotation)
                .setTargetResolution(Size(d.widthPixels,d.heightPixels))
                .setFlashMode(flashMode)
                .build()
        }


        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)


        val orientationEventListener = object : OrientationEventListener(this) {
            @SuppressLint("RestrictedApi")
            override fun onOrientationChanged(orientation : Int) {
                val rotation : Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
        orientationEventListener.enable()
        preview?.setSurfaceProvider(mBinding.cameraPreview.surfaceProvider)
        cameraProvider?.unbindAll()
        camera=cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageCapture,videoCapture, preview)

        Log.e(TAG, "bindPreview  ${mBinding.cameraPreview.width}  ${mBinding.cameraPreview.height}" )

    }


    private fun takePicture() {
        val currentDate = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Images.Media.DISPLAY_NAME,"camerax_jpg_${currentDate}")
        contentValue.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValue.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraBasic/CameraX")
        }
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
                    Log.e(TAG, "takePicture.onError: ${error.message}" )
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.e(TAG, "takePicture.onImageSaved: }" )
                    type = 0
                    uri = outputFileResults.savedUri
                    CameraUtils.showThumbnail(this@CameraXActivity, uri, mBinding.ivAlbum)

                }
            })
    }


    @SuppressLint("MissingPermission")
    private fun startRecording() {
        Log.e(TAG, "startRecording" )
        val currentDate = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Video.Media.DISPLAY_NAME,"camerax_video_${currentDate}")
        contentValue.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValue.put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/CameraBasic/CameraX")
        }
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

        mBinding.tvTips.visibility= View.VISIBLE
        mBinding.ivAlbum.visibility= View.GONE
        mBinding.ivSwitch.visibility= View.GONE
        mBinding.tlMode.visibility= View.GONE
        mBinding.llTop.visibility= View.GONE
        mBinding.ivCamera.setImageResource(R.drawable.ic_video_stop)

        videoStatus=CameraConfig.VideoState.RECORDING
    }


    private fun stopRecording() {
        Log.e(TAG, "stopRecording" )
        currentRecording?.stop()

        mBinding.tvTips.visibility= View.GONE
        mBinding.ivAlbum.visibility= View.VISIBLE
        mBinding.ivSwitch.visibility= View.VISIBLE
        mBinding.tlMode.visibility= View.VISIBLE
        mBinding.llTop.visibility= View.VISIBLE
        mBinding.ivCamera.setImageResource(R.drawable.ic_video)
        videoStatus=CameraConfig.VideoState.PREVIEW
    }


    private val captureListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Status -> {
                Log.e(TAG, "VideoRecordEvent.Status" )
            }
            is VideoRecordEvent.Start -> {
                videoStatus=CameraConfig.VideoState.RECORDING
                Log.e(TAG, "VideoRecordEvent.Start" )
            }
            is VideoRecordEvent.Finalize-> {
                videoStatus=CameraConfig.VideoState.PREVIEW
                Log.e(TAG, "VideoRecordEvent.Finalize" )
                type = 1
                uri = event.outputResults.outputUri
                CameraUtils.showThumbnail(this@CameraXActivity, uri, mBinding.ivAlbum)
            }
            is VideoRecordEvent.Pause -> {
                Log.e(TAG, "VideoRecordEvent.Pause" )
            }
            is VideoRecordEvent.Resume -> {
                Log.e(TAG, "VideoRecordEvent.Resume" )
            }
        }

    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun selectExternalOrBestCamera(provider: ProcessCameraProvider?):CameraSelector? {
        provider?:return null
        val cam2Infos = provider.availableCameraInfos.map {
            Camera2CameraInfo.from(it)
        }.sortedByDescending {
            it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        }
        Log.e(TAG, "initCamera:id num ${cam2Infos.size}" )
        cam2Infos.forEach {
            Log.e(TAG, "initCamera:id ${it.cameraId}" )
            Log.e(TAG, "initCamera:facing ${it.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)}" )
            Log.e(TAG, "initCamera:API ${it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}" )
            Log.e(TAG, "initCamera:zoom ${it.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)}" )
            Log.e(TAG, "initCamera:resolution ${it.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}" )
            Log.e(TAG, "initCamera:flash ${it.getCameraCharacteristic(CameraCharacteristics.FLASH_INFO_AVAILABLE)}" )
        }

        return when {
            cam2Infos.isNotEmpty() -> {
                CameraSelector.Builder()
                    .addCameraFilter {
                        it.filter { camInfo ->
                            val thisCamId = Camera2CameraInfo.from(camInfo).cameraId
                            thisCamId == cam2Infos[0].cameraId
                        }
                    }.build()
            }
            else -> null
        }
    }

    val job = lifecycleScope.launch {
        delay(3000)
        mBinding.ivFocus.visibility = View.INVISIBLE
    }

    private suspend fun showFocus(x: Float, y: Float) {
        job.cancel()
        mBinding.ivFocus.visibility = View.VISIBLE
        mBinding.ivFocus.x = x - mBinding.ivFocus.width / 2
        mBinding.ivFocus.y = mBinding.cameraPreview.top + y - mBinding.ivFocus.height / 2
        job.join()
    }

    private fun setFlashDrawable(@DrawableRes res: Int){
        val drawable = ContextCompat.getDrawable(this, res)
        drawable?.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
        mBinding.tvFlash.setCompoundDrawables(null,drawable,null,null)
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }
}
