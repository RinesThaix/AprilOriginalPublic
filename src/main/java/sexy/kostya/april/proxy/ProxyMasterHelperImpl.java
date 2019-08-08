package sexy.kostya.april.proxy;

import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.Logger;
import sexy.kostya.april.AprilImpl;
import sexy.kostya.april.network.Connection;
import sexy.kostya.april.network.netty.NettyConnection;
import sexy.kostya.april.network.netty.server.NettyServer;
import sexy.kostya.april.proxy.inner.ProxyInnerClient;
import sexy.kostya.april.proxy.inner.ProxyInnerServer;
import sexy.kostya.april.proxy.outer.ProxyServer;
import sexy.kostya.april.util.Shutdowner;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Created by k.shandurenko on 04/04/2019
 */
public class ProxyMasterHelperImpl implements ProxyMasterHelper {

    private final AttributeKey<String> connectionNameAttributeKey = AttributeKey.newInstance("ConnectionName");
    private final AttributeKey<UUID> connectionUUIDAttributeKey = AttributeKey.newInstance("ConnectionUUID");

    @Override
    public void start() {
        Configurations configuration = new Configurations();
        try {
            File configFile = new File("config.properties");
            if (!configFile.exists()) {
                initializeDefaultConfig(configFile);
            }
            Configuration config = configuration.properties(configFile);
            String myAddress = config.getString("bind_address.inner");
            config.getList(String.class, "proxies_inner").forEach(proxy -> {
                if (proxy.equals(myAddress)) {
                    return;
                }
                String[] split = proxy.split(":");
                if (split.length != 2) {
                    throw new RuntimeException("Illegal proxy address in configuration file :: " + proxy);
                }
                int port;
                try {
                    port = Integer.parseInt(split[1]);
                } catch (NumberFormatException ex) {
                    throw new RuntimeException("Illegal proxy address in configuration file :: " + proxy, ex);
                }
                ProxyInnerClient client = new ProxyInnerClient(getLogger());
                getLogger().info("Trying to connect to the proxy " + proxy + "..");
                try {
                    client.connect(split[0], port).get();
                } catch (InterruptedException | ExecutionException e) {
                    client.disconnect();
                    getLogger().info("Denied that proxy");
                }
            });

            String[] split = myAddress.split(":");
            if (split.length != 2) {
                throw new RuntimeException("Illegal proxy inner server address in configuration file :: " + myAddress);
            }
            int port;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Illegal proxy inner server address in configuration file :: " + myAddress, ex);
            }
            NettyServer server = new ProxyInnerServer(getLogger());
            getLogger().info("Starting proxy inner server at " + myAddress + "..");
            try {
                server.start(split[0], port).get();
                Shutdowner.addHook(server::stop);
            } catch (InterruptedException | ExecutionException e) {
                server.stop();
                getLogger().error("Proxy inner server could not be started");
                return;
            }

            String myAddress2 = config.getString("bind_address.outer");
            split = myAddress2.split(":");
            if (split.length != 2) {
                throw new RuntimeException("Illegal proxy outer server address in configuration file :: " + myAddress2);
            }
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Illegal proxy outer server address in configuration file :: " + myAddress2, ex);
            }
            server = new ProxyServer(getLogger());
            getLogger().info("Starting proxy outer server at " + myAddress2 + "..");
            try {
                server.start(split[0], port).get();
                Shutdowner.addHook(server::stop);
            } catch (InterruptedException | ExecutionException e) {
                server.stop();
                getLogger().error("Proxy outer server could not be started");
                return;
            }
        } catch (ConfigurationException | IOException e) {
            throw new RuntimeException(e);
        }
        getLogger().info("Successfully started the proxy.");
    }

    @Override
    public void setConnectionUUID(Connection connection, UUID uuid) {
        ((NettyConnection) connection).getContext().channel().attr(this.connectionUUIDAttributeKey).set(uuid);
    }

    @Override
    public UUID getConnectionUUID(Connection connection) {
        NettyConnection casted = (NettyConnection) connection;
        Channel channel = casted.getContext().channel();
        if (!channel.hasAttr(this.connectionUUIDAttributeKey)) {
            return null;
        }
        return channel.attr(this.connectionUUIDAttributeKey).get();
    }

    @Override
    public void setConnectionName(Connection connection, String name) {
        ((NettyConnection) connection).getContext().channel().attr(this.connectionNameAttributeKey).set(name);
    }

    @Override
    public String getConnectionName(Connection connection) {
        NettyConnection casted = (NettyConnection) connection;
        Channel channel = casted.getContext().channel();
        if (!channel.hasAttr(this.connectionNameAttributeKey)) {
            return null;
        }
        return channel.attr(this.connectionNameAttributeKey).get();
    }

    @Override
    public Logger getLogger() {
        return AprilImpl.getLogger();
    }

    private void initializeDefaultConfig(File file) throws IOException {
        final List<String> lines = Lists.newArrayList(
                "bind_address.inner = localhost:25000",
                "bind_address.outer = localhost:26000",
                "proxies_inner = localhost:25000",
                "proxies_inner = localhost:25001",
                "proxies_inner = localhost:25002"
        );
        if (!file.createNewFile()) {
            throw new IOException("Could not create config.properties");
        }
        try (PrintWriter writer = new PrintWriter(file)) {
            lines.forEach(writer::println);
        }
    }

}
