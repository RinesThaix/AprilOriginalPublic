package sexy.kostya.april.network.netty.client;

import io.netty.channel.ChannelHandlerContext;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.netty.NettyPacketHandler;
import sexy.kostya.april.network.packet.SPacketDisconnect;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class NettyClientPacketHandler extends NettyPacketHandler {

    private final NettyClient client;

    public NettyClientPacketHandler(NettyClient client) {
        this.client = client;
        this.clearHandlers();
    }

    private void handleDisconnect(SPacketDisconnect packet) {
        client.getLogger().info("Disconnected from server" + (packet.message == null ? "" : ". Message: " + packet.message));
        client.disconnect();
    }

    @Override
    public void clearHandlers() {
        super.clearHandlers();
        this.addHandler(SPacketDisconnect.class, this::handleDisconnect);
    }

    @Override
    public void packetSent(Packet packet) {
        if (this.client.packetSentListener != null) {
            this.client.packetSentListener.accept(packet);
        }
        super.packetSent(packet);
    }

    @Override
    public void handle(Packet packet) {
        if (this.client.packetReceivedListener != null) {
            this.client.packetReceivedListener.accept(packet);
        }
        packetReceived();
        if (this.client.onPacketPreReceived(packet)) {
            return;
        }
        super.handle(packet);
    }

    @Override
    public void onConnect(ChannelHandlerContext ctx) {
        setConnection(this.client.createConnection(ctx, this));
        this.client.onConnected();
    }

    @Override
    public void onDisconnect(ChannelHandlerContext ctx) {
        this.client.deleteConnection(ctx);
    }

}