package cn.com.warlock.kafka.spring;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import cn.com.warlock.common.util.NodeNameHolder;
import cn.com.warlock.common.util.ResourceUtils;
import cn.com.warlock.kafka.consumer.ConsumerContext;
import cn.com.warlock.kafka.consumer.NewApiTopicConsumer;
import cn.com.warlock.kafka.consumer.OldApiTopicConsumer;
import cn.com.warlock.kafka.consumer.TopicConsumer;
import cn.com.warlock.kafka.handler.MessageHandler;
import cn.com.warlock.kafka.handler.OffsetLogHanlder;
import cn.com.warlock.kafka.serializer.KyroMessageDeserializer;
import cn.com.warlock.kafka.utils.KafkaConst;

public class TopicConsumerSpringProvider implements InitializingBean, DisposableBean {

    private final static Logger         logger         = LoggerFactory
        .getLogger(TopicConsumerSpringProvider.class);

    private TopicConsumer               consumer;

    /**
     * 配置
     */
    private Properties                  configs;

    // 是否独立进程
    private boolean                     independent;

    private boolean                     useNewAPI      = false;

    private Map<String, MessageHandler> topicHandlers;

    private int                         processThreads = 200;

    private String                      groupId;

    private String                      consumerId;

    // 标记状态（0：未运行，1：启动中，2：运行中，3：停止中，4：重启中）
    private AtomicInteger               status         = new AtomicInteger(0);

    // 环境路由
    private String                      routeEnv;

    private OffsetLogHanlder            offsetLogHanlder;

    @Override
    public void afterPropertiesSet() throws Exception {

        Validate.isTrue(topicHandlers != null && topicHandlers.size() > 0, "at latest one topic");
        // 当前状态
        if (status.get() > 0)
            return;

        routeEnv = StringUtils.trimToNull(ResourceUtils.get(KafkaConst.PROP_ENV_ROUTE));

        if (routeEnv != null) {
            logger.info("current route Env value is:", routeEnv);
            Map<String, MessageHandler> newTopicHandlers = new HashMap<>();
            for (String origTopicName : topicHandlers.keySet()) {
                newTopicHandlers.put(routeEnv + "." + origTopicName,
                    topicHandlers.get(origTopicName));
            }
            topicHandlers = newTopicHandlers;
        }

        // make sure that rebalance.max.retries * rebalance.backoff.ms >
        // zookeeper.session.timeout.ms.
        configs.put("rebalance.max.retries", "5");
        configs.put("rebalance.backoff.ms", "1205");
        configs.put("zookeeper.session.timeout.ms", "6000");

        configs.put("key.deserializer", StringDeserializer.class.getName());

        if (!configs.containsKey("value.deserializer")) {
            configs.put("value.deserializer", KyroMessageDeserializer.class.getName());
        }

        if (useNewAPI) {
            if ("smallest".equals(configs.getProperty("auto.offset.reset"))) {
                configs.put("auto.offset.reset", "earliest");
            } else if ("largest".equals(configs.getProperty("auto.offset.reset"))) {
                configs.put("auto.offset.reset", "latest");
            }
        } else {
            // 强制自动提交
            configs.put("enable.auto.commit", "true");
        }

        // 同步节点信息
        groupId = configs.get(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG)
            .toString();

        logger.info("\n===============KAFKA Consumer group[{}] begin start=================\n",
            groupId);

        consumerId = NodeNameHolder.getNodeId();
        //
        configs.put("consumer.id", consumerId);

        // kafka 内部处理 consumerId ＝ groupId + "_" + consumerId
        consumerId = groupId + "_" + consumerId;
        //
        if (!configs.containsKey("client.id")) {
            configs.put("client.id", consumerId);
        }
        //
        start();

        logger.info(
            "\n===============KAFKA Consumer group[{}],consumerId[{}] start finished!!=================\n",
            groupId, consumerId);
    }

    /**
     * 启动
     */
    private void start() {
        if (independent) {
            logger.info("KAFKA 启动模式[independent]");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    registerKafkaSubscriber();
                }
            }).start();
        } else {
            registerKafkaSubscriber();
        }
    }

    /**
     * 
     */
    @SuppressWarnings("rawtypes")
    private void registerKafkaSubscriber() {
        // 状态：启动中
        status.set(1);

        Validate.notEmpty(this.configs, "configs is required");
        Validate.notEmpty(this.configs.getProperty("group.id"),
            "kafka configs[group.id] is required");
        Validate.notEmpty(this.configs.getProperty("bootstrap.servers"),
            "kafka configs[bootstrap.servers] is required");

        StringBuffer sb = new StringBuffer();
        Iterator itr = this.configs.entrySet().iterator();
        while (itr.hasNext()) {
            Entry e = (Entry) itr.next();
            sb.append(e.getKey()).append("  =  ").append(e.getValue()).append("\n");
        }
        logger.info("\n============kafka.Consumer.Config============\n" + sb.toString() + "\n");

        ConsumerContext consumerContext = new ConsumerContext(configs, groupId, consumerId,
            topicHandlers, processThreads);
        if (useNewAPI) {
            consumer = new NewApiTopicConsumer(consumerContext);
        } else {
            consumer = new OldApiTopicConsumer(consumerContext);
        }

        consumerContext.setOffsetLogHanlder(offsetLogHanlder);

        consumer.start();
        // 状态：运行中
        status.set(2);
    }

    /**
     * kafka 配置
     *
     * @param configs
     *            kafka 配置
     */
    public void setConfigs(Properties configs) {
        this.configs = configs;
    }

    public void setTopicHandlers(Map<String, MessageHandler> topicHandlers) {
        this.topicHandlers = topicHandlers;
    }

    public void setIndependent(boolean independent) {
        this.independent = independent;
    }

    public void setProcessThreads(int processThreads) {
        this.processThreads = processThreads;
    }

    public void setUseNewAPI(boolean useNewAPI) {
        this.useNewAPI = useNewAPI;
    }

    public void setOffsetLogHanlder(OffsetLogHanlder offsetLogHanlder) {
        this.offsetLogHanlder = offsetLogHanlder;
    }

    @Override
    public void destroy() throws Exception {
        consumer.close();
    }

}
