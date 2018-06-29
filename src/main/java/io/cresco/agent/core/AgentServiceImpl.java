package io.cresco.agent.core;


import io.cresco.agent.controller.core.ControllerEngine;
import io.cresco.library.agent.AgentService;
import io.cresco.library.agent.AgentState;
import io.cresco.library.agent.ControllerState;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component(
        service = { AgentService.class} ,
        immediate = true,
        //reference=@Reference(name="ConfigurationAdmin", service=ConfigurationAdmin.class)
        reference={ @Reference(name="ConfigurationAdmin", service=ConfigurationAdmin.class) , @Reference(name="LogService", service=LogService.class) }



)
public class AgentServiceImpl implements AgentService {

    private ControllerEngine controllerEngine;
    private ControllerState controllerState;
    private AgentState agentState;
    private PluginBuilder plugin;
    private PluginAdmin pluginAdmin;

    public AgentServiceImpl() {

    }

    @Activate
    void activate(BundleContext context) {

        this.controllerState = new ControllerState();
        this.pluginAdmin = new PluginAdmin(context);

        agentState = new AgentState(controllerState);
        agentState.setId("0");


        try {
            ServiceReference ref = context.getServiceReference(LogReaderService.class.getName());
            if (ref != null)
            {
                LogReaderService reader = (LogReaderService) context.getService(ref);
                reader.addLogListener(new LogWriter());
            }


            File configFile  = new File("conf/agent.properties");
            if(configFile.isFile()) {

                //Agent Config
                Config config = config = new Config(configFile.getAbsolutePath());
                Map<String,Object> map = config.getConfigMap();

                plugin = new PluginBuilder(this, this.getClass().getName(), context, map);

                controllerEngine = new ControllerEngine(controllerState, plugin, pluginAdmin);

                pluginAdmin.addBundle();
                pluginAdmin.addConfig("plugin/0");
                pluginAdmin.startPlugin("plugin/0");

            } else {
                System.out.println("NO CONFIG FILE!!");
            }

            MessageSender messageSender = new MessageSender(plugin);
            new Thread(messageSender).start();




        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public AgentState getAgentState() {
        return agentState;
    }


    @Override
    public void msgOut(String id, MsgEvent msg) {
        controllerEngine.msgIn(msg);
    }

}