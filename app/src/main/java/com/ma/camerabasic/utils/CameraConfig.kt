package com.ma.camerabasic.utils

import android.util.Size

class CameraConfig {

    enum class CameraMode(val num: Int){
        PHOTO(0),
        VIDEO(1),
    }


    enum class VideoState(val num: Int){
        PREVIEW(0),
        RECORDING(1),
    }


    enum class CameraRatio(val size: Size){
        SQUARE(Size(1,1)),
        RECTANGLE_4_3(Size(4,3)),
        RECTANGLE_16_9(Size(16,9)),
        FUll(Size(0,0)),
    }

}