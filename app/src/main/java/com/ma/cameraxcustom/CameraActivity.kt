package com.ma.cameraxcustom


import android.Manifest
import android.R.attr.x
import android.R.attr.y
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ma.cameraxcustom.databinding.ActivityCameraBinding
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class CameraActivity : BaseActivity<ActivityCameraBinding>(),Camera.PreviewCallback,Camera.PictureCallback,Camera.AutoFocusCallback {

    private var lensFacing: Int = Camera.CameraInfo.CAMERA_FACING_BACK
    private var mCamera: Camera? = null
    private var mParameters: Camera.Parameters? = null
    private lateinit var mSurfaceHolder: SurfaceHolder
    private var type=1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }

    override fun initView() {
        mBinding.ivCamera.setOnClickListener {
            Log.e(TAG, "initView: 拍照" )
            mCamera?.takePicture(null,null,this)
        }
        mBinding.ivCamera.setOnLongClickListener {
            Log.e(TAG, "initView: 视频" )

            true
        }
        mBinding.ivCamera.setOnTouchListener { view, event->
            when(event.action){
                MotionEvent.ACTION_UP->{
                    Log.e(TAG, "initView: 视频停止" )

                }

            }
            false
        }
        mBinding.ivAlbum.setOnClickListener {

        }
        mBinding.ivSwitch.setOnClickListener {
            releaseCamera()
            if(lensFacing==Camera.CameraInfo.CAMERA_FACING_BACK){
                lensFacing=Camera.CameraInfo.CAMERA_FACING_FRONT
            }else{
                lensFacing=Camera.CameraInfo.CAMERA_FACING_BACK
            }
            openCamera()
        }
        mBinding.ivFlash.setOnClickListener {

        }


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
            if (mParameters!!.maxNumMeteringAreas > 0) {
                meteringAreas.add(cameraArea)
                focusAreas.add(cameraArea)
            }
            mParameters?.focusMode = Camera.Parameters.FOCUS_MODE_AUTO // 设置对焦模式
            mParameters?.focusAreas = focusAreas // 设置对焦区域
            mParameters?.meteringAreas = meteringAreas // 设置测光区域

            try {
                mCamera?.cancelAutoFocus() // 每次对焦前，需要先取消对焦
                mCamera?.parameters = mParameters // 设置相机参数
                mCamera?.autoFocus(this) // 开启对焦
            } catch (e:Exception) {
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
        mSurfaceHolder.addCallback(object :SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceCreated" )
                openCamera()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.e(TAG, "surfaceChanged" )
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                Log.e(TAG, "surfaceDestroyed" )
                releaseCamera()
            }

        })
    }

    override fun onAutoFocus(p0: Boolean, p1: Camera?) {

    }


    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {

    }

    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        p1?.startPreview()
        val currentDate = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())
        val contentValue = ContentValues()
        contentValue.put(MediaStore.Images.Media.DISPLAY_NAME,"camera_${currentDate}")
        contentValue.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        contentValue.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraX")
        contentValue.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val uri = contentResolver.insert(collection, contentValue)

        try {
            uri?.let {
                val fos =contentResolver.openOutputStream(it)
                fos?.write(p0)
                fos?.close()
            }
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: ${e.message}")
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: ${e.message}")
        }
        type=1
        showResult(uri)
    }

    /**
     * 显示照片或视频
     */
    private fun showResult(uri: Uri?) {
        uri?:return
        val intent =  Intent(this, ShowResultActivity::class.java)
        intent.putExtra("type",type)
        intent.putExtra("uri",uri.toString())
        startActivity(intent)
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun openCamera() {
        if (!hasCamera(lensFacing)) return
        try {
            mCamera=Camera.open(lensFacing)
            mParameters= mCamera?.parameters
            mParameters?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            mCamera?.parameters=mParameters
            mCamera?.setPreviewCallback(this)
            mCamera?.setPreviewDisplay(mSurfaceHolder)
            setCameraDisplayOrientation()
            mCamera?.startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "打开相机失败${e.message}" )
        }
    }

    private fun setCameraDisplayOrientation(){
        var info = Camera.CameraInfo()
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
        mCamera?.setDisplayOrientation(result)
    }

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

    private fun releaseCamera() {
        mCamera?.setPreviewCallback(null);
        mCamera?.stopPreview();
        mCamera?.release()
        mCamera = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }
}