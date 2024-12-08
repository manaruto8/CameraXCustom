package com.ma.camerabasic

import android.content.Intent
import android.os.Bundle
import com.ma.camerabasic.databinding.ActivitySelectCameraBinding

class SelectCameraActivity : BaseActivity<ActivitySelectCameraBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivitySelectCameraBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }

    override fun initView() {
        mBinding.cameraxTv.setOnClickListener {
            startActivity(Intent(this,CameraXActivity::class.java))
        }
        mBinding.camera2Tv.setOnClickListener {
            startActivity(Intent(this,Camera2Activity::class.java))
        }
        mBinding.cameraTv.setOnClickListener {
            startActivity(Intent(this,CameraActivity::class.java))
        }
    }
}