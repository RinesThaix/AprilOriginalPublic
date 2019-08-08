package sexy.kostya.april.network.netty;

import io.netty.channel.ChannelHandlerContext;
import sexy.kostya.april.network.PacketHandler;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public abstract class NettyPacketHandler extends PacketHandler {

    public abstract void onConnect(ChannelHandlerContext ctx);

    public abstract void onDisconnect(ChannelHandlerContext ctx);

    @Override
    public NettyConnection getConnection() {
        return (NettyConnection) super.getConnection();
    }

}