package cn.com.warlock.spring.web;

import javax.servlet.ServletContextEvent;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import cn.com.warlock.spring.InstanceFactory;
import cn.com.warlock.spring.SpringInstanceProvider;

public class ContextLoaderListener extends org.springframework.web.context.ContextLoaderListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String serviceName = event.getServletContext().getInitParameter("appName");
        System.setProperty("serviceName", serviceName == null ? "undefined" : serviceName);
        super.contextInitialized(event);
        WebApplicationContext applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(event.getServletContext());
        SpringInstanceProvider provider = new SpringInstanceProvider(applicationContext);
        InstanceFactory.setInstanceProvider(provider);
    }
}
