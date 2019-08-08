package sexy.kostya.april.network.netty.server;

import io.netty.channel.ChannelHandlerContext;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.netty.NettyPacketHandler;
import sexy.kostya.april.network.packet.SPacketHandshake;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class NettyServerPacketHandler extends NettyPacketHandler {

    private final NettyServer server;

    public NettyServerPacketHandler(NettyServer server) {
        this.server = server;
        this.addHandler(SPacketHandshake.class, this::handshake);
    }

    private void handshake(SPacketHandshake packet) {
        if (packet.version > this.server.getPacketRegistry().getVersion()) {
            this.connection.disconnect("Are you from future?");
        } else if (packet.version < this.server.getPacketRegistry().getVersion()) {
            this.connection.disconnect("Client protocol version is outdated");
        }
        this.removeHandler(SPacketHandshake.class, this::handshake);
        try {
            server.onNewConnection(connection);
        } catch (Exception ex) {
            server.getLogger().warn("Can not process connection callback", ex);
        }
    }

    @Override
    public void packetSent(Packet packet) {
        if (this.server.packetSentListener != null) {
            this.server.packetSentListener.accept(connection, packet);
        }
        super.packetSent(packet);
    }

    @Override
    public void handle(Packet packet) {
        if (this.server.packetReceivedListener != null) {
            this.server.packetReceivedListener.accept(connection, packet);
        }
        packetReceived();
        if (this.server.onPacketPreReceived(packet)) {
            return;
        }
        super.handle(packet);
    }

    @Override
    public void onConnect(ChannelHandlerContext ctx) {
        setConnection(this.server.createNewConnection(ctx, this));
    }

    @Override
    public void onDisconnect(ChannelHandlerContext ctx) {
        this.server.deleteConnection(ctx);
    }

}