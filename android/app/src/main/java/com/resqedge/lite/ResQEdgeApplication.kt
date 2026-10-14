package com.resqedge.lite

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class ResQEdgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
    }
}
