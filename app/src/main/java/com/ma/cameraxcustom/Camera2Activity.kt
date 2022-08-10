package com.ma.cameraxcustom

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.*
import android.net.Uri
import android.net.wifi.aware.Characteristics
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.ma.cameraxcustom.databinding.ActivityCamera2Binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.min


class Camera2Activity : BaseActivity<ActivityCamera2Binding>() {

    /** Maximum number of images that will be held in the reader's buffer */
    private val IMAGE_BUFFER_SIZE: Int = 3

    /** Maximum time allowed to wait for the result of an image capture */
    private val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

    /** Milliseconds used for UI animations */
    private val ANIMATION_FAST_MILLIS = 50L

    private val VIDEO_MIME_TYPE = "video/avc"
    private val AUDIO_MIME_TYPE = "audio/mp4a-latm"

    private val animationTask: Runnable by lazy {
        Runnable {
            mBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            mBinding.overlay.postDelayed({
                mBinding.overlay.background = null
            }, ANIMATION_FAST_MILLIS)
        }
    }

    private val FRONT_CAMERAID: String = "1"
    private val BACK_CAMERAID: String = "0"
    private val RECORDER_VIDEO_BITRATE: Int = 10_000_000


    private lateinit var cameraManager: CameraManager
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var imageReader: ImageReader
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private lateinit var mCamera: CameraDevice
    private lateinit var mSession: CameraCaptureSession
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private lateinit var  mediaSurface:Surface
    private lateinit var mediaRecorder:MediaRecorder
    private var flashMode = CameraMetadata.FLASH_MODE_OFF
    private var cameraId=BACK_CAMERAID
    private lateinit var captureRequest:CaptureRequest.Builder
    private lateinit var size:Size
    private var videoStatus: Boolean = false
    private var type=1
    private var mediaFile:File?=null
    private var relative:Int=0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCamera2Binding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }


    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            Log.e(TAG, "initView: 拍照" )
            type=1
            takePicture()
        }
        mBinding.ivCamera.setOnLongClickListener {
            if(!videoStatus) {
                Log.e(TAG, "initView: 视频开始" )
                type=2
                mBinding.tvTips.visibility= View.VISIBLE
                startRecording()
            }
            true
        }
        mBinding.ivCamera.setOnTouchListener { view, event->
            when(event.action){
                MotionEvent.ACTION_UP->{
                    if(videoStatus){
                        Log.e(TAG, "initView: 视频停止" )
                        mBinding.tvTips.visibility= View.GONE
                        videoStatus=false
                        mediaRecorder.stop()
                        releaseMediaRecorder()
                        startPreview()
                        showResult(Uri.fromFile(mediaFile))
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
            cameraId=if(cameraId==BACK_CAMERAID){
                FRONT_CAMERAID
            }else{
                BACK_CAMERAID
            }
            if (mCamera!=null){
                mCamera.close()
                initCamera()
            }
        }
        mBinding.ivFlash.setOnClickListener {
            flashMode = if(flashMode==CameraMetadata.FLASH_MODE_OFF){
                CameraMetadata.FLASH_MODE_TORCH
            }else{
                CameraMetadata.FLASH_MODE_OFF
            }
            captureRequest.set(
                CaptureRequest.FLASH_MODE,
                flashMode
            )
            mSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
        }


        //监听点击事件进行手动对焦
        mBinding.textureView.setOnTouchListener { _, motionEvent ->

//            val areaX = (motionEvent.x / mBinding.textureView.width * size.height)
//            val areaY = (motionEvent.y / mBinding.textureView.height * size.width)
//
//            // 创建Rect区域
//            val focusArea = Rect()
//            focusArea.left = (areaX.toInt() - 100).coerceAtLeast(0) // 取最大或最小值，避免范围溢出屏幕坐标
//            focusArea.top = (areaY.toInt() - 100).coerceAtLeast(0)
//            focusArea.right = (areaX.toInt() + 100).coerceAtMost(size.height)
//            focusArea.bottom = (areaY.toInt() + 100).coerceAtMost(size.width)
//            captureRequest.apply{
//                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(focusArea, 1000)))
//                set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(focusArea, 1000)))
//                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
//                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
//                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
//            }
//            mSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
            false
        }
        checkPremission()
    }

    /**
     * 检查权限
     */
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
//        mBinding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback{
//            override fun surfaceCreated(p0: SurfaceHolder) {
//                Log.e(TAG, "surfaceCreated" )
//                initCamera()
//            }
//
//            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
//                Log.e(TAG, "surfaceChanged" )
//            }
//
//            override fun surfaceDestroyed(p0: SurfaceHolder) {
//                Log.e(TAG, "surfaceDestroyed" )
//
//            }
//
//        })
        mBinding.textureView.surfaceTextureListener= object :SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                Log.e(TAG, "onSurfaceTextureAvailable" )
                initCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
                Log.e(TAG, "onSurfaceTextureSizeChanged" )
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                Log.e(TAG, "onSurfaceTextureDestroyed" )
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                //Log.e(TAG, "onSurfaceTextureUpdated" )
            }

        }
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
        lifecycleScope.launch(Dispatchers.IO) {
            val buffer = takePhoto().planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
            getOutputMediaFile()
            FileOutputStream(mediaFile).use { it.write(bytes) }
            val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            val exifOrientation = computeExifOrientation(relative, mirrored)
            val exif = ExifInterface(mediaFile!!.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            exif.saveAttributes()
            showResult(Uri.fromFile(mediaFile))
        }
    }

    private suspend fun takePhoto(): Image = suspendCoroutine { cont ->

        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) { }
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.e(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = mSession.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        mSession.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                //增加拍照闪烁效果
                //mBinding.textureView.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)

                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.e(TAG, "Capture result received: $resultTimestamp")
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {
                        val image = imageQueue.take()
                        // TODO(owahltinez): b/142011420
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp) continue
                        Log.e(TAG, "Matching image dequeued: ${image.timestamp}")
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }
                        cont.resume(image)
                    }
                }
            }
        }, cameraHandler)
    }

    /**
     * 打开相册
     */
    private fun openAlbum() {
        val intent = Intent(Intent.ACTION_PICK , MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
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
     * 录制视频
     */
    private fun startRecording()= lifecycleScope.launch(Dispatchers.Main) {

        val cameraConfig = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val mediaSize =cameraConfig
            .getOutputSizes(MediaRecorder::class.java)
            .maxByOrNull { it.height * it.width }!!
        val secondsPerFrame =
            cameraConfig.getOutputMinFrameDuration(MediaRecorder::class.java, mediaSize) / 1_000_000_000.0
        val mediaFps= if (secondsPerFrame > 0) (1.0 / secondsPerFrame).toInt() else 0
        cameraConfig.getOutputSizes(MediaRecorder::class.java).forEach { size ->
            val secondsFrame =
                cameraConfig.getOutputMinFrameDuration(MediaRecorder::class.java, size) / 1_000_000_000.0
            val fps= if (secondsFrame > 0) (1.0 / secondsPerFrame).toInt() else 0
            Log.e(TAG, "startRecording:支持的录制尺寸 ${size.width}* ${size.height}，帧率为 $fps Fps" )
        }
        mediaSurface= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MediaCodec.createPersistentInputSurface()
        } else {
            val mediaCodec=MediaCodec.createDecoderByType(VIDEO_MIME_TYPE)
            mediaCodec.createInputSurface()
        }
        Log.e(TAG, "startRecording:最佳录制尺寸 ${mediaSize.width}* ${mediaSize.height}，帧率为 $mediaFps Fps" )
        mediaRecorder=createRecorder(mediaSurface,mediaFps,mediaSize)
        val surface = Surface(mBinding.textureView.surfaceTexture)
        val targets = listOf(surface, mediaSurface)
        mSession = createCaptureSession(mCamera, targets, cameraHandler)

        val captureRequest = mCamera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        ).apply {
            addTarget(surface)
            addTarget(mediaSurface)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(mediaFps, mediaFps))
        }
        mSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
        mediaRecorder.start()
        videoStatus=true
    }

    private fun createRecorder(surface: Surface, mediaFps:Int, mediaSize:  Size) = MediaRecorder(this).apply {
        getOutputMediaFile()
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(mediaFile?.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        if (mediaFps > 0) setVideoFrameRate(mediaFps)
        setVideoSize(mediaSize.width, mediaSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOrientationHint(relative)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setInputSurface(surface)
        }else{
            setPreviewDisplay(surface)
        }
        prepare()
    }


    /**
     * 设置保存位置
     */
    private fun getOutputMediaFile(){
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Camera2"
        )
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.e(TAG, "failed to create directory")
                    return
                }
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        mediaFile = if(type==1) {
            File(
                mediaStorageDir.path + File.separator +
                        "camera2_${timeStamp}.jpg"
            )
        }else{
            File(
                mediaStorageDir.path + File.separator +
                        "camera2_video_${timeStamp}.mp4"
            )
        }
    }

    /**
     * 获取相机ID  也可在此设置需要的ID
     */
    @SuppressLint("InlinedApi")
    private fun initCamera() = lifecycleScope.launch(Dispatchers.Main){
        cameraManager=getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList.filter {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            capabilities?.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) ?: false
        }

        // Iterate over the list of cameras and return all the compatible ones
        cameraIds.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            Log.e(TAG, "enumerateCameras:摄像头ID为 $id" )
            //Log.e(TAG, "enumerateCameras:摄像头变焦范围 ${characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)}" )
            //Log.e(TAG, "enumerateCameras:摄像头最大数码变焦倍数 ${characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}" )
            // FRONT=0 BACK=1
            Log.e(TAG, "enumerateCameras:摄像头位置 ${lensFacing(characteristics.get(CameraCharacteristics.LENS_FACING)!!)}" )
            // LEGACY=2 < LIMITED=0 < FULL=1 < LEVEL_3=3       EXTERNAL=4
            // LEGACY（旧版）。这些设备通过 Camera API2 接口为应用提供功能，而且这些功能与通过 Camera API1 接口提供给应用的功能大致相同。旧版框架代码在概念上将 Camera API2 调用转换为 Camera API1 调用；旧版设备不支持 Camera API2 功能，例如每帧控件。
            // LIMITED（有限）。这些设备支持部分（但不是全部）Camera API2 功能，并且必须使用 Camera HAL 3.2 或更高版本。
            // FULL（全面）。这些设备支持 Camera API2 的所有主要功能，并且必须使用 Camera HAL 3.2 或更高版本以及 Android 5.0 或更高版本。
            // LEVEL_3（级别 3）：这些设备支持 YUV 重新处理和 RAW 图片捕获，以及其他输出流配置。
            // EXTERNAL（外部）：这些设备类似于 LIMITED 设备，但有一些例外情况；例如，某些传感器或镜头信息可能未被报告或具有较不稳定的帧速率。此级别用于外部相机（如 USB 网络摄像头）。
            Log.e(TAG, "enumerateCameras:摄像头API的支持级别 ${supportedHardwareLevel(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!)}" )
            Log.e(TAG, "enumerateCameras:摄像头成像区域的内存大小(最大分辨率) ${characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}" )
            Log.e(TAG, "enumerateCameras:摄像头是否支持闪光灯 ${characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)}" )
            //Log.e(TAG, "enumerateCameras:摄像头支持的功能 ${characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)}" )
        }
        characteristics=cameraManager.getCameraCharacteristics(cameraId)
        mCamera=openCamera(cameraManager,cameraId,cameraHandler)
        val orientationEventListener = object : OrientationEventListener(applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = when {
                    orientation <= 45 -> Surface.ROTATION_0
                    orientation <= 135 -> Surface.ROTATION_90
                    orientation <= 225 -> Surface.ROTATION_180
                    orientation <= 315 -> Surface.ROTATION_270
                    else -> Surface.ROTATION_0
                }
                relative = computeRelativeRotation(characteristics, rotation)
            }
        }
        orientationEventListener.enable()

        startPreview()
    }

    /**
     * 开始预览
     */
    @SuppressLint("MissingPermission")
    private fun startPreview()= lifecycleScope.launch(Dispatchers.Main){
        val cameraConfig = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        size=cameraConfig
            .getOutputSizes(ImageFormat.JPEG)
            .maxByOrNull { it.width *it.height }!!
        var diff= 1.0
        cameraConfig.getOutputSizes(SurfaceTexture::class.java).forEach{
            val previewRatio=it.height.toDouble() /it.width//获取到的宽高是反的
            val surfaceRatio= mBinding.textureView.width.toDouble()/mBinding.textureView.height
            val value=previewRatio-surfaceRatio
            Log.e(TAG, "startPreview:支持的预览尺寸 ${it.height}* ${it.width}" )
            if(value>0&&value<diff){
                diff=value
                size= Size(it.width ,it.height)
            }
        }
        cameraConfig.getOutputSizes(ImageFormat.JPEG).forEach{
            Log.e(TAG, "startPreview:支持的照片尺寸 ${it.height}* ${it.width}" )
        }
        Log.e(TAG, "startPreview:最佳照片尺寸 ${size.height}* ${size.width}" )
        mBinding.textureView.setTransform(computeTransformationMatrix(size))
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)
        //val targets = listOf(mBinding.surfaceView.holder.surface, imageReader.surface)
        val surface = Surface(mBinding.textureView.surfaceTexture)
        val targets = listOf(surface, imageReader.surface)
        mSession = createCaptureSession(mCamera, targets, cameraHandler)

