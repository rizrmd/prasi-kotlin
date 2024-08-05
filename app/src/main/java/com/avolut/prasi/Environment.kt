package com.avolut.prasi

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

object Environment {
  private var clock: () -> Long = { getTimeMillis() }
  private val environment = applicationEngineEnvironment {
    connector {
      port = 17564
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
      routing {

        post("{...}") {

          val pathname = call.request.uri

          if (pathname.startsWith("/_prasi")) {
            if (pathname.startsWith("/_prasi/comp")) {
              val raw = call.receiveText()
              val posted = Json.decodeFromString<Posted>(raw)
              val posts = ArrayList<String>()
              posted.ids.forEach { e -> posts.add("/comps/${e}.json") }
              val res = Bundle.listIn(posts.toTypedArray())
              if (res.size > 0) {
                val comps = buildJsonObject {
                  res.forEach { e ->
                    if (e.text != null) {
                      val json = Json.parseToJsonElement(e.text!!).jsonObject;
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
              } else {
                call.respondText(
                  text = "{}", contentType = ContentType.parse("application/json")
                )
              }
            }
          }
        }
        get("{...}") {
          val pathname = call.request.uri

          if (pathname.startsWith("/_prasi")) {
            var found = false
            if (pathname == "/_prasi/route") {
              Bundle.respondFile(call, "route", "application/json")
              found = true
            } else if (pathname.startsWith("/_prasi/compress")) {
              call.respondText(text = "{}", contentType = ContentType.parse("application/json"))
              found = true
            } else if (pathname.startsWith("/_prasi/load.js")) {
              Bundle.respondFile(call, "load-js", "text/javascript")
              found = true
            } else if (pathname.startsWith("/_prasi/code")) {
              val res = Bundle.listIn(arrayOf("/code/site${pathname.replace("/_prasi/code", "")}"))
              if (res.size > 0) {
                Bundle.respondItem(call, res[0])
              }
            } else if (pathname.startsWith("/_prasi/page/")) {
              val filename = "/pages/${pathname.replace("/_prasi/page/", "")}.json"
              Bundle.respondFile(
                call, filename, "application/json"
              )
              found = true
            } else if (pathname.startsWith("/_prasi/pages")) {
              val filename = "/pages/${pathname.replace("/_prasi/page/", "")}.json"
              Bundle.respondFile(
                call, filename, "application/json"
              )
              found = true
            }

            if (!found) {
              call.respondText(text = "", status = HttpStatusCode.NotFound)
            }
          } else {
            val res = Bundle.listIn(arrayOf("/code/core${pathname}", "/code/site${pathname}"))
            if (res.size > 0) {
              Bundle.respondItem(call, res[0])
            } else {
              if (pathname == "/" || pathname == "/index.html" || pathname == "/index.htm") {
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
                  call.respondText(
                    contentType = ContentType.parse("text/html"),
                    text = index,
                    status = HttpStatusCode.NotFound
                  )
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