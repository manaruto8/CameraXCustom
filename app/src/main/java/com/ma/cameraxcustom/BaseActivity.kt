package com.ma.cameraxcustom

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding

open class BaseActivity<VB: ViewBinding> : AppCompatActivity() {

    lateinit var mBinding: VB
    lateinit var TAG:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)
        TAG=javaClass.simpleName
    }

    open fun initView() {
        TODO("Not yet implemented")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) {
            window.decorView.systemUiVisibility = (
                    //低调模式  图标变暗或消失
                    View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    //清除沉浸标记  状态栏与导航栏出现后不再隐藏
                    //View.SYSTEM_UI_FLAG_IMMERSIVE or
                    //短暂清除沉浸标记  状态栏与导航栏出现后一定时间自动隐藏
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    //保证View布局整体不变动
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    //隐藏状态栏
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    //不隐藏状态栏 布局延伸至状态栏
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    //隐藏导航栏
                    //View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    //不隐藏导航栏 布局延伸至导航栏
                    //View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
    }
}