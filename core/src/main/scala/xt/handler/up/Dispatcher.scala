package xt.handler.up

import xt.Config
import xt.handler.Env
import xt.vc.env.PathInfo
import xt.vc.{Router, Env => CEnv}
import xt.vc.controller.MissingParam

import java.lang.reflect.{Method, InvocationTargetException}
import java.util.{Map => JMap, List => JList, LinkedHashMap}

import org.jboss.netty.channel._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.handler.codec.http._
import ChannelHandler.Sharable
import HttpResponseStatus._
import HttpVersion._

@Sharable
class Dispatcher extends SimpleChannelUpstreamHandler with ClosedClientSilencer {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val m = e.getMessage
    if (!m.isInstanceOf[Env]) {
      ctx.sendUpstream(e)
      return
    }

    val env        = m.asInstanceOf[Env]
    val request    = env("request").asInstanceOf[HttpRequest]
    val pathInfo   = env("pathInfo").asInstanceOf[PathInfo]
    val uriParams  = env("uriParams").asInstanceOf[CEnv.Params]
    val bodyParams = env("bodyParams").asInstanceOf[CEnv.Params]

    Router.matchRoute(request.getMethod, pathInfo) match {
      case Some((ka, pathParams)) =>
        env("pathParams") = pathParams
        dispatchWithFailsafe(ctx, ka, env)

      case None =>
        val response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND)
        response.setHeader("X-Sendfile", System.getProperty("user.dir") + "/public/404.html")
        env("response") = response
        ctx.getChannel.write(env)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    logger.error("Dispatcher", e.getCause)
    e.getChannel.close
  }

  //----------------------------------------------------------------------------

  private def dispatchWithFailsafe(ctx: ChannelHandlerContext, ka: Router.KA, env: Env) {
    try {
      val (klass, action) = ka
      val controller      = klass.newInstance
      controller(ctx, env)

      logger.debug(controller.request.getMethod + " " + controller.pathInfo.decoded)
      logger.debug(filterParams(controller.allParams).toString)

      // Begin timestamp
      val t1 = System.currentTimeMillis

      // Call before filters
      val passed = controller.beforeFilters.forall(filter => {
        val onlyActions = filter._2
        if (onlyActions.isEmpty) {
          val exceptActions = filter._3
          if (!exceptActions.contains(action)) {
            val method = filter._1
            method.invoke(controller).asInstanceOf[Boolean]
          }	else true
        } else {
          if (onlyActions.contains(action)) {
            val method = filter._1
            method.invoke(controller).asInstanceOf[Boolean]
          } else true
        }
      })

      // Call action
      if (passed) action.invoke(controller)

      // End timestamp
      val t2 = System.currentTimeMillis
      logger.debug((t2 - t1) + " [ms]")
    } catch {
      case e =>
        val response = new DefaultHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR)

        // MissingParam is a special case

        if (e.isInstanceOf[InvocationTargetException]) {
          val ite = e.asInstanceOf[InvocationTargetException]
          val c = ite.getCause
          if (c.isInstanceOf[MissingParam]) {
            response.setStatus(BAD_REQUEST)
            val mp  = c.asInstanceOf[MissingParam]
            val key = mp.key
            val cb  = ChannelBuffers.copiedBuffer("Missing Param: " + key, CharsetUtil.UTF_8)
            HttpHeaders.setContentLength(response, cb.readableBytes)
            response.setContent(cb)
          }
        }

        if (response.getStatus != BAD_REQUEST) {
          logger.error("Error on dispatching", e)
          response.setHeader("X-Sendfile", System.getProperty("user.dir") + "/public/500.html")
        }

        env("response") = response
        ctx.getChannel.write(env)
    }
  }

  // Same as Rails' config.filter_parameters
  private def filterParams(params: java.util.Map[String, java.util.List[String]]): java.util.Map[String, java.util.List[String]] = {
    val ret = new java.util.LinkedHashMap[String, java.util.List[String]]()
    ret.putAll(params)
    for (key <- Config.filterParams) {
      if (ret.containsKey(key)) ret.put(key, Router.toValues("[filtered]"))
    }
    ret
  }
}
