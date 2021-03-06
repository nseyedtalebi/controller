package io.cresco.agent.controller.netdiscovery;

import com.google.gson.Gson;
import io.cresco.agent.controller.core.ControllerEngine;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

public class UDPDiscoveryStatic {
    //private static final Logger logger = LoggerFactory.getLogger(UDPDiscoveryStatic.class);

    private ControllerEngine controllerEngine;
    private PluginBuilder plugin;
    private CLogger logger;
    private DatagramSocket c;
    private Gson gson;
    private int discoveryPort;
    //private Timer timer;
    //private int discoveryTimeout;
    //private DiscoveryType disType;
    //private boolean timerActive = false;
    private List<MsgEvent> discoveredList;
    private DiscoveryCrypto discoveryCrypto;

    public UDPDiscoveryStatic(ControllerEngine controllerEngine) {
        //this.logger = new CLogger(UDPDiscoveryStatic.class, agentcontroller.getMsgOutQueue(), agentcontroller.getRegion(), agentcontroller.getAgent(), agentcontroller.getPluginID(), CLogger.Level.Info);
        //this.agentcontroller = agentcontroller;
        this.controllerEngine = controllerEngine;
        this.plugin = controllerEngine.getPluginBuilder();
        this.logger = plugin.getLogger(UDPDiscoveryStatic.class.getName(),CLogger.Level.Info);

        gson = new Gson();
        //this.discoveryTimeout = discoveryTimeout;
        //this.disType = disType;
        this.discoveryCrypto = new DiscoveryCrypto(controllerEngine);
        this.discoveryPort = plugin.getConfig().getIntegerParam("netdiscoveryport",32005);
    }

    public UDPDiscoveryStatic(ControllerEngine controllerEngine, int discoveryPort) {
        this.controllerEngine = controllerEngine;
        this.plugin = controllerEngine.getPluginBuilder();
        this.logger = plugin.getLogger(UDPDiscoveryStatic.class.getName(),CLogger.Level.Info);

        //this.logger = new CLogger(UDPDiscoveryStatic.class, agentcontroller.getMsgOutQueue(), agentcontroller.getRegion(), agentcontroller.getAgent(), agentcontroller.getPluginID(), CLogger.Level.Info);
        //this.agentcontroller = agentcontroller;
        gson = new Gson();
        //this.discoveryTimeout = discoveryTimeout;
        //this.disType = disType;
        this.discoveryCrypto = new DiscoveryCrypto(controllerEngine);
        this.discoveryPort = discoveryPort;
    }

    /*
    private class StopListenerTask extends TimerTask {
        public void run() {
            try {
                logger.trace("Closing Listener...");
                //user timer to close socket
                c.close();
                timer.cancel();
                timerActive = false;
            } catch (Exception ex) {
                logger.error("StopListenerTask {}", ex.getMessage());
            }
        }
    }
    */

    private boolean setCertTrust(String remoteAgentPath, String remoteCertString) {
        boolean isSet = false;
        try {
            Certificate[] certs = controllerEngine.getCertificateManager().getCertsfromJson(remoteCertString);
            controllerEngine.getCertificateManager().addCertificatesToTrustStore(remoteAgentPath,certs);
            isSet = true;

        } catch(Exception ex) {
            logger.error("configureCertTrust Error " + ex.getMessage());
        }
        return isSet;
    }

    private synchronized void processIncoming(DatagramPacket packet) {
        synchronized (packet) {
            String json = new String(packet.getData()).trim();
            logger.trace(json);
            try {
                MsgEvent me = gson.fromJson(json, MsgEvent.class);
                if (me != null) {

                    String remoteAddress = packet.getAddress().getHostAddress();
                    if (remoteAddress.contains("%")) {
                        String[] remoteScope = remoteAddress.split("%");
                        remoteAddress = remoteScope[0];
                    }
                    logger.trace("Processing packet for {} {}_{}", remoteAddress, me.getParam("src_region"), me.getParam("src_agent"));
                    me.setParam("dst_ip", remoteAddress);
                    me.setParam("dst_region", me.getParam("src_region"));
                    me.setParam("dst_agent", me.getParam("src_agent"));
                    me.setParam("validated_authenication",ValidatedAuthenication(me));
                    if(me.getParam("public_cert") != null) {
                        logger.trace("public_cert Exists");
                        String remoteAgentPath = me.getParam("src_region") + "_" + me.getParam("src_agent");
                        if(setCertTrust(remoteAgentPath,me.getParam("public_cert"))) {
                            logger.trace("Added Static discovered host to discoveredList.");
                            logger.trace("discoveredList contains " + discoveredList.size() + " items.");
                        } else {
                            logger.trace("Could not set Trust");
                        }
                    } else {
                        logger.trace("processIncoming() : no cert found");
                    }
                    discoveredList.add(me);

                    //sme.setParam("public_cert", agentcontroller.getCertificateManager().getJsonFromCerts(agentcontroller.getCertificateManager().getPublicCertificate()));


                }
            } catch (Exception ex) {
                logger.error("DiscoveryClientWorker in loop {}", ex.getMessage());
            }
        }
    }

    public List<MsgEvent> discover(DiscoveryType disType, int discoveryTimeout, String hostAddress) {
        return discover(disType,discoveryTimeout,hostAddress,false);
    }

    public List<MsgEvent> discover(DiscoveryType disType, int discoveryTimeout, String hostAddress, Boolean sendCert) {
        // Find the server using UDP broadcast
        logger.info("Discovery Static started : Enable Cert: " + sendCert);

            // Broadcast the message over all the network interfaces
                    try {
                        discoveredList = new ArrayList<>();

                         logger.trace("Trying address {}", hostAddress);

                        c = new DatagramSocket();

                        //timer = new Timer();
                        //timer.schedule(new StopListenerTask(), discoveryTimeout);
                        //timerActive = true;

                        MsgEvent sme = new MsgEvent(MsgEvent.Type.DISCOVER, this.plugin.getRegion(), this.plugin.getAgent(), this.plugin.getPluginID(), "Discovery request.");
                        sme.setParam("discover_ip", hostAddress);
                        sme.setParam("src_region", this.plugin.getRegion());
                        sme.setParam("src_agent", this.plugin.getAgent());
                        if(sendCert) {
                            sme.setParam("public_cert", controllerEngine.getCertificateManager().getJsonFromCerts(controllerEngine.getCertificateManager().getPublicCertificate()));
                        }
                        if (disType == DiscoveryType.AGENT || disType == DiscoveryType.REGION || disType == DiscoveryType.GLOBAL) {
                            logger.trace("Discovery Type = {}", disType.name());
                            sme.setParam("discovery_type", disType.name());
                        } else {
                            logger.trace("Discovery type unknown");
                            sme = null;
                        }
                        //set for static discovery
                        sme.setParam("discovery_static_agent","true");

                        //set crypto message for discovery
                        sme.setParam("discovery_validator",generateValidateMessage(sme));

                        if (sme != null) {
                            //logger.trace("Building sendPacket for {}", inAddr.toString());
                            String sendJson = gson.toJson(sme);
                            byte[] sendData = sendJson.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(hostAddress), discoveryPort);
                            synchronized (c) {
                                c.send(sendPacket);
                                logger.trace("Sent sendPacket to {}", hostAddress);
                            }
                            if (!c.isClosed()) {
                                try {
                                    byte[] recvBuf = new byte[15000];
                                    DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                                    synchronized (c) {
                                        c.setSoTimeout(discoveryTimeout);
                                        logger.trace("Static Discovery Timeout=" + c.getSoTimeout());
                                        c.receive(receivePacket);
                                        logger.trace("Received packet");
                                        /*
                                        if (timerActive) {
                                            logger.trace("Restarting listening timer");
                                            timer.schedule(new StopListenerTask(), discoveryTimeout);
                                        }*/
                                    }
                                    synchronized (receivePacket) {
                                        processIncoming(receivePacket);
                                        c.close();
                                    }
                                } catch (SocketException se) {
                                    // Eat the message, this is normal
                                } catch (Exception e) {
                                    logger.error("discovery {}", e.getMessage());
                                }
                            }
                        }
                    } catch (SocketException se) {
                        logger.error("getDiscoveryMap : SocketException {}", se.getMessage());
                    } catch (IOException ie) {
                        // Eat the exception, closing the port
                    } catch (Exception e) {
                        logger.error("getDiscoveryMap {}", e.getMessage());
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        logger.error(errors.toString());
                    }

        return discoveredList;
    }

    private String ValidatedAuthenication(MsgEvent rme) {
        String decryptedString = null;
        try {

            String discoverySecret = null;
            if (rme.getParam("discovery_type").equals(DiscoveryType.AGENT.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_agent");
            } else if (rme.getParam("discovery_type").equals(DiscoveryType.REGION.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_region");
            } else if (rme.getParam("discovery_type").equals(DiscoveryType.GLOBAL.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_global");
            }
            if(rme.getParam("validated_authenication") != null) {
                decryptedString = discoveryCrypto.decrypt(rme.getParam("validated_authenication"), discoverySecret);
            }
            else {
                logger.error("[validated_authenication] record not found!");
                logger.error(rme.getParams().toString());
            }

        }
        catch(Exception ex) {
            logger.error(ex.getMessage());
        }

        return decryptedString;
    }

    private String generateValidateMessage(MsgEvent sme) {
        String encryptedString = null;
        try {

            String discoverySecret = null;
            if (sme.getParam("discovery_type").equals(DiscoveryType.AGENT.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_agent");
            } else if (sme.getParam("discovery_type").equals(DiscoveryType.REGION.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_region");
            } else if (sme.getParam("discovery_type").equals(DiscoveryType.GLOBAL.name())) {
                discoverySecret = plugin.getConfig().getStringParam("discovery_secret_global");
            }

            String verifyMessage = "DISCOVERY_MESSAGE_VERIFIED";
            encryptedString = discoveryCrypto.encrypt(verifyMessage,discoverySecret);

        }
        catch(Exception ex) {
            logger.error(ex.getMessage());
        }

        return encryptedString;
    }
}
