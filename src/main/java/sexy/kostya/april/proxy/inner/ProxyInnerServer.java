package sexy.kostya.april.proxy.inner;

import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.netty.NettyConnection;
import sexy.kostya.april.network.netty.server.NettyServer;
import sexy.kostya.april.proxy.ProxyMaster;
import sexy.kostya.april.proxy.inner.handler.ProxyServerHandler;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class ProxyInnerServer extends NettyServer {

    public ProxyInnerServer(Logger logger) {
        super(logger, ProxyInnerPacketRegistry.INSTANCE);
//        setPacketReceivedListener((conn, packet) -> System.out.println("RECEIVED " + packet));
//        setPacketSentListener((conn, packet) -> System.out.println("SENT " + packet));
    }

    @Override
    public void onNewConnection(NettyConnection connection) {
        connection.getHandler().setup(new ProxyServerHandler());
    }

    @Override
    public void onDisconnecting(NettyConnection connection) {
        ProxyMaster.getInstance().removeProxyConnection(connection);
    }

}
