package com.doodream.rmovjs.test;

import com.doodream.rmovjs.annotation.RMIException;
import com.doodream.rmovjs.client.RMIClient;
import com.doodream.rmovjs.model.RMIServiceInfo;
import com.doodream.rmovjs.net.ServiceProxy;
import com.doodream.rmovjs.sdp.SilentServiceAdvertiser;
import com.doodream.rmovjs.server.RMIService;
import com.doodream.rmovjs.test.service.echoback.EchoBackController;
import com.doodream.rmovjs.test.service.noreply.NoReplyService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ServiceNoReplyTest {

    private final static SilentServiceAdvertiser serviceAdvertiser = new SilentServiceAdvertiser();
    private static RMIService service;

    @BeforeClass
    public static void startServer() throws Exception {
        service = RMIService.create(NoReplyService.class, serviceAdvertiser);
        service.listen(false);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        service.stop();
    }

    @Test(expected = RMIException.class)
    public void testNoReply() throws Exception {
        Object client = buildNewClient();
        final EchoBackController controller = (EchoBackController) client;
        controller.sendUserList(new ArrayList<>());
        RMIClient.destroy(client);
    }

    private Object buildNewClient() {
        final ServiceProxy proxy = RMIServiceInfo.toServiceProxy(serviceAdvertiser.getServiceInfo());
        Assert.assertNotNull(proxy);

        return RMIClient.create(proxy, NoReplyService.class, new Class[]{
                EchoBackController.class,
        }, 1000L, TimeUnit.MILLISECONDS);
    }
}