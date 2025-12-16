package com.university.timetable.solver;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Holder for Spring ApplicationContext to allow OptaPlanner
 * constraint providers to access Spring beans.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            System.err.println("[SpringContextHolder] WARNING: ApplicationContext is null when trying to get " + beanClass.getSimpleName());
            return null;
        }
        T bean = context.getBean(beanClass);
        System.out.println("[SpringContextHolder] Retrieved bean: " + beanClass.getSimpleName() + " = " + (bean != null ? "OK" : "NULL"));
        return bean;
    }
}
