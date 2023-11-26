package com.3isk
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class 3isk: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(3isk())
    }
}