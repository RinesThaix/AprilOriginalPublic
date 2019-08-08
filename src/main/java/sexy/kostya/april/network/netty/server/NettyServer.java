package sexy.kostya.april.network.netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.Connection;
import sexy.kostya.april.network.NetworkServer;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.PacketRegistry;
import sexy.kostya.april.network.callback.CallbackHandler;
import sexy.kostya.april.network.netty.NettyConnection;
import sexy.kostya.april.network.netty.NettyPacketHandler;
import sexy.kostya.april.network.netty.NettyUtil;
import sexy.kostya.april.network.packet.SPacketKeepAlive;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public abstract class NettyServer extends CallbackHandler implements NetworkServer {

    private final PacketRegistry packetRegistry;

    private final Map<SocketAddress, NettyConnection> connections = new ConcurrentHashMap<>();

    private ScheduledFuture<?> callbackTickFuture = null;
    private ScheduledFuture<?> keepAliveFuture = null;

    BiConsumer<Connection, Packet> packetReceivedListener;
    BiConsumer<Connection, Packet> packetSentListener;

    public NettyServer(Logger logger, PacketRegistry packetRegistry) {
        super(logger);
        this.packetRegistry = packetRegistry;
    }

    private String address;
    private int port;

    private Channel channel;

    @Override
    public Future<Void> start(String address, int port) {
        this.address = address;
        this.port = port;
        ServerBootstrap b = new ServerBootstrap()
                .group(NettyUtil.getBossLoopGroup(), NettyUtil.getWorkerLoopGroup())
                .channel(NettyUtil.getServerChannel())
                .childHandler(new ServerChannelInitializer(this));
        ChannelFuture startingFuture = b.bind(address, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                this.channel = future.channel();
                this.logger.info("TCP listening on " + address + ":" + port);
            } else {
                this.logger.warn("Could not bind to host " + address + ":" + port, future.cause());
            }
        });
        if (this.callbackTickFuture == null) {
            this.callbackTickFuture = NettyUtil.getWorkerLoopGroup().scheduleWithFixedDelay(this::callbackTick, 50L, 50L, TimeUnit.MILLISECONDS);
        }
        if (this.keepAliveFuture == null) {
            this.keepAliveFuture = NettyUtil.getWorkerLoopGroup().scheduleWithFixedDelay(() -> {
                try {
                    long current = System.currentTimeMillis();
                    for (NettyConnection connection : this.connections.values()) {
                        NettyPacketHandler handler = connection.getHandler();
                        if (current - handler.getLastPacketReceivedTime() > 30000L) {
                            connection.disconnect("No keep alive signals for a long time");
                            continue;
                        }
                        if (current - handler.getLastPacketSentTime() > 20000L) {
                            connection.sendPacket(new SPacketKeepAlive());
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Exception in keep-alive task", ex);
                }
            }, 5L, 5L, TimeUnit.SECONDS);
        }
        return startingFuture;
    }

    @Override
    public void stop() {
        if (this.callbackTickFuture != null) {
            this.callbackTickFuture.cancel(false);
            this.callbackTickFuture = null;
        }
        if (this.keepAliveFuture != null) {
            this.keepAliveFuture.cancel(false);
            this.keepAliveFuture = null;
        }
        super.callCallbacksTimeouts();
        if (this.channel != null) {
            this.logger.info("Closing tcp listener");
            this.channel.close().syncUninterruptibly();
        }
    }

    @Override
    public String getAddress() {
        return this.address;
    }

    @Override
    public int getBindPort() {
        return this.port;
    }

    @Override
    public Collection<NettyConnection> getConnections() {
        return this.connections.values();
    }

    @Override
    public PacketRegistry getPacketRegistry() {
        return this.packetRegistry;
    }

    @Override
    public final void onNewConnection(Connection connection) {
        onNewConnection((NettyConnection) connection);
    }

    @Override
    public final void onDisconnecting(Connection connection) {
        onDisconnecting((NettyConnection) connection);
    }

    public abstract void onNewConnection(NettyConnection connection);

    public abstract void onDisconnecting(NettyConnection connection);

    @Override
    public void setPacketReceivedListener(BiConsumer<Connection, Packet> packetReceivedListener) {
        this.packetReceivedListener = packetReceivedListener;
    }

    @Override
    public void setPacketSentListener(BiConsumer<Connection, Packet> packetSentListener) {
        this.packetSentListener = packetSentListener;
    }

    public Logger getLogger() {
        return this.logger;
    }

    NettyConnection createNewConnection(ChannelHandlerContext ctx, NettyServerPacketHandler handler) {
        NettyConnection connection = new NettyConnection(this, ctx, handler);
        this.connections.put(ctx.channel().remoteAddress(), connection);
        return connection;
    }

    void deleteConnection(ChannelHandlerContext ctx) {
        NettyConnection connection = this.connections.remove(ctx.channel().remoteAddress());
        if (connection == null) {
            return;
        }
        try {
            connection.getHandler().setConnection(null);
            onDisconnecting(connection);
        } catch (Exception ex) {
            logger.warn("Can not process disconnection callback", ex);
        }
    }

}