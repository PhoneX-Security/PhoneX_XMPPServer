/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jivesoftware.openfire.plugin.userService.amqp;

import com.rabbitmq.client.QueueingConsumer;

/**
 *
 * @author dusanklinec
 */
public interface AMQPMsgListener {
    void acceptMessage(String queue, QueueingConsumer.Delivery delivery);
}
