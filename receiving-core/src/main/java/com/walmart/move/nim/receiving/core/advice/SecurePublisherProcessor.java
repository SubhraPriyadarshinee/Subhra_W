package com.walmart.move.nim.receiving.core.advice;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.lang.reflect.Field;
import java.util.Objects;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * * Postprocessor to inject @{@link SecurePublisher} to the needy beans
 *
 * @author sitakant
 */
@Component
public class SecurePublisherProcessor implements BeanPostProcessor, ApplicationContextAware {

  private ApplicationContext applicationContext;

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName)
      throws BeansException {
    ReflectionUtils.doWithFields(
        bean.getClass(),
        new ReflectionUtils.FieldCallback() {
          @Override
          public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
            SecurePublisher securePublisher = field.getDeclaredAnnotation(SecurePublisher.class);
            if (Objects.nonNull(securePublisher)) {
              field.setAccessible(Boolean.TRUE);
              field.set(bean, applicationContext.getBean(ReceivingConstants.SECURE_KAFKA_TEMPLATE));
              field.setAccessible(Boolean.FALSE);
            }
          }
        });
    return bean;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
