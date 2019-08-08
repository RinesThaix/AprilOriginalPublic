package sexy.kostya.april.proxy.outer;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.network.callback.CallbackPacket;
import sexy.kostya.april.network.netty.client.NettyClient;
import sexy.kostya.april.proxy.outer.handler.ClientHandler;
import sexy.kostya.april.proxy.outer.packet.*;
import sexy.kostya.april.proxy.serialization.ProxySerializable;
import sexy.kostya.april.proxy.serialization.ProxySerializableType;
import sexy.kostya.april.util.AprilFutures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static sexy.kostya.april.network.Connection.DEFAULT_TIMEOUT_UNIT;
import static sexy.kostya.april.network.Connection.DEFAULT_TIMEOUT_VALUE;

/**
 * Created by k.shandurenko on 26/03/2019
 */
public class ProxyClient extends NettyClient {

    public static ProxyClient INSTANCE;

    @Getter
    private volatile String name;

    private List<String> proxiesRemembered;

    private final List<String> producerNames = new ArrayList<>();

    public ProxyClient(Logger logger) {
        super(logger, ProxyPacketRegistry.INSTANCE);
        INSTANCE = this;
//        setPacketReceivedListener(packet -> System.out.println("RECEIVED " + packet));
//        setPacketSentListener(packet -> System.out.println("SENT " + packet));
    }

    public boolean chooseProxyServerAndConnect(List<String> addresses) {
        this.proxiesRemembered = addresses;
        for (String proxy : addresses) {
            String[] split = proxy.split(":");
            Preconditions.checkArgument(split.length == 2, "Illegal proxy address :: %s", proxy);
            int port;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Illegal proxy address :: " + proxy, ex);
            }
            getLogger().info("Trying to connect to the proxy " + proxy + "..");
            try {
                connect(split[0], port).get();
                return true;
            } catch (Exception e) {
                disconnect();
                getLogger().info("Denied that proxy");
            }
        }
        return false;
    }

    @Override
    public void onConnected() {
        getConnection().getHandler().setup(new ClientHandler());
        AprilFutures.addCallback(sendPacketWithCallback(new ProxyPacket1Connection()), result -> {
            ProxyPacket2Naming casted = (ProxyPacket2Naming) result;
            this.name = casted.getName();
            synchronized (this.producerNames) {
                this.producerNames.forEach(producerName -> sendPacket(new ProxyPacket5RegisterProducer(producerName)));
            }
        }, ex -> {
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            getLogger().warn("Could not retrieve it's name from the proxy server.");
            disconnect();
        });
    }

    @Override
    public void onDisconnected() {
        this.name = null;
    }

    public void sendObject(String target, ProxySerializableType serializationType, Object value) {
        sendObject(Collections.singletonList(target), serializationType, value);
    }

    public void sendObject(List<String> targets, ProxySerializableType serializationType, Object value) {
        ProxySerializable serializable = serializationType.create();
        serializable.set(value);
        sendPacket(new ProxyPacket3ContainerToProxy(targets, serializable));
    }

    public ListenableFuture sendObjectWithCallback(String target, ProxySerializableType serializationType, Object value) {
        return sendObjectWithCallback(target, serializationType, value, DEFAULT_TIMEOUT_VALUE, DEFAULT_TIMEOUT_UNIT);
    }

    public ListenableFuture sendObjectWithCallback(String target, ProxySerializableType serializationType, Object value, long timeout, TimeUnit timeUnit) {
        ProxySerializable serializable = serializationType.create();
        serializable.set(value);
        ListenableFuture<CallbackPacket> future = sendPacketWithCallback(
                new ProxyPacket3ContainerToProxy(Collections.singletonList(target), serializable),
                timeout,
                timeUnit
        );
        return Futures.transform(future, (Function<? super CallbackPacket, ?>) result -> {
            ProxyPacket4ContainerFromProxy casted = (ProxyPacket4ContainerFromProxy) result;
            return casted.getValue().get();
        });
    }

    public void registerAsProducer(String producerName) {
        if (this.name != null) {
            sendPacket(new ProxyPacket5RegisterProducer(producerName));
        }
        synchronized (this.producerNames) {
            this.producerNames.add(producerName);
        }
    }

    public void registerAsListener(String producerName, String listenerGroup) {

    }

    @Override
    protected Runnable reconnect() {
        if (this.proxiesRemembered != null) {
            return () -> chooseProxyServerAndConnect(this.proxiesRemembered);
        } else {
            return super.reconnect();
        }
    }

}
