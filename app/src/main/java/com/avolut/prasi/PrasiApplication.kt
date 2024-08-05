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

  }

}
