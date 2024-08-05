package com.avolut.prasi

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*

object Server {
  private val instance =
    embeddedServer(factory = Netty, environment = Environment.getEnvironment())

  private const val URL = "https://prasi.avolut.com/prod-zip/53f19c29-a36b-48b1-b13a-25dcdaef8ea5"

  val port: Int
    get() = this.instance.environment.connectors[0].port;

  fun start(callback: (instance: NettyApplicationEngine) -> Unit) {
    instance.start(wait = false)
    callback(instance)
  }
}
