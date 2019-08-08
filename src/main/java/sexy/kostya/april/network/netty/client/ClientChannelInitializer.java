package sexy.kostya.april.network.netty.client;

import io.netty.channel.Channel;
import sexy.kostya.april.network.netty.ChannelInitializer;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class ClientChannelInitializer extends ChannelInitializer<Channel> {

    public ClientChannelInitializer(NettyClient client) {
        super(
                client.getPacketRegistry(),
                () -> new NettyClientPacketHandler(client),
                client.getLogger()
        );
    }

}