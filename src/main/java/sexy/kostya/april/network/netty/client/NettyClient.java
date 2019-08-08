package sexy.kostya.april.network.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.NetworkClient;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.PacketRegistry;
import sexy.kostya.april.network.callback.CallbackHandler;
import sexy.kostya.april.network.netty.NettyConnection;
import sexy.kostya.april.network.netty.NettyPacketHandler;
import sexy.kostya.april.network.netty.NettyUtil;
import sexy.kostya.april.network.packet.SPacketHandshake;
import sexy.kostya.april.network.packet.SPacketKeepAlive;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public abstract class NettyClient extends CallbackHandler implements NetworkClient {

    private final PacketRegistry packetRegistry;

    private ScheduledFuture<?> callbackTickFuture = null;
    private NettyConnection connection;

    private ChannelFuture connectFuture;
    private ScheduledFuture<?> reconnectFuture;
    private ScheduledFuture<?> keepAliveFuture;
    private Bootstrap bootstrap;
    private volatile boolean shuttingDown;
    private boolean logReconnectMessage;
    Consumer<Packet> packetReceivedListener;
    Consumer<Packet> packetSentListener;

    private AtomicBoolean disconnected = new AtomicBoolean();

    public NettyClient(Logger logger, PacketRegistry packetRegistry) {
        super(logger);
        this.packetRegistry = packetRegistry;
    }

    @Override
    public Future<Void> connect(String address, int port) {
        this.shuttingDown = false;
        this.logReconnectMessage = true;
        if (this.bootstrap == null) {
            this.bootstrap = new Bootstrap()
                    .channel(NettyUtil.getChannel())
                    .group(NettyUtil.getWorkerLoopGroup())
                    .handler(new ClientChannelInitializer(this))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        }
        this.bootstrap.remoteAddress(address, port);
        return connect();
    }

    private ChannelFuture connect() {
        this.disconnected.set(false);
        if (this.connectFuture != null) {
            return this.connectFuture;
        }
        this.reconnectFuture = null;
        if (this.callbackTickFuture == null) {
            this.callbackTickFuture = NettyUtil.getWorkerLoopGroup().scheduleWithFixedDelay(this::callbackTick, 50L, 50L, TimeUnit.MILLISECONDS);
        }
        if (this.keepAliveFuture == null) {
            this.keepAliveFuture = NettyUtil.getWorkerLoopGroup().scheduleWithFixedDelay(() -> {
                try {
                    if (this.connection == null) {
                        return;
                    }
                    long current = System.currentTimeMillis();
                    NettyPacketHandler handler = this.connection.getHandler();
                    if (current - handler.getLastPacketReceivedTime() > 30000L) {
                        disconnect();
                        this.shuttingDown = false;
                        connect();
                        return;
                    }
                    if (current - handler.getLastPacketSentTime() > 20000L) {
                        sendPacket(new SPacketKeepAlive());
                    }
                } catch (Exception ex) {
                    logger.warn("Exception in keep-alive task", ex);
                }
            }, 1L, 1L, TimeUnit.SECONDS);
        }
        return this.connectFuture = this.bootstrap
                .connect()
                .addListener((ChannelFutureListener) future -> {
                    this.connectFuture = null;
                    if (future.isSuccess()) {
                        this.logger.info("Connected to the server");
                        this.logReconnectMessage = true;
                    } else {
                        if (this.logReconnectMessage) {
                            this.logger.warn("Could not connect to the server. I will connect as soon as the server is online..");
                            this.logReconnectMessage = false;
                        }
                        if (!(future.cause() instanceof CancellationException) && !this.disconnected.get() &&
                                (this.reconnectFuture == null || this.reconnectFuture.isDone())) {
                            Runnable reconnect = reconnect();
                            if (reconnect != null) {
                                this.reconnectFuture = future.channel().eventLoop().schedule(reconnect, 10L, TimeUnit.SECONDS);
                            }
                        }
                    }
                });
    }

    @Override
    public void disconnect() {
        this.disconnected.set(true);
        this.shuttingDown = true;
        if (this.connectFuture != null) {
            this.connectFuture.cancel(true);
            this.connectFuture = null;
        }
        if (this.reconnectFuture != null) {
            this.reconnectFuture.cancel(true);
            this.reconnectFuture = null;
        }
        if (this.callbackTickFuture != null) {
            this.callbackTickFuture.cancel(false);
            this.callbackTickFuture = null;
        }
        super.callCallbacksTimeouts();
        if (this.keepAliveFuture != null) {
            this.keepAliveFuture.cancel(false);
            this.keepAliveFuture = null;
        }
        if (this.connection != null) {
            this.connection.getContext().channel().close().syncUninterruptibly();
            this.connection = null;
        }
        this.logger.info("Disconnected from the server");
    }

    @Override
    public boolean isConnected() {
        return this.connection != null;
    }

    @Override
    public PacketRegistry getPacketRegistry() {
        return this.packetRegistry;
    }

    @Override
    public NettyConnection getConnection() {
        return this.connection;
    }

    @Override
    public void sendPacket(Packet packet) {
        if (this.connection == null) {
            return;
        }
        this.connection.sendPacket(packet);
    }

    @Override
    public void setPacketReceivedListener(Consumer<Packet> listener) {
        this.packetReceivedListener = listener;
    }

    @Override
    public void setPacketSentListener(Consumer<Packet> listener) {
        this.packetSentListener = listener;
    }

    public Logger getLogger() {
        return this.logger;
    }

    NettyConnection createConnection(ChannelHandlerContext ctx, NettyClientPacketHandler handler) {
        this.connection = new NettyConnection(this, ctx, handler);
        try {
            sendPacket(new SPacketHandshake(getPacketRegistry().getVersion()));
        } catch (Exception ex) {
            logger.warn("Can not process connection callback", ex);
        }
        return this.connection;
    }

    void deleteConnection(ChannelHandlerContext ctx) {
        this.connection.getHandler().setConnection(null);
        try {
            onDisconnected();
        } catch (Exception ex) {
            logger.warn("Can not process disconnection callback", ex);
        }
        this.connection = null;
        if (!this.shuttingDown) {
            connect();
        }
    }

    protected Runnable reconnect() {
        return this::connect;
    }

}