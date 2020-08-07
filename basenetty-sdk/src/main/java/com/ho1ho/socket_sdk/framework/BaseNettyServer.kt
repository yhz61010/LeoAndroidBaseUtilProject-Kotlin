package com.ho1ho.socket_sdk.framework

import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.inter.ServerConnectListener
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
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
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.stream.ChunkedWriteHandler
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:34
 */
abstract class BaseNettyServer protected constructor(
    private val port: Int,
    val connectionListener: ServerConnectListener<BaseNettyServer>,
    internal var isWebSocket: Boolean = false,
    internal var webSocketPath: String = "/ws"
) : BaseNetty() {
    // InetSocketAddress(port).hostString, port, connectionListener, retryStrategy

    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
    }

    private val tag = javaClass.simpleName

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .handler(LoggingHandler(LogLevel.INFO))
        .option(ChannelOption.SO_BACKLOG, 1024)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_IN_MILLS)

    private lateinit var serverChannel: Channel
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultServerInboundHandler: BaseServerChannelInboundHandler<*>? = null
        protected set

    @Volatile
    internal var connectState: AtomicReference<ServerConnectStatus> = AtomicReference(ServerConnectStatus.UNINITIALIZED)

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
        if (connectState.get() == ServerConnectStatus.STARTED) {
            LLog.w(tag, "===== Already started or not initialized =====")
            return
        }
        try {
            serverChannel = bootstrap.bind(port).sync().channel()
            connectState.set(ServerConnectStatus.STARTED)
            LLog.i(tag, "===== Start successfully =====")
            connectionListener.onStarted(this)
            serverChannel.closeFuture().sync()
        } catch (e: RejectedExecutionException) {
            LLog.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
            LLog.e(tag, "Netty server had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(this, ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: Exception) {
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(this, ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR, e.message)
        }
    }

    /**
     * Stop and release server using **syncUninterruptibly** method.(Full release will cost almost 4s.) So you'd better NOT call this method in main thread.
     *
     * Once you call this method, you can not start server again simply by calling [startServer] because of the Server Netty object will be released.
     * If you want to start server again, you must recreate the Server Netty object.
     */
    fun stopServer(): Boolean {
        LLog.w(tag, "===== stopServer() current state=${connectState.get().name} =====")
        if (!::serverChannel.isInitialized || ServerConnectStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already release or not initialized")
            return false
        }
        connectState.set(ServerConnectStatus.UNINITIALIZED)

        LLog.w(tag, "Releasing default socket handler first...")
        defaultServerInboundHandler?.release()
        defaultServerInboundHandler = null
        channelInitializer = null

        serverChannel.run {
            LLog.w(tag, "Closing channel...")
            kotlin.runCatching {
                pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
//                closeFuture()
                close().syncUninterruptibly()
            }.onFailure { LLog.e(tag, "Close channel error.", it) }
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
        LLog.w(tag, "=====> Server released <=====")
        connectState.set(ServerConnectStatus.STOPPED)
        connectionListener.onStopped()
        return true
    }

    // ================================================

    private fun isValidExecuteCommandEnv(clientChannel: Channel, cmd: Any?): Boolean {
        if (cmd == null) {
            LLog.e(tag, "The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("Command must be either String or ByteArray.")
        }
        if (!clientChannel.isActive) {
            LLog.e(tag, "Client channel is not active. Can not send command.")
            return false
        }
        return true
    }

    /**
     * @param isPing Only works in WebSocket mode
     */
    private fun executeUnifiedCommand(clientChannel: Channel, cmd: Any?, showLog: Boolean, isPing: Boolean): Boolean {
        if (!isValidExecuteCommandEnv(clientChannel, cmd)) {
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
            if (isPing) clientChannel.writeAndFlush(PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd))
            else clientChannel.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
        } else {
            clientChannel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(clientChannel: Channel, cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(clientChannel, cmd, showLog, false)

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(clientChannel: Channel, cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(clientChannel, cmd, showLog, true)

    // ================================================
}