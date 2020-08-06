package com.ho1ho.socket_sdk.framework

import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.inter.ServerConnectListener
import com.ho1ho.socket_sdk.framework.retry_strategy.ConstantRetry
import com.ho1ho.socket_sdk.framework.retry_strategy.base.RetryStrategy
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.ConnectException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:34
 */
abstract class BaseNettyServer protected constructor(
    private val port: Int,
    val connectionListener: ServerConnectListener,
    private val retryStrategy: RetryStrategy = ConstantRetry(),
    internal var isWebSocket: Boolean = false,
    internal var webSocketPath: String = "/ws"
) : BaseNetty() {
    // InetSocketAddress(port).hostString, port, connectionListener, retryStrategy

    companion object {
        const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
    }

    private val tag = javaClass.simpleName

    // All client channels
    internal val clients: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .option(ChannelOption.SO_BACKLOG, 1024)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_IN_MILLS)

    private lateinit var channel: Channel
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultServerInboundHandler: BaseServerChannelInboundHandler<*>? = null
        protected set

    @Volatile
    var disconnectManually = false

    @Volatile
    internal var connectState: AtomicReference<ServerConnectStatus> = AtomicReference(ServerConnectStatus.UNINITIALIZED)
    private val retryThread = HandlerThread("retry-thread").apply { start() }
    private val retryHandler = Handler(retryThread.looper)
    var retryTimes = AtomicInteger(0)

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun initHandler(handler: BaseServerChannelInboundHandler<*>?) {
        defaultServerInboundHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    if (isWebSocket) {
//                        if ((webSocketPath?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
//                            LLog.w(tag, "Working in wss mode")
//                            val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
//                            // FIXME
////                        pipeline.addFirst(sslCtx.newHandler(serverSocketChannel.alloc(), host, port))
//                        }
                        addLast(HttpServerCodec())
                        addLast(HttpObjectAggregator(65536))
                        /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                         * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                        addLast(ChunkedWriteHandler())
                        // FIXME If add this, the server can not receive client message.
//                        addLast(WebSocketServerCompressionHandler())
                        addLast(WebSocketServerProtocolHandler(webSocketPath))
                    } else {
                        addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                        addLast(StringDecoder())
                        addLast(StringEncoder())
                    }
                    addLastToPipeline(this)
                    defaultServerInboundHandler?.let { addLast("default-server-inbound-handler", it) }
                }
            }
        }
        bootstrap.childHandler(channelInitializer)
    }

    /**
     * If netty client has already been release, call this method will throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
//    @Throws(RejectedExecutionException::class)
    @Synchronized
    fun startServer() {
        LLog.i(tag, "===== connect() current state=${connectState.get().name} =====")
        if (connectState.get() == ServerConnectStatus.CONNECTED) {
            LLog.w(tag, "===== Already connected =====")
            return
        } else {
            LLog.i(tag, "===== Prepare to connect to server =====")
        }
        try {
            // Want to use asynchronous way? Tell me how.
            val f = bootstrap.bind(port).sync()
            channel = f.syncUninterruptibly().channel()

            LLog.i(tag, "===== Connect success on ${channel.localAddress()} =====")
        } catch (e: RejectedExecutionException) {
            LLog.e(tag, "===== RejectedExecutionException: ${e.message} =====")
            LLog.e(tag, "Netty client had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onFailed(this, ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: ConnectException) {
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onFailed(this, ServerConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            doRetry()
        } catch (e: Exception) {
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onFailed(this, ServerConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, e.message)
            doRetry()
        }
    }

    /**
     * After calling this method, you can reuse it again by calling [connect].
     * If you don't want to reconnect it anymore, do not forget to call [release].
     *
     * **Remember**, If you call this method, it will not trigger retry process.
     */
    fun disconnectManually() {
        LLog.w(tag, "===== disconnect() current state=${connectState.get().name} =====")
        if (!::channel.isInitialized || ServerConnectStatus.DISCONNECTED == connectState.get() || ServerConnectStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already disconnected or not initialized.")
            return
        }
        disconnectManually = true
        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler if connection has been connected before.
        // However, if connection status is [CONNECTING], it ChannelHandler [channelInactive] will not be triggered.
        // So we just need to assign its status to [DISCONNECTED]. No need to call listener.
        connectState.set(ServerConnectStatus.DISCONNECTED)
        stopRetryHandler()
        channel.disconnect().syncUninterruptibly()
    }

    fun doRetry() {
        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LLog.e(tag, "===== Connect failed - Exceed max retry times. =====")
            stopRetryHandler()
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyServer,
                ServerConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LLog.w(tag, "Reconnect($retryTimes) in ${retryStrategy.getDelayInMillSec(retryTimes.get())}ms | current state=${connectState.get().name}")
            retryHandler.postDelayed({ startServer() }, retryStrategy.getDelayInMillSec(retryTimes.get()))
        }
    }

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2s.) So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect], you must create netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     */
    fun release() {
        LLog.w(tag, "===== release() current state=${connectState.get().name} =====")
        if (!::channel.isInitialized || ServerConnectStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already release or not initialized")
            return
        }
        disconnectManually = true
        LLog.w(tag, "Releasing retry handler...")
        stopRetryHandler()
        retryThread.quitSafely()

        LLog.w(tag, "Releasing default socket handler first...")
        defaultServerInboundHandler?.release()
        defaultServerInboundHandler = null
        channelInitializer = null

        channel.run {
            LLog.w(tag, "Closing channel...")
            pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
            closeFuture()
            close().syncUninterruptibly()
        }

        bossGroup.run {
            LLog.w(tag, "Releasing bossGroup...")
            shutdownGracefully().syncUninterruptibly() // Will not stuck here.
//            shutdownGracefully()
        }
        workerGroup.run {
            LLog.w(tag, "Releasing workerGroup...")
            shutdownGracefully().syncUninterruptibly() // Will not stuck here.
//            shutdownGracefully()
        }

        connectState.set(ServerConnectStatus.UNINITIALIZED)
        LLog.w(tag, "=====> Socket released <=====")
    }

    private fun stopRetryHandler() {
        retryHandler.removeCallbacksAndMessages(null)
        retryThread.interrupt()
        retryTimes.set(0)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(cmd: Any?): Boolean {
        if (!::channel.isInitialized) {
            LLog.e(tag, "Channel is not initialized. Stop processing.")
            return false
        }
        if (cmd == null) {
            LLog.e(tag, "The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("Command must be either String or ByteArray.")
        }
        if (ServerConnectStatus.CONNECTED != connectState.get()) {
            LLog.e(tag, "Socket is not connected. Can not send command.")
            return false
        }
        if (!channel.isActive) {
            LLog.e(tag, "Can not execute cmd because of Channel is not active.")
            return false
        }
        return true
    }

    /**
     * @param isPing Only works in WebSocket mode
     */
    private fun executeUnifiedCommand(cmd: Any?, showLog: Boolean, isPing: Boolean): Boolean {
        if (!isValidExecuteCommandEnv(cmd)) {
            return false
        }
        val stringCmd: String?
        val bytesCmd: ByteBuf?
        val isStringCmd: Boolean
        when (cmd) {
            is String -> {
                isStringCmd = true
                stringCmd = cmd
                bytesCmd = null
                if (showLog) LLog.i(tag, "exeCmd[${cmd.length}]=$cmd")
            }
            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) LLog.i(tag, "exeCmd HEX[${cmd.size}]=[${cmd.toHexStringLE()}]")
            }
            else -> throw IllegalArgumentException("Command must be either String or ByteArray")
        }

        if (isWebSocket) {
            if (isPing) channel.writeAndFlush(PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd))
            else channel.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
        } else {
            channel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, false)

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, true)

    // ================================================
}