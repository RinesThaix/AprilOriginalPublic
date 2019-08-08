package sexy.kostya.april.network.netty;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.PacketRegistry;

import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class ChannelInitializer<T extends Channel> extends io.netty.channel.ChannelInitializer<T> {

    private final PacketRegistry packetRegistry;
    private final Supplier<NettyPacketHandler> packetHandlerGenerator;
    private final Logger logger;

    public ChannelInitializer(PacketRegistry packetRegistry, Supplier<NettyPacketHandler> packetHandlerGenerator, Logger logger) {
        this.packetRegistry = packetRegistry;
        this.packetHandlerGenerator = packetHandlerGenerator;
        this.logger = logger;
    }

    @Override
    protected void initChannel(T ch) {
        ch.config().setAllocator(PooledByteBufAllocator.DEFAULT);

        ch.pipeline().addLast("encoder-decoder", new PacketCodec(this.packetRegistry));
        HandlerBoss boss = new HandlerBoss(this.packetHandlerGenerator.get(), this.logger);
        ch.pipeline().addLast("boss", boss);
        ch.attr(HandlerBoss.BOSS_KEY).set(boss);
    }

}