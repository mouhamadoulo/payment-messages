package com.bank.paymentmessages.config;

import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IbmMqConfig {


    @Bean
    public ConnectionFactory connectionFactory() throws Exception {


        MQQueueConnectionFactory factory = new MQQueueConnectionFactory();

        factory.setHostName("localhost");
        factory.setPort(1414);
        factory.setQueueManager("QM1");
        factory.setChannel("DEV.APP.SVRCONN");

        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);

        return (ConnectionFactory) factory;
    }

}