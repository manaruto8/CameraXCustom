package com.ma.cameraxcustom

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.core.view.isGone
import com.ma.cameraxcustom.databinding.ActivityCameraxBinding
import com.ma.cameraxcustom.databinding.ActivityShowResultBinding

class ShowResultActivity : BaseActivity<ActivityShowResultBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityShowResultBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initView()
    }

    override fun initView() {
        val type=intent.getIntExtra("type",1)
        val uri=Uri.parse(intent.getStringExtra("uri"))
        if(type==1){
            mBinding.ivPhoto.isGone=false
            mBinding.videoView.isGone=true
            showPhoto(uri)
        }else{
            mBinding.ivPhoto.isGone=true
            mBinding.videoView.isGone=false
            showVideo(uri)
        }
        mBinding.tvAgain.setOnClickListener {
            finish()
        }
    }


    private fun showPhoto(uri:Uri){
        mBinding.ivPhoto.setImageURI(uri)
    }

    private fun showVideo(uri:Uri){
        val mc = MediaController(this)
        mBinding.videoView.apply {
            setVideoURI(uri)
            setMediaController(mc)
            requestFocus()
        }.start()
        mc.show(0)
    }
}