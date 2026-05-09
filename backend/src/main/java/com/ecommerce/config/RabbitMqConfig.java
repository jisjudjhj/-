package com.ecommerce.config;

import com.ecommerce.mq.RabbitMqNames;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public Declarables ecommerceRabbitDeclarables(@org.springframework.beans.factory.annotation.Value("${order.timeout-minutes:30}")
                                                  int timeoutMinutes) {
        TopicExchange eventExchange = new TopicExchange(RabbitMqNames.EVENT_EXCHANGE, true, false);
        DirectExchange delayExchange = new DirectExchange(RabbitMqNames.DELAY_EXCHANGE, true, false);
        DirectExchange dlxExchange = new DirectExchange(RabbitMqNames.DLX_EXCHANGE, true, false);

        Queue notificationQueue = buildBusinessQueue(
                RabbitMqNames.NOTIFICATION_QUEUE,
                RabbitMqNames.NOTIFICATION_DLQ_ROUTING_KEY
        );
        Queue notificationDlq = QueueBuilder.durable(RabbitMqNames.NOTIFICATION_DLQ).build();

        Queue behaviorQueue = buildBusinessQueue(
                RabbitMqNames.BEHAVIOR_QUEUE,
                RabbitMqNames.BEHAVIOR_DLQ_ROUTING_KEY
        );
        Queue behaviorDlq = QueueBuilder.durable(RabbitMqNames.BEHAVIOR_DLQ).build();

        Queue broadcastQueue = buildBusinessQueue(
                RabbitMqNames.BROADCAST_QUEUE,
                RabbitMqNames.BROADCAST_DLQ_ROUTING_KEY
        );
        Queue broadcastDlq = QueueBuilder.durable(RabbitMqNames.BROADCAST_DLQ).build();

        Queue timeoutCheckQueue = buildBusinessQueue(
                RabbitMqNames.ORDER_TIMEOUT_CHECK_QUEUE,
                RabbitMqNames.ORDER_TIMEOUT_CHECK_DLQ_ROUTING_KEY
        );
        Queue timeoutCheckDlq = QueueBuilder.durable(RabbitMqNames.ORDER_TIMEOUT_CHECK_DLQ).build();

        Queue timeoutDelayQueue = QueueBuilder.durable(RabbitMqNames.ORDER_TIMEOUT_DELAY_QUEUE)
                .withArgument("x-message-ttl", timeoutMinutes * 60 * 1000)
                .withArgument("x-dead-letter-exchange", RabbitMqNames.EVENT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitMqNames.ROUTING_ORDER_TIMEOUT_CHECK)
                .build();

        return new Declarables(
                eventExchange,
                delayExchange,
                dlxExchange,
                notificationQueue,
                notificationDlq,
                behaviorQueue,
                behaviorDlq,
                broadcastQueue,
                broadcastDlq,
                timeoutCheckQueue,
                timeoutCheckDlq,
                timeoutDelayQueue,
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_CREATED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_PAID),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_CANCELLED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_TIMEOUT_CANCELLED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_STATUS_CHANGED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_REFUND_APPLIED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_REFUND_REFUNDED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_REFUND_REJECTED),
                bind(notificationQueue, eventExchange, RabbitMqNames.ROUTING_COUPON_ASSIGNED),
                bind(behaviorQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_PAID),
                bind(broadcastQueue, eventExchange, RabbitMqNames.ROUTING_MESSAGE_BROADCAST),
                bind(timeoutCheckQueue, eventExchange, RabbitMqNames.ROUTING_ORDER_TIMEOUT_CHECK),
                BindingBuilder.bind(timeoutDelayQueue).to(delayExchange).with(RabbitMqNames.ROUTING_ORDER_TIMEOUT_SCHEDULE),
                BindingBuilder.bind(notificationDlq).to(dlxExchange).with(RabbitMqNames.NOTIFICATION_DLQ_ROUTING_KEY),
                BindingBuilder.bind(behaviorDlq).to(dlxExchange).with(RabbitMqNames.BEHAVIOR_DLQ_ROUTING_KEY),
                BindingBuilder.bind(broadcastDlq).to(dlxExchange).with(RabbitMqNames.BROADCAST_DLQ_ROUTING_KEY),
                BindingBuilder.bind(timeoutCheckDlq).to(dlxExchange).with(RabbitMqNames.ORDER_TIMEOUT_CHECK_DLQ_ROUTING_KEY)
        );
    }

    @Bean(name = "mqListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory mqListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                          MqProperties mqProperties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);

        Advice retryAdvice = RetryInterceptorBuilder.stateless()
                .maxAttempts(Math.max(1, mqProperties.getListenerMaxAttempts()))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
        factory.setAdviceChain(retryAdvice);
        return factory;
    }

    private Queue buildBusinessQueue(String queueName, String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", RabbitMqNames.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }

    private Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
