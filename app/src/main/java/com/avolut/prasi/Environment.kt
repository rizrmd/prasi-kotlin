package com.avolut.prasi

import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.filter
import io.ktor.util.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.ServerSocket
import java.net.URI

object Environment {
  @Throws(IOException::class)
  private fun findRandomOpenPortOnAllLocalInterfaces(): Int {
    ServerSocket(0).use { socket ->
      return socket.getLocalPort()
    }
  }

  private val environment = applicationEngineEnvironment {
    connector {
      port = findRandomOpenPortOnAllLocalInterfaces()
    }

    val index = """
<!DOCTYPE html>
<html lang="en">

<head>
  <meta charset="UTF-8">
  <meta name="viewport"
    content="width=device-width, initial-scale=1.0, user-scalable=1.0, minimum-scale=1.0, maximum-scale=1.0">
  <link rel="stylesheet" href="/index.css">
  <link rel="stylesheet" crossorigin href="/main.css">
  
</head>

<body class="flex-col flex-1 w-full min-h-screen flex opacity-0">
  
  <div id="root"></div>
  <script>
    window._prasi = { 
      basepath: "/", 
      platform: "android",
      site_id: "${Bundle.version}",
    }
  </script>
  <script src="/main.js" type="module"></script>
</body>
</html>
      """.trimIndent()

    module {
      install(ContentNegotiation) {
        json()
      }

      val baseUri = Uri.parse(Bundle.baseUrl)
      val client = HttpClient()
      routing {
        route("{...}") {
          handle {
            val pathname = call.request.uri

            if (pathname.startsWith("/_prasi")) {
              var responded = false
              if (pathname == "/_prasi/route") {
                Bundle.respondFile(call, "route", "application/json")
                responded = true
              } else if (pathname.startsWith("/_prasi/compress")) {
                call.respondText(text = "{}", contentType = ContentType.parse("application/json"))
                responded = true
              } else if (pathname.startsWith("/_prasi/load.js")) {
                Bundle.respondFile(call, "load-js", "text/javascript")
                responded = true
              } else if (pathname.startsWith("/_prasi/code")) {
                val res =
                  Bundle.listIn(arrayOf("/code/site${pathname.replace("/_prasi/code", "")}"))
                if (res.size > 0) {
                  Bundle.respondItem(call, res[0])
                }
              } else if (pathname.startsWith("/_prasi/page/")) {
                val filename = "/pages/${pathname.replace("/_prasi/page/", "")}.json"
                Bundle.respondFile(
                  call, filename, "application/json"
                )
                responded = true
              } else if (pathname.startsWith("/_prasi/pages")) {
                val filename = "/pages/${pathname.replace("/_prasi/page/", "")}.json"
                Bundle.respondFile(
                  call, filename, "application/json"
                )
                responded = true
              } else if (pathname.startsWith("/_prasi/comp")) {
                val raw = call.receiveText()
                val posted = Json.decodeFromString<Posted>(raw)
                val posts = ArrayList<String>()
                posted.ids.forEach { e -> posts.add("/comps/${e}.json") }
                val res = Bundle.listIn(posts.toTypedArray())
                if (res.size > 0) {
                  val comps = buildJsonObject {
                    res.forEach { e ->
                      if (e.text != null) {
                        val json = Json.parseToJsonElement(e.text!!).jsonObject
                        val ctree = json.get("content_tree")
                        if (ctree != null) {
                          put(
                            e.path.replace("/comps/", "").replace(".json", ""), ctree
                          )
                        }
                      }
                    }
                  }
                  call.respondText(
                    text = comps.toString(), contentType = ContentType.parse("application/json")
                  )
                  responded = true
                } else {
                  call.respondText(
                    text = "{}", contentType = ContentType.parse("application/json")
                  )
                  responded = true
                }
              }

              if (!responded) {
                call.respondText(text = "", status = HttpStatusCode.NotFound)
              }
            } else {
              val res = Bundle.listIn(arrayOf("/code/core${pathname}", "/code/site${pathname}"))
              if (res.size > 0) {
                Bundle.respondItem(call, res[0])
              } else {
                if (pathname.startsWith("/favicon.")) call.respondText("")
                else if (pathname == "/" || pathname == "/index.html" || pathname == "/index.htm") {
                  call.respondText(
                    contentType = ContentType.parse("text/html"), text = index
                  )
                } else {
                  if (pathname.endsWith(".js") || pathname.endsWith(".css")) {
                    call.respondText(
                      contentType = ContentType.parse("text/javascript"),
                      text = "",
                      status = HttpStatusCode.NotFound
                    )
                  } else {

                    val uri = Uri.parse(call.request.uri)
                    val newUri = URI(
                      baseUri.scheme,
                      null,
                      baseUri.host,
                      baseUri.port,
                      uri.path,
                      uri.query,
                      uri.fragment
                    )
                    val response = client.request(newUri.toString()) {
                      method = call.request.httpMethod
                      val filtered = call.request.headers.filter { key, _ ->
                        !key.equals("User-Agent", ignoreCase = true) &&
                            !key.equals("Host", ignoreCase = true) && !key.equals(
                          "Origin",
                          ignoreCase = true
                        ) && !key.equals("Referer", ignoreCase = true) && !key.startsWith(
                          "Sec-",
                          ignoreCase = true
                        ) && !key.startsWith("X-", ignoreCase = true) && !key.startsWith(
                          "Accept-",
                          ignoreCase = true
                        )
                      }
                      headers.appendAll(filtered)

                      // Forward the body for POST, PUT, PATCH requests
                      if (listOf(
                          HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch
                        ).contains(call.request.httpMethod)
                      ) {
                        val requestBody = call.receiveChannel().toByteArray()
                        setBody(requestBody)
                      }
                    }

                    val responseBodyBytes = response.bodyAsChannel().toByteArray()

                    call.response.status(response.status)

                    // Forward headers
                    response.headers.forEach { key, values ->
                      values.forEach { value ->
                        call.response.headers.append(key, value)
                      }
                    }

                    // Respond with binary data
                    call.respondBytes(responseBodyBytes)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  fun getEnvironment() = environment
}

@Serializable
data class Posted(
  var ids: ArrayList<String> = arrayListOf(),
)