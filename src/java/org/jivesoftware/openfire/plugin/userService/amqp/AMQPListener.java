/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jivesoftware.openfire.plugin.userService.amqp;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.jivesoftware.openfire.plugin.userService.BackgroundThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMPQ listener.
 *
 * @author dusanklinec
 */
public class AMQPListener extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(AMQPListener.class);
    private static final String QUEUE_SERVER = "phone-x.net";
    private static final String QUEUE_VHOST = "/phonex";
    private static final String QUEUE_USER_NAME = "xmppServer";
    private static final String QUEUE_XMPP_NAME = "xmpp";

    /**
     * Timeous sync in milliseconds.
     */
    private static final long TIMEOUT_LISTEN = 1000 * 5;
    private long lastSync = 0;
    private volatile boolean running = true;
    private volatile boolean connectionOK = false;

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

        log.info(String.format("AMPQ starting: %s", this));
        this.start();
    }

    public synchronized void deinit() {
        log.info(String.format("Deinitializing AMQP listener, this=%s", this));
        setRunning(false);
    }

    private void ensureConnected() {
        boolean connected = false;
        try {
            connected = connection != null && connection.isOpen() && channel != null && channel.isOpen();
        } catch (Exception e) {
            log.error("Cannot determine if connected or not", e);
        }

        if (connected) {
            return;
        }

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_XMPP_NAME, true, false, false, null);
            connectionOK = true;
            log.info(String.format("AMQP connected, listener=%s, connection=%s, channel=%s", this, connection, channel));

        } catch (Exception e) {
            log.error("Cannot initialize messaging queue...");
            connectionOK = false;
        }
    }

    private void ensureClosed() {
        // Close channel.
        if (channel != null) {
            try {
                log.info(String.format("Going to close channel: %s", channel));

                channel.close();
                channel = null;
            } catch (Exception ex) {
                log.error("Could not close the channel", ex);
            }
        }

        // Close connection.
        if (connection != null) {
            try {
                log.info(String.format("Going to close connection: %s", connection));

                connection.close();
                connection = null;
            } catch (Exception ex) {
                log.error("Could not close the connection", ex);
            }
        }
    }

    public void doTheJob() {
        try {
            this.ensureConnected();

            final QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(QUEUE_XMPP_NAME, true, consumer);
            while (this.running) {
                final QueueingConsumer.Delivery delivery = consumer.nextDelivery(2000);
                if (delivery == null) {
                    continue;
                }

                processNewMessage(delivery);
            }
        } catch (Exception ex) {
            log.warn("Problem occurred during property sync", ex);
        } finally {
            ensureClosed();
        }
    }

    @Override
    public void run() {
        this.running = true;
        while (this.running) {
            doTheJob();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }

        ensureClosed();
        log.info(String.format("AMPQListener thread ended. Running: %s, this: %s", running, this));
    }

    private void processNewMessage(QueueingConsumer.Delivery delivery) {
        try {
            if (listener != null) {
                listener.acceptMessage(QUEUE_XMPP_NAME, delivery);
            }
        } catch (Exception ex) {
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
        log.info(String.format("Listener set to %s, listener %s", this, listener));
    }

}
