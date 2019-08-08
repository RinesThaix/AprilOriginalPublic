package sexy.kostya.april.network.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.Packet;
import sexy.kostya.april.network.exception.PacketHandleException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class HandlerBoss extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<HandlerBoss> BOSS_KEY = AttributeKey.newInstance("HandlerBoss");

    private NettyPacketHandler handler;
    private final Logger logger;

    public HandlerBoss(NettyPacketHandler initialHandler, Logger logger) {
        this.handler = initialHandler;
        this.logger = logger;
    }

    public Logger getLogger() {
        return logger;
    }

    public NettyPacketHandler getHandler() {
        return this.handler;
    }

    public void setHandler(NettyPacketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.handler.onConnect(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.handler.onDisconnect(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            this.handler.handle((Packet) msg);
        } catch (Exception ex) {
            throw new PacketHandleException(msg.getClass().getSimpleName(), ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (ctx.channel().isActive()) {
            if (cause instanceof TimeoutException) {
                this.logger.info(ctx.channel().remoteAddress() + " - read timed out");
            } else if (cause instanceof IOException) {
                this.logger.info(ctx.channel().remoteAddress() + " - IOException: " + cause.getMessage());
            } else if (cause instanceof DecoderException) {
                this.logger.warn(ctx.channel().remoteAddress() + " - Decoder exception: ", cause);
            } else if (cause instanceof PacketHandleException) {
                this.logger.warn(ctx.channel().remoteAddress() + " - Can not handle packet: " + cause.getMessage(), cause.getCause());
            } else {
                this.logger.error(ctx.channel().remoteAddress() + " - Encountered exception", cause);
            }
            ctx.close();
        }
    }
}