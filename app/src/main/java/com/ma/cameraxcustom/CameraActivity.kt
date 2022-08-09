package com.ma.cameraxcustom


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ma.cameraxcustom.databinding.ActivityCameraBinding
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class CameraActivity : BaseActivity<ActivityCameraBinding>(),Camera.PreviewCallback,Camera.PictureCallback,Camera.AutoFocusCallback {

    private var lensFacing: Int = Camera.CameraInfo.CAMERA_FACING_BACK
    private var flashMode: String =  Camera.Parameters.FLASH_MODE_OFF
    private var mSurfaceHolder: SurfaceHolder?=null
    private var mCamera: Camera?=null
    private var mParameters: Camera.Parameters?=null
    private var mediaRecorder:MediaRecorder?=null
    private var videoStatus: Boolean = false
    private var type=1
    private var mediaFile:File?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }

    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            Log.e(TAG, "initView: 拍照" )
            type=1
            mCamera?.takePicture(null,null,this)
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
                        mBinding.tvTips.visibility=View.GONE
                        mediaRecorder?.stop()
                        releaseMediaRecorder()
                        videoStatus=false
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


        //监听点击事件进行手动对焦
        mBinding.surfaceView.setOnTouchListener { _, motionEvent ->
            val areaX = (motionEvent.x / mBinding.surfaceView.width * 2000) - 1000 // 获取映射区域的X坐标
            val areaY = (motionEvent.y / mBinding.surfaceView.height * 2000) - 1000 // 获取映射区域的Y坐标

            // 创建Rect区域
            val focusArea = Rect()
            focusArea.left = (areaX.toInt() - 100).coerceAtLeast(-1000) // 取最大或最小值，避免范围溢出屏幕坐标
            focusArea.top = (areaY.toInt() - 100).coerceAtLeast(-1000)
            focusArea.right = (areaX.toInt() + 100).coerceAtMost(1000)
            focusArea.bottom = (areaY.toInt() + 100).coerceAtMost(1000)

            // 创建Camera.Area
            val cameraArea = Camera.Area(focusArea, 1000)
            val meteringAreas: MutableList<Camera.Area> = ArrayList()
            val focusAreas: MutableList<Camera.Area> = ArrayList()
            mParameters?.let {
                if (it.maxNumMeteringAreas > 0) {
                    meteringAreas.add(cameraArea)
                    focusAreas.add(cameraArea)
                }
                it.focusMode = Camera.Parameters.FOCUS_MODE_AUTO // 设置对焦模式
                it.focusAreas = focusAreas // 设置对焦区域
                it.meteringAreas = meteringAreas // 设置测光区域
            }
            mCamera?.let {
                try {
                    it.cancelAutoFocus() // 每次对焦前，需要先取消对焦
                    it.parameters = mParameters // 设置相机参数
                    it.autoFocus(this) // 开启对焦
                } catch (e:Exception) {
                }
            }
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
        mSurfaceHolder= mBinding.surfaceView.holder
        mSurfaceHolder?.addCallback(object :SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceCreated" )
                openCamera()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.e(TAG, "surfaceChanged" )
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
     * 拍照回调
     */
    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        p1?.startPreview()
        getOutputMediaFile()
        try {
            val fos = FileOutputStream(mediaFile)
            fos.write(p0)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: ${e.message}")
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: ${e.message}")
        }
        showResult(Uri.fromFile(mediaFile))
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
     * 录制视频
     */
    private fun startRecording() {

        getOutputMediaFile()
        mediaRecorder = MediaRecorder()
        mCamera?.let{
            it.unlock()
            mediaRecorder?.run {
                setCamera(mCamera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFile(mediaFile?.absolutePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                    setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))
                }else{
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                    setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
                }
                setOrientationHint(getCameraDisplayOrientation())
                setPreviewDisplay(mBinding.surfaceView.holder.surface)
                try {
                    prepare()
                    start()
                    videoStatus=true
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


    /**
     * 设置保存位置
     */
    private fun getOutputMediaFile(){
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Camera"
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
                        "camera_${timeStamp}.jpg"
            )
        }else{
            File(
                mediaStorageDir.path + File.separator +
                        "camera_video_${timeStamp}.mp4"
            )
        }
    }


    /**
     * 初始化相机
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun openCamera() {
        if (!hasCamera(lensFacing)) return
        try {
            mCamera=Camera.open(lensFacing)
            mParameters= mCamera?.parameters
            mParameters?.supportedPreviewSizes?.forEach {
                Log.e(TAG, "openCamera:支持的预览尺寸 ${it.height}* ${it.width}" )
            }
            mParameters?.supportedPictureSizes?.forEach {
                Log.e(TAG, "openCamera:支持的照片尺寸 ${it.height}* ${it.width}" )
            }
            mParameters?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            mParameters?.setRotation(getCameraDisplayOrientation())
//            闪光灯配置，Parameters.getFlashMode()。
//            Camera.Parameters.FLASH_MODE_AUTO 自动模式，当光线较暗时自动打开闪光灯；
//            Camera.Parameters.FLASH_MODE_OFF 关闭闪光灯；
//            Camera.Parameters.FLASH_MODE_ON 拍照时闪光灯；
//            Camera.Parameters.FLASH_MODE_RED_EYE 闪光灯参数，防红眼模式。
//            对焦模式配置，Parameters.getFocusMode()。
//            Camera.Parameters.FOCUS_MODE_AUTO 自动对焦模式；
//            Camera.Parameters.FOCUS_MODE_FIXED 固定焦距模式；
//            Camera.Parameters.FOCUS_MODE_EDOF 景深模式；
//            Camera.Parameters.FOCUS_MODE_INFINITY 远景模式；
//            Camera.Parameters.FOCUS_MODE_MACRO 微焦模式；
//            场景模式配置，Parameters.getSceneMode()。
//            Camera.Parameters.SCENE_MODE_BARCODE 扫描条码场景，NextQRCode项目会判断并设置为这个场景；
//            Camera.Parameters.SCENE_MODE_ACTION 动作场景，抓拍用的；
//            Camera.Parameters.SCENE_MODE_AUTO 自动选择场景；
//            Camera.Parameters.SCENE_MODE_HDR 高动态对比度场景，拍摄晚霞等明暗分明的照片；
//            Camera.Parameters.SCENE_MODE_NIGHT 夜间场景；
            mCamera?.parameters=mParameters
            mCamera?.setPreviewCallback(this)
            mCamera?.setPreviewDisplay(mSurfaceHolder)
            mCamera?.setDisplayOrientation(getCameraDisplayOrientation())
            mCamera?.startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "打开相机失败${e.message}" )
        }
    }

    /**
     * 获取相机ID  也可在此设置需要的ID
     */
    private fun hasCamera(lensFacing: Int): Boolean {
        var info = Camera.CameraInfo()
        var cameraNum=0
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            Log.e(TAG, "当前摄像头ID："+i+"---位置："+info.facing)
            if (info.facing ==lensFacing){
                cameraNum++
            }
        }
        return cameraNum>0
    }


    /**
     * 获取旋转角度
     */
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