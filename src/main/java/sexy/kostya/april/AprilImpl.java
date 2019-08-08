package sexy.kostya.april;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.proxy.outer.ProxyClient;
import sexy.kostya.april.proxy.outer.ProxyServer;
import sexy.kostya.april.proxy.serialization.ProxySerializableType;
import sexy.kostya.april.rpc.RpcTranslator;
import sexy.kostya.april.util.InternalUtils;
import sexy.kostya.april.util.InternalUtilsInterface;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by k.shandurenko on 04/04/2019
 */
@Log4j2(topic = "April")
public class AprilImpl implements April {

    static {
        InternalUtils.setInstance(new InternalUtilsInterface() {
            @Override
            public Logger getLogger() {
                return AprilImpl.getLogger();
            }

            @Override
            public void sendObject(List<String> targets, ProxySerializableType serializationType, Object value) {
                ProxyClient.INSTANCE.sendObject(targets, serializationType, value);
            }

            @Override
            public ListenableFuture sendObjectWithCallback(String target, ProxySerializableType serializationType, Object value, long timeout, TimeUnit timeUnit) {
                return ProxyClient.INSTANCE.sendObjectWithCallback(target, serializationType, value);
            }

            @Override
            public void registerAsProducer(String producerName) {
                ProxyClient.INSTANCE.registerAsProducer(producerName);
            }

            @Override
            public void registerAsListener(String producerName, String listenerGroup) {
                ProxyClient.INSTANCE.registerAsListener(producerName, listenerGroup);
            }

            @Override
            public boolean isProxyServerPresent() {
                return ProxyServer.isPresent();
            }
        });
    }

    @Override
    public void connect() {
        Bootstrap.runAsClient();
    }

    @Override
    public void connect(List<String> proxies) {
        Bootstrap.runAsClient(proxies);
    }

    @Override
    public boolean isConnected() {
        return ProxyClient.INSTANCE != null && ProxyClient.INSTANCE.isConnected();
    }

    @Override
    public String getInstanceName() {
        return ProxyClient.INSTANCE == null ? null : ProxyClient.INSTANCE.getName();
    }

    @Override
    public <T> T registerRpcRetriever(Class<T> retrieverClass) {
        return RpcTranslator.generate(retrieverClass);
    }

    @Override
    public <T> T registerRpcProducer(Class<T> producerClass) {
        return RpcTranslator.registerProducer(producerClass);
    }

    @Override
    public <T extends ConnectionListener> void registerConnectionListener(T instance) {
        RpcTranslator.registerConnectionListener(instance);
    }

    public static Logger getLogger() {
        return log;
    }

}
