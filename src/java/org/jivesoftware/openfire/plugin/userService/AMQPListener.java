/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jivesoftware.openfire.plugin.userService;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMPQ listener.
 * @author dusanklinec
 */
public class AMQPListener extends BackgroundThreadService {
   private static final Logger log = LoggerFactory.getLogger(AMQPListener.class);
   private static final String QUEUE_SERVER     = "phone-x.net";
   private static final String QUEUE_VHOST      = "/phonex"; 
   private static final String QUEUE_USER_NAME  = "xmppServer";
   private static final String QUEUE_XMPP_NAME  = "xmpp"; 
   
   /**
    * Timeous sync in milliseconds.
    */
   private static final long TIMEOUT_LISTEN = 1000*5;
   private long lastSync = 0;
   private volatile boolean running=true;
   private volatile boolean connectionOK=false;
   
   private ConnectionFactory factory;
   private Connection connection;
   private Channel channel;
   private AMQPMsgListener listener;
    
   /**
    * Initializes internal running thread.
    */
   public synchronized void init() {
       initThread(this, "AMQPListener");
       
       factory = new ConnectionFactory();
       factory.setHost(QUEUE_SERVER);
       factory.setUsername(QUEUE_USER_NAME);
       factory.setPassword("chooshew5ot1aeMi");
       factory.setVirtualHost(QUEUE_VHOST);
       
       log.info("AMPQ connected & declared");
       this.start();
   }
   
   public synchronized void deinit(){
       log.info("listener deinitialization");
       setRunning(false);
   }
   
   private void ensureConnected(){
       boolean connected = false;
       try {
           connected = connection != null && connection.isOpen();
       } catch(Exception e){
           log.error("Cannot determine if connected or not", e);
       }
       
       if (connected){
           return;
       }
       
       try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_XMPP_NAME, true, false, false, null);
            connectionOK = true;
       } catch(Exception e){
           log.error("Cannot initialize messaging queue...");
           connectionOK = false;
       }    
   }

   public void doTheJob(){
       try {
           this.ensureConnected();
           
           final QueueingConsumer consumer = new QueueingConsumer(channel);
           channel.basicConsume(QUEUE_XMPP_NAME, true, consumer);
           while (this.running) {
              final QueueingConsumer.Delivery delivery = consumer.nextDelivery(2000);
              if (delivery == null){
                  continue;
              }
              
              processNewMessage(delivery);
           }
           
           // Close channel.
           if (channel != null){
               try {
                   channel.close();
               } catch(Exception ex){
                   log.error("Could not close the channel", ex);
               }
           }
           
           // Close connection.
           if (connection != null){
               try {
                   connection.close();
               } catch(Exception ex){
                   log.error("Could not close the connection", ex);
               }
           }
        } catch(Exception ex){
            log.warn("Problem occurred during property sync", ex);
        }
   }
   
    @Override
    public void run() {
        this.running = true;
        while (this.running) {
            long cmilli = System.currentTimeMillis();
            if ((cmilli-lastSync) > TIMEOUT_LISTEN){
                lastSync = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
        log.info("AMPQListener thread ended.");
    }

    private void processNewMessage(QueueingConsumer.Delivery delivery){
        try {
            if (listener != null){
                listener.acceptMessage(QUEUE_XMPP_NAME, delivery);
            }
        } catch(Exception ex){
            log.warn("Exception in processing a new message");
        }
    }
    
    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public AMQPMsgListener getListener() {
        return listener;
    }

    public void setListener(AMQPMsgListener listener) {
        this.listener = listener;
        log.info("Listener set");
    }
    
}
