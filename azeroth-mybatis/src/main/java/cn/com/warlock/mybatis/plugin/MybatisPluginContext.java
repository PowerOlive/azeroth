package cn.com.warlock.mybatis.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import cn.com.warlock.mybatis.core.InterceptorHandler;
import cn.com.warlock.mybatis.core.InterceptorType;
import cn.com.warlock.mybatis.parser.MybatisMapperParser;
import cn.com.warlock.mybatis.plugin.cache.CacheHandler;
import cn.com.warlock.mybatis.plugin.rwseparate.RwRouteHandler;
import cn.com.warlock.mybatis.plugin.shard.DatabaseRouteHandler;
import cn.com.warlock.spring.InstanceFactory;
import cn.com.warlock.spring.SpringInstanceProvider;

/**
 * mybatis 插件入口
 */
@Intercepts({ @Signature(type = Executor.class, method = "update", args = { MappedStatement.class,
                                                                            Object.class }),
              @Signature(type = Executor.class, method = "query", args = { MappedStatement.class,
                                                                           Object.class,
                                                                           RowBounds.class,
                                                                           ResultHandler.class }) })
public class MybatisPluginContext implements Interceptor, InitializingBean, DisposableBean,
                                  ApplicationContextAware {

    //CRUD框架驱动 default，mapper3
    private String                   crudDriver          = "default";
    private List<InterceptorHandler> interceptorHandlers = new ArrayList<>();

    private static boolean           cacheEnabled, rwRouteEnabled, dbShardEnabled;

    //cache,rwRoute,dbShard
    public void setInterceptorHandlers(String interceptorHandlers) {
        String[] handlerNames = StringUtils.tokenizeToStringArray(interceptorHandlers,
            ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);

        for (String name : handlerNames) {
            if ("cache".equals(name)) {
                this.interceptorHandlers.add(new CacheHandler());
                cacheEnabled = true;
            } else if ("rwRoute".equals(name)) {
                this.interceptorHandlers.add(new RwRouteHandler());
                rwRouteEnabled = true;
            } else if ("dbShard".equals(name)) {
                this.interceptorHandlers.add(new DatabaseRouteHandler());
                dbShardEnabled = true;
            }
        }
    }

    public void setMapperLocations(String mapperLocations) {
        MybatisMapperParser.setMapperLocations(mapperLocations);
    }

    public void setCrudDriver(String crudDriver) {
        this.crudDriver = crudDriver;
    }

    public String getCrudDriver() {
        return crudDriver;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        boolean proceed = false;
        Object result = null;
        try {
            for (InterceptorHandler handler : interceptorHandlers) {
                Object object = handler.onInterceptor(invocation);
                if (handler.getInterceptorType().equals(InterceptorType.around)) {
                    result = object;
                    //查询缓存命中，则不执行分库和读写分离的处理器
                    if (result != null && handler instanceof CacheHandler) {
                        break;
                    }
                }
            }
            if (result == null) {
                result = invocation.proceed();
                proceed = true;
            }
            return result;
        } finally {
            for (InterceptorHandler handler : interceptorHandlers) {
                handler.onFinished(invocation, proceed ? result : null);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        } else {
            return target;
        }

    }

    @Override
    public void setProperties(Properties properties) {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        for (InterceptorHandler handler : interceptorHandlers) {
            handler.start(this);
        }
    }

    @Override
    public void destroy() throws Exception {
        for (InterceptorHandler handler : interceptorHandlers) {
            handler.close();
        }
    }

    public static boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public static boolean isRwRouteEnabled() {
        return rwRouteEnabled;
    }

    public static boolean isDbShardEnabled() {
        return dbShardEnabled;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        InstanceFactory.setInstanceProvider(new SpringInstanceProvider(applicationContext));
    }

}
