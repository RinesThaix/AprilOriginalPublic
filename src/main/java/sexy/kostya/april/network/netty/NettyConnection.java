package sexy.kostya.april.network.netty;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import sexy.kostya.april.network.Connection;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.callback.CallbackHandler;
import sexy.kostya.april.network.callback.CallbackPacket;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class NettyConnection implements Connection {

    private final CallbackHandler parent;
    private final ChannelHandlerContext context;
    private final NettyPacketHandler handler;

    public NettyConnection(CallbackHandler parent, ChannelHandlerContext context, NettyPacketHandler handler) {
        this.parent = parent;
        this.context = context;
        this.handler = handler;
    }

    public ChannelHandlerContext getContext() {
        return this.context;
    }

    @Override
    public NettyPacketHandler getHandler() {
        return this.handler;
    }

    @Override
    public void sendPacket(Packet packet) {
        this.handler.packetSent(packet);
        this.context.writeAndFlush(packet, context.voidPromise());
    }

    @Override
    public <T extends CallbackPacket> ListenableFuture<CallbackPacket> sendPacketWithCallback(T packet, long timeout, TimeUnit timeUnit) {
        SettableFuture<CallbackPacket> future = SettableFuture.create();
        this.parent.registerCallback(packet, future, timeUnit.toMillis(timeout));
        sendPacket(packet);
        return future;
    }

    @Override
    public void processDisconnection() {
        this.context.channel().close();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) this.context.channel().remoteAddress();
    }

}