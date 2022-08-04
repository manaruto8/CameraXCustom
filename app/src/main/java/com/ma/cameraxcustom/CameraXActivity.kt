package com.ma.cameraxcustom

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import com.ma.cameraxcustom.databinding.ActivityCameraxBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraXActivity : BaseActivity<ActivityCameraxBinding>() {

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var imageCapture: ImageCapture? =null
    private var videoCapture: VideoCapture<Recorder>?=null
    private var currentRecording: Recording? = null
    private var videoStatus: Boolean = false
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var type=1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCameraxBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }


    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            Log.e(TAG, "initView: 拍照" )
            type=1
            takePicture()
        }
        mBinding.ivCamera.setOnLongClickListener() {
            if(!videoStatus) {
                Log.e(TAG, "initView: 视频开始" )
                type=2
                mBinding.tvTips.visibility=View.VISIBLE
                startRecording()
            }
            true
        }
        mBinding.ivCamera.setOnTouchListener(){view,event->
            when(event.action){
                MotionEvent.ACTION_UP->{
                    if(videoStatus){
                        Log.e(TAG, "initView: 视频停止" )
                        mBinding.tvTips.visibility=View.GONE
                        currentRecording?.stop()
                    }
                }

            }
            false
        }
        mBinding.ivAlbum.setOnClickListener {
            type=1
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
        checkPremission()
    }

    /**
     * 检查权限
     */
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


    /**
     * 显示照片或视频
     */
    private fun showResult(uri: Uri?) {
        uri?:return
        Log.e(TAG, "showResult: $uri||| ${uri.path}" )
        val intent =  Intent(this, ShowResultActivity::class.java)
        intent.putExtra("type",type)
        intent.putExtra("uri",uri.toString())
        startActivity(intent)
    }

    /**
     * 拍照
     */
    private fun takePicture() {
        val currentDate = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Images.Media.DISPLAY_NAME,"camerax_${currentDate}")
        contentValue.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValue.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraX")
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
                    showResult(outputFileResults.savedUri)
                }
            })
    }

    /**
     * 打开相册
     */
    private fun openAlbum() {
        val intent = Intent(Intent.ACTION_PICK ,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

//        打开文件管理器筛选图片
//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//        intent.addCategory(Intent.CATEGORY_OPENABLE)
//        intent.type = "image/*"

        activityLuncher.launch(intent)
    }

    private val activityLuncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.data != null && it.resultCode == Activity.RESULT_OK) {
            showResult(it.data?.data)
        } else {

        }
    }

    /**
     * 视频
     */
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val currentDate = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Video.Media.DISPLAY_NAME,"camerax_video_${currentDate}")
        contentValue.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValue.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraX")
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
        Log.e(TAG, "initView: 录制开始" )
    }

    //视频事件回调
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
                showResult(event.outputResults.outputUri)
            }
            is VideoRecordEvent.Pause -> {
                Log.e(TAG, "VideoRecordEvent.Pause" )
            }
            is VideoRecordEvent.Resume -> {
                Log.e(TAG, "VideoRecordEvent.Resume" )
            }
        }

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
        //可以自己过滤摄像头替代cameraSelector
        val selector = selectExternalOrBestCamera(cameraProvider)

        //拍照设置  根据设置实际的图片分辨率是最接近的可用分辨率，优先等于或大于然后小于。
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(d.widthPixels,d.heightPixels))//设置相机分辨率
            //.setTargetAspectRatio() //设置相机宽高比  无法同时设置宽高比和分辨率。会抛出 IllegalArgumentException
            .setTargetRotation(mBinding.cameraPreview.display.rotation)
            .build()
        Log.e(TAG, "bindPreview ${d.widthPixels}---${d.heightPixels}" )

        //视频设置  如果list中所有请求分辨率都不受支持  会通过FallbackStrategy的设置选择最接近该分辨率的
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        //拍摄旋转角度监听
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
                videoCapture?.targetRotation=rotation
            }
        }
        orientationEventListener.enable()
        preview.setSurfaceProvider(mBinding.cameraPreview.surfaceProvider)
        cameraProvider?.unbindAll()
        camera=cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageCapture,videoCapture, preview)

        mBinding.ivSwitch.isEnabled = hasBackCamera() && hasFrontCamera()
    }

    /**
     * 获取摄像头信息
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun selectExternalOrBestCamera(provider: ProcessCameraProvider?):CameraSelector? {
        provider?:return null
        val cam2Infos = provider.availableCameraInfos.map {
            Camera2CameraInfo.from(it)
        }.sortedByDescending {
            // HARDWARE_LEVEL is Int type, with the order of:
            // LEGACY < LIMITED < FULL < LEVEL_3 < EXTERNAL   优先外置摄像头
            it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        }
        Log.e(TAG, "selectExternalOrBestCamera:手机有 ${cam2Infos.size}个摄像头" )
        cam2Infos.forEach {
            Log.e(TAG, "selectExternalOrBestCamera:摄像头ID为 ${it.cameraId}" )
            //Log.e(TAG, "selectExternalOrBestCamera:摄像头变焦范围 ${it.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)}" )
            //Log.e(TAG, "selectExternalOrBestCamera:摄像头最大数码变焦倍数 ${it.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}" )
            // FRONT=0 BACK=1
            Log.e(TAG, "selectExternalOrBestCamera:摄像头位置 ${lensFacing(it.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)!!)}" )
            // LEGACY=2 < LIMITED=0 < FULL=1 < LEVEL_3=3       EXTERNAL=4
            // LEGACY（旧版）。这些设备通过 Camera API2 接口为应用提供功能，而且这些功能与通过 Camera API1 接口提供给应用的功能大致相同。旧版框架代码在概念上将 Camera API2 调用转换为 Camera API1 调用；旧版设备不支持 Camera API2 功能，例如每帧控件。
            // LIMITED（有限）。这些设备支持部分（但不是全部）Camera API2 功能，并且必须使用 Camera HAL 3.2 或更高版本。
            // FULL（全面）。这些设备支持 Camera API2 的所有主要功能，并且必须使用 Camera HAL 3.2 或更高版本以及 Android 5.0 或更高版本。
            // LEVEL_3（级别 3）：这些设备支持 YUV 重新处理和 RAW 图片捕获，以及其他输出流配置。
            // EXTERNAL（外部）：这些设备类似于 LIMITED 设备，但有一些例外情况；例如，某些传感器或镜头信息可能未被报告或具有较不稳定的帧速率。此级别用于外部相机（如 USB 网络摄像头）。
            Log.e(TAG, "selectExternalOrBestCamera:摄像头API的支持级别 ${supportedHardwareLevel(it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!)}" )
            Log.e(TAG, "selectExternalOrBestCamera:摄像头成像区域的内存大小(最大分辨率) ${it.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}" )
            Log.e(TAG, "selectExternalOrBestCamera:摄像头是否支持闪光灯 ${it.getCameraCharacteristic(CameraCharacteristics.FLASH_INFO_AVAILABLE)}" )
            //Log.e(TAG, "selectExternalOrBestCamera:摄像头支持的功能 ${it.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)}" )
        }

        return when {
            cam2Infos.isNotEmpty() -> {
                CameraSelector.Builder()
                    .addCameraFilter {
                        it.filter { camInfo ->
                            // cam2Infos[0] is either EXTERNAL or best built-in camera
                            val thisCamId = Camera2CameraInfo.from(camInfo).cameraId
                            thisCamId == cam2Infos[0].cameraId
                        }
                    }.build()
            }
            else -> null
        }
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun lensFacing(value: Int) = when(value) {
        CameraCharacteristics.LENS_FACING_BACK -> "后置"
        CameraCharacteristics.LENS_FACING_FRONT -> "前置"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "额外"
        else -> "未知"
    }

    private fun supportedHardwareLevel(value: Int) = when(value) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY（旧版）"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED（有限）"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL（全面）"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3（级别 3）"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL（外部）"
        else -> "未知"
    }
}
