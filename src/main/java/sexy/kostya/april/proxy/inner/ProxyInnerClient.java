package sexy.kostya.april.proxy.inner;

import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.netty.client.NettyClient;
import sexy.kostya.april.proxy.ProxyMaster;
import sexy.kostya.april.proxy.inner.handler.ProxyClientHandler;
import sexy.kostya.april.proxy.inner.packet.ProxyInnerPacket1Connection;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class ProxyInnerClient extends NettyClient {

    public ProxyInnerClient(Logger logger) {
        super(logger, ProxyInnerPacketRegistry.INSTANCE);
//        setPacketReceivedListener(packet -> System.out.println("RECEIVED " + packet));
//        setPacketSentListener(packet -> System.out.println("SENT " + packet));
    }

    @Override
    public void onConnected() {
        getConnection().getHandler().setup(new ProxyClientHandler());
        sendPacket(new ProxyInnerPacket1Connection(ProxyMaster.getInstance().getUniqueID()));
    }

    @Override
    public void onDisconnected() {
        ProxyMaster.getInstance().removeProxyConnection(getConnection());
    }

}
