package com.avolut.prasi

import android.app.Application
import io.ktor.server.netty.NettyApplicationEngine


class PrasiApplication : Application() {
  var currentActivity: MainActivity? = null
  var server: NettyApplicationEngine? = null

  override fun onCreate() {
    super.onCreate()

    Bundle.init(applicationContext)
    Server.start { instance ->
      this.server = instance
      this.currentActivity?.showWebview()
    }

    val version = Bundle.version
    val site_id = Bundle.site_id
    val base_url = Bundle.base_url
    print(version)
  }

}