//        TEMPLATE_PREVIEW：创建预览的请求
//        TEMPLATE_STILL_CAPTURE：创建一个适合于静态图像捕获的请求，图像质量优先于帧速率。
//        TEMPLATE_RECORD：创建视频录制的请求
//        TEMPLATE_VIDEO_SNAPSHOT：创建视视频录制时截屏的请求
//        TEMPLATE_ZERO_SHUTTER_LAG：创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量。
//        TEMPLATE_MANUAL：创建一个基本捕获请求，这种请求中所有的自动控制都是禁用的(自动曝光，自动白平衡、自动焦点)。
//        val captureRequest = mCamera.createCaptureRequest(
//            CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(mBinding.surfaceView.holder.surface) }
        captureRequest = mCamera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        ).apply {
            addTarget(surface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            set(
                CaptureRequest.FLASH_MODE,
                flashMode
            )
        }
        mSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }

    private fun computeTransformationMatrix(previewSize: Size): Matrix {//获取到的宽高是反的
        val matrix = Matrix()
        var sx =  mBinding.textureView.height.toFloat()*previewSize.height/previewSize.width/mBinding.textureView.width.toFloat()
        var sy =  mBinding.textureView.width.toFloat()*previewSize.width/previewSize.height/mBinding.textureView.height.toFloat()
        if(sx<1){
            sx=sx*1/sx
            sy=sy*1/sx
        }else if(sy<1){
            sx=sx*1/sy
            sy=sy*1/sy
        }
        matrix.setScale(
            sx,
            sy,
            mBinding.textureView.width / 2f,
            mBinding.textureView.height / 2f
        )
        Log.e(TAG, "computeTransformationMatrix:设置的放大比例sx= $sx sy= $sy" )
        return matrix
    }

    /**
     * 获取相机控制类
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }


    /**
     * 获取相机管理器
     */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.e(TAG, "Camera $cameraId onDisconnected")
                finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId onError: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
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

    private fun computeRelativeRotation(
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

        return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360
    }

    private fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = when {
        rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
        rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
        rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
        rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
        rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
        rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
        rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
        rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
        else -> ExifInterface.ORIENTATION_UNDEFINED
    }

    private fun releaseCamera() {
        try {
            mCamera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder.reset()
        mediaRecorder.release()
        mediaSurface.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }
}