package com.university.timetable.solver;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder for Spring ApplicationContext to allow Timefold Solver
 * constraint providers and move factories to access Spring beans.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SpringContextHolder.class);

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            log.warn("ApplicationContext is null when trying to get {}", beanClass.getSimpleName());
            return null;
        }
        return context.getBean(beanClass);
    }
}
