package xitrum.controller

import java.net.{InetSocketAddress, SocketAddress}

import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST
import org.jboss.netty.handler.ssl.SslHandler

import xitrum.{Controller, Config}

// See:
//   http://httpd.apache.org/docs/2.2/mod/mod_proxy.html
//   http://en.wikipedia.org/wiki/X-Forwarded-For
//   https://github.com/playframework/play/blob/master/framework/src/play/mvc/Http.java
//   https://github.com/pepite/Play--Netty/blob/master/src/play/modules/netty/PlayHandler.java
//
// X-Forwarded-Host:   the original HOST header
// X-Forwarded-For:    comma separated, the first element is the origin
// X-Forwarded-Proto:  http/https
// X-Forwarded-Scheme: same as X-Forwarded-Proto
// X-Forwarded-Ssl:    on/off
//
// Note:
//   X-Forwarded-Server is the hostname of the proxy server, not the same as
//   X-Forwarded-Host.
object Net {
  // These are not put in trait Net so that they can be reused at AccessLog

  /** See reverseProxy in config/xitrum.json */
  def proxyNotAllowed(clientIp: String): Boolean = {
    Config.config.reverseProxy match {
      case None      => false
      case Some(reverseProxy) => reverseProxy.ips.contains(clientIp)
    }
  }

  /**
   * TODO: inetSocketAddress can be Inet4Address or Inet6Address
   * See java.net.preferIPv6Addresses
   * @return IP of the direct HTTP client (may be the proxy)
   */
  def clientIp(remoteAddress: SocketAddress): String = {
    val inetSocketAddress = remoteAddress.asInstanceOf[InetSocketAddress]
    inetSocketAddress.getAddress.getHostAddress
  }

  /** @return IP of the HTTP client, X-Forwarded-For is supported */
  def remoteIp(remoteAddress: SocketAddress, request: HttpRequest): String = {
    val ip = clientIp(remoteAddress)

    if (proxyNotAllowed(ip)) {
      ip
    } else {
      val xForwardedFor = request.getHeader("X-Forwarded-For")
      if (xForwardedFor == null)
        ip
      else
        xForwardedFor.split(",")(0).trim
    }
  }
}

trait Net {
  this: Controller =>

  // The "val"s must be "lazy", because when the controller is constructed, the
  // "request" object is null

  /** See reverseProxy in config/xitrum.json */
  private lazy val proxyNotAllowed = Net.proxyNotAllowed(clientIp)

  /**
   * TODO: inetSocketAddress can be Inet4Address or Inet6Address
   * See java.net.preferIPv6Addresses
   * @return IP of the direct HTTP client (may be the proxy)
   */
  private lazy val clientIp = Net.clientIp(channel.getRemoteAddress)

  /** @return IP of the original remote HTTP client (not the proxy), X-Forwarded-For is supported */
  lazy val remoteIp = Net.remoteIp(channel.getRemoteAddress, request)

  lazy val isSsl = {
    if (channel.getPipeline.get(classOf[SslHandler]) != null) {
      true
    } else {
      if (proxyNotAllowed) {
        false
      } else {
        val xForwardedProto = request.getHeader("X-Forwarded-Proto")
        if (xForwardedProto != null)
          (xForwardedProto == "https")
        else {
          val xForwardedScheme = request.getHeader("X-Forwarded-Scheme")
          if (xForwardedScheme != null)
            (xForwardedScheme == "https")
          else {
            val xForwardedSsl = request.getHeader("X-Forwarded-Ssl")
            if (xForwardedSsl != null)
              (xForwardedSsl == "on")
            else
              false
          }
        }
      }
    }
  }

  lazy val scheme          = if (isSsl) "https" else "http"
  lazy val webSocketScheme = if (isSsl) "wss"   else "ws"

  lazy val (serverName, serverPort) = {
    val np = request.getHeader(HOST)  // Ex: localhost, localhost:3000
    val xs = np.split(':')
    if (xs.length == 1) {
      val port = if (isSsl) 443 else 80
      (xs(0), port)
    } else {
      (xs(0), xs(1).toInt)
    }
  }

  lazy val absoluteUrlPrefix          = scheme          + "://" + absoluteUrlPrefixWithoutScheme
  lazy val webSocketAbsoluteUrlPrefix = webSocketScheme + "://" + absoluteUrlPrefixWithoutScheme

  lazy val absoluteUrlPrefixWithoutScheme = {
    val portSuffix =
      if ((isSsl && serverPort == 443) || (!isSsl && serverPort == 80))
        ""
      else
        ":" + serverPort
    serverName + portSuffix + Config.baseUrl
  }
}
