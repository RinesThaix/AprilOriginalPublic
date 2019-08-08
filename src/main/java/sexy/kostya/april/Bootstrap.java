package sexy.kostya.april;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import sexy.kostya.april.proxy.ProxyMaster;
import sexy.kostya.april.proxy.ProxyMasterHelperImpl;
import sexy.kostya.april.proxy.outer.ProxyClient;
import sexy.kostya.april.util.Shutdowner;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Created by k.shandurenko on 25/03/2019
 */
public class Bootstrap {

    public static void main(String[] args) {
        runAsProxy();
    }

    public static void runAsClient() {
        Configurations configuration = new Configurations();
        try {
            Configuration config = configuration.properties(new File("config.properties"));
            List<String> proxies = config.getList(String.class, "proxies");
            runAsClient(proxies);
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void runAsClient(List<String> proxies) {
        Collections.shuffle(proxies);
        ProxyClient client = new ProxyClient(AprilImpl.getLogger());
        boolean connected = client.chooseProxyServerAndConnect(proxies);
        if (!connected) {
            AprilImpl.getLogger().error("Could not connect to any of specified proxies. Shutting down.");
            System.exit(18);
        }
        Shutdowner.addHook(client::disconnect);
        AprilImpl.getLogger().info("Successfully started the client and connected to the proxy-server.");
    }

    private static void runAsProxy() {
        ProxyMaster.getInstance().setHelper(new ProxyMasterHelperImpl());
        ProxyMaster.getInstance().start();
        while (true) {
            try {
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
