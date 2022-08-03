package com.ma.cameraxcustom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.ma.cameraxcustom.databinding.ActivityCamera2Binding
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class Camera2Activity : BaseActivity<ActivityCamera2Binding>() {

    private val cameraManager: CameraManager by lazy {
        val context = applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private val IMAGE_BUFFER_SIZE: Int = 3
    private var cameraId: String = "0"
    private var imageReader: ImageReader?=null
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private var mCamera: CameraDevice?=null
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var mSession: CameraCaptureSession?=null
    private var videoStatus: Boolean = false
    private var type=1


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
        }
        mBinding.ivCamera.setOnLongClickListener {
            if(!videoStatus) {
                Log.e(TAG, "initView: 视频开始" )
                type=2
                mBinding.tvTips.visibility= View.VISIBLE
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

                    }
                }

            }
            false
        }
        mBinding.ivAlbum.setOnClickListener {
            type=1
        }
        mBinding.ivSwitch.setOnClickListener {

        }
        mBinding.ivFlash.setOnClickListener {

        }


        //监听点击事件进行手动对焦
        mBinding.surfaceView.setOnTouchListener { _, motionEvent ->

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
        mBinding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceCreated" )
                initCamera()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.e(TAG, "surfaceChanged" )
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceDestroyed" )

            }

        })
    }

    @SuppressLint("MissingPermission")
    private fun initCamera() {
        enumerateCameras()
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId onOpened")
                mCamera=device
                openCamera()
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId onDisconnected")

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
            }
        }, imageReaderHandler)
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


    private fun openCamera() {
        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
        imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)

        val targets = listOf(mBinding.surfaceView.holder.surface, imageReader?.surface)

        mCamera?.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                mSession=session
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${mCamera?.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
            }
        }, cameraHandler)

        val captureRequest = mCamera?.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW)?.apply { addTarget(mBinding.surfaceView.holder.surface) }

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        mSession?.setRepeatingRequest(captureRequest!!.build(), null, cameraHandler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {

            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }



    @SuppressLint("InlinedApi")
    private fun enumerateCameras() {

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
            //Log.e(TAG, "enumerateCameras:摄像头位置 ${characteristics.get(CameraCharacteristics.LENS_FACING)}" )
            // LEGACY=2 < LIMITED=0 < FULL=1 < LEVEL_3=3       EXTERNAL=4
            // LEGACY（旧版）。这些设备通过 Camera API2 接口为应用提供功能，而且这些功能与通过 Camera API1 接口提供给应用的功能大致相同。旧版框架代码在概念上将 Camera API2 调用转换为 Camera API1 调用；旧版设备不支持 Camera API2 功能，例如每帧控件。
            // LIMITED（有限）。这些设备支持部分（但不是全部）Camera API2 功能，并且必须使用 Camera HAL 3.2 或更高版本。
            // FULL（全面）。这些设备支持 Camera API2 的所有主要功能，并且必须使用 Camera HAL 3.2 或更高版本以及 Android 5.0 或更高版本。
            // LEVEL_3（级别 3）：这些设备支持 YUV 重新处理和 RAW 图片捕获，以及其他输出流配置。
            // EXTERNAL（外部）：这些设备类似于 LIMITED 设备，但有一些例外情况；例如，某些传感器或镜头信息可能未被报告或具有较不稳定的帧速率。此级别用于外部相机（如 USB 网络摄像头）。
            //Log.e(TAG, "enumerateCameras:摄像头API的支持级别 ${characteristicscharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}" )
            Log.e(TAG, "enumerateCameras:摄像头成像区域的内存大小(最大分辨率) ${characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}" )
            Log.e(TAG, "enumerateCameras:摄像头是否支持闪光灯 ${characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)}" )
            //Log.e(TAG, "enumerateCameras:摄像头支持的功能 ${characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)}" )
        }
    }
}