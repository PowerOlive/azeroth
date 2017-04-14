package cn.com.warlock.scheduler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import cn.com.warlock.spring.InstanceFactory;
import cn.com.warlock.spring.SpringInstanceProvider;

public class SchedulerFactoryBeanWrapper implements ApplicationContextAware, InitializingBean,
                                         DisposableBean {

    protected static final Logger logger = LoggerFactory
        .getLogger(SchedulerFactoryBeanWrapper.class);

    private ApplicationContext    context;

    private String                groupName;

    List<AbstractJob>             schedulers;

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setSchedulers(List<AbstractJob> schedulers) {
        this.schedulers = schedulers;
    }

    public void setConfigPersistHandler(ConfigPersistHandler configPersistHandler) {
        JobContext.getContext().setConfigPersistHandler(configPersistHandler);
    }

    public void setRegistry(JobRegistry registry) {
        JobContext.getContext().setRegistry(registry);
    }

    public void setJobLogPersistHandler(JobLogPersistHandler jobLogPersistHandler) {
        JobContext.getContext().setJobLogPersistHandler(jobLogPersistHandler);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
        InstanceFactory.setInstanceProvider(new SpringInstanceProvider(context));
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        Validate.notBlank(groupName);

        DefaultListableBeanFactory acf = (DefaultListableBeanFactory) context
            .getAutowireCapableBeanFactory();

        List<Trigger> triggers = new ArrayList<>();
        for (AbstractJob sch : schedulers) {
            sch.setGroup(groupName);
            sch.init();
            triggers.add(registerSchedulerTriggerBean(acf, sch));
        }
        //
        new Thread(new Runnable() {
            @Override
            public void run() {
                InstanceFactory.waitUtilInitialized();
                for (AbstractJob sch : schedulers) {
                    sch.afterInitialized();
                }
            }
        }).start();

        String beanName = "schedulerFactory";
        BeanDefinitionBuilder beanDefBuilder = BeanDefinitionBuilder
            .genericBeanDefinition(SchedulerFactoryBean.class);
        beanDefBuilder.addPropertyValue("triggers", triggers);
        acf.registerBeanDefinition(beanName, beanDefBuilder.getRawBeanDefinition());

        for (final AbstractJob sch : schedulers) {
            //
            JobContext.getContext().addJob(sch);
            //
            if (sch.isExecuteOnStarted()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        logger.info("<<Job[{}] execute on startup....", sch.jobName);
                        sch.execute();
                        logger.info(">>Job[{}] execute on startup ok!", sch.jobName);
                    }
                }).start();
            }
        }
    }

    /**
     * 
     * @param acf
     * @param sch
     * @return
     */
    private Trigger registerSchedulerTriggerBean(DefaultListableBeanFactory acf, AbstractJob sch) {
        //注册JobDetail
        String jobDetailBeanName = sch.getJobName() + "JobDetail";
        if (context.containsBean(jobDetailBeanName)) {
            throw new RuntimeException("duplicate jobName[" + sch.getJobName() + "] defined!!");
        }
        BeanDefinitionBuilder beanDefBuilder = BeanDefinitionBuilder
            .genericBeanDefinition(MethodInvokingJobDetailFactoryBean.class);
        beanDefBuilder.addPropertyValue("targetObject", sch);
        beanDefBuilder.addPropertyValue("targetMethod", "execute");
        beanDefBuilder.addPropertyValue("concurrent", false);
        acf.registerBeanDefinition(jobDetailBeanName, beanDefBuilder.getRawBeanDefinition());

        //注册Trigger
        String triggerBeanName = sch.getJobName() + "Trigger";
        beanDefBuilder = BeanDefinitionBuilder.genericBeanDefinition(CronTriggerFactoryBean.class);
        beanDefBuilder.addPropertyReference("jobDetail", jobDetailBeanName);
        beanDefBuilder.addPropertyValue("cronExpression", sch.getCronExpr());
        beanDefBuilder.addPropertyValue("group", groupName);
        acf.registerBeanDefinition(triggerBeanName, beanDefBuilder.getRawBeanDefinition());

        logger.info("register scheduler task [{}] ok!!", sch.getJobName());
        return (Trigger) context.getBean(triggerBeanName);

    }

    @Override
    public void destroy() throws Exception {
        JobContext.getContext().close();
    }

}
