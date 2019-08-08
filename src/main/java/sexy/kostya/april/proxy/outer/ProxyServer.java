package sexy.kostya.april.proxy.outer;

import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.netty.NettyConnection;
import sexy.kostya.april.network.netty.server.NettyServer;
import sexy.kostya.april.proxy.ProxyMaster;
import sexy.kostya.april.proxy.inner.packet.ProxyInnerPacket4OuterConnection;
import sexy.kostya.april.proxy.outer.handler.InitialServerHandler;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class ProxyServer extends NettyServer {

    public static boolean PRESENT;

    public static boolean isPresent() {
        return PRESENT;
    }

    public ProxyServer(Logger logger) {
        super(logger, ProxyPacketRegistry.INSTANCE);
        PRESENT = true;
//        setPacketReceivedListener((conn, packet) -> System.out.println("RECEIVED " + packet));
//        setPacketSentListener((conn, packet) -> System.out.println("SENT " + packet));
    }

    @Override
    public void onNewConnection(NettyConnection connection) {
        connection.getHandler().setup(new InitialServerHandler());
    }

    @Override
    public void onDisconnecting(NettyConnection connection) {
        ProxyMaster master = ProxyMaster.getInstance();
        master.removeDirectConnection(connection);
        master.broadcastToProxies(new ProxyInnerPacket4OuterConnection(master.getClientName(connection), false));
    }

}
