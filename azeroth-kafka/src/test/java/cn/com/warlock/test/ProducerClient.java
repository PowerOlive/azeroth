package cn.com.warlock.test;

import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import cn.com.warlock.kafka.message.DefaultMessage;
import cn.com.warlock.kafka.monitor.KafkaMonitor;
import cn.com.warlock.kafka.spring.TopicProducerSpringProvider;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-kafka-producer.xml")
public class ProducerClient implements ApplicationContextAware {

    @Autowired
    private TopicProducerSpringProvider topicProducer;

    @Test
    public void testPublish() throws InterruptedException {

        final ExecutorService pool = Executors.newFixedThreadPool(3);
        final Timer timer = new Timer(true);

        final AtomicInteger count = new AtomicInteger(0);

        final int nums = 5000;
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (count.get() == nums) {
                    timer.cancel();
                    return;
                }
                for (int i = 0; i < 1; i++) {

                    pool.submit(new Runnable() {
                        @Override
                        public void run() {
                            String topic = new Random().nextBoolean() ? "demo-topic"
                                : "demo2-topic";
                            topicProducer.publish(topic,
                                new DefaultMessage(RandomStringUtils.random(5, true, true)));
                            count.incrementAndGet();
                        }
                    });
                }
            }
        }, 3000, 3000);

        while (true) {
            if (count.get() >= nums) {
                System.out.println(">>>>>>send count:" + count.get());
                break;
            }
        }

        pool.shutdown();
    }

    @Override
    public void setApplicationContext(ApplicationContext arg0) throws BeansException {
    }

}
