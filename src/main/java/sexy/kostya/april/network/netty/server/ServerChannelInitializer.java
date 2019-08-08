package sexy.kostya.april.network.netty.server;

import io.netty.channel.socket.SocketChannel;
import sexy.kostya.april.network.netty.ChannelInitializer;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    public ServerChannelInitializer(NettyServer server) {
        super(
                server.getPacketRegistry(),
                () -> new NettyServerPacketHandler(server),
                server.getLogger()
        );
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.config().setTcpNoDelay(false);
        super.initChannel(ch);
    }
}
