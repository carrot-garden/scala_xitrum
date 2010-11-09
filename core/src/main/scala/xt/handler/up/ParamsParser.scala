package xt.handler.up

import xt.vc.Env

import java.nio.charset.Charset

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{QueryStringDecoder, HttpMethod}

/**
 * This middleware:
 * 1. Puts the request path to env.pathInfo
 * 2. Parses params in URI and request body and puts them to env.allParams
 */
class ParamsParser extends RequestHandler {
  def handleRequest(ctx: ChannelHandlerContext, env: Env) {
    import env._

    uri = request.getUri
    val query = if (request.getMethod != HttpMethod.POST) {
      uri
    } else {
      val c1 = request.getContent  // ChannelBuffer
      val c2 = c1.toString(Charset.forName("UTF-8"))
      val p  = if (uri.indexOf("?") == -1) "?" + c2 else "&" + c2
      uri + p
    }

    val d = new QueryStringDecoder(query)
    val p = d.getParameters

    // Because we will likely put things to params in later middlewares, we need
    // to avoid UnsupportedOperationException when p is empty. Whe p is empty,
    // it is a java.util.Collections$EmptyMap, which is immutable.
    //
    // See the source code of QueryStringDecoder as of Netty 3.2.3.Final
    val p2 = if (p.isEmpty) new java.util.LinkedHashMap[String, java.util.List[String]]() else p

    pathInfo = d.getPath
    allParams = p2
    Channels.fireMessageReceived(ctx, env)
  }
}
