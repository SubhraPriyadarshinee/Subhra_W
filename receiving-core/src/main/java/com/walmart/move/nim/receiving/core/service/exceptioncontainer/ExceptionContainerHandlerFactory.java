package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ExceptionContainerHandlerFactory implements ApplicationContextAware {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ExceptionContainerHandlerFactory.class);

  private ApplicationContext applicationContext;

  public ExceptionContainerHandler exceptionContainerHandler(
      ContainerException containerException) {
    switch (containerException) {
      case OVERAGE:
        LOGGER.info(
            "Selected bean for {} exception type is OverageExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(OverageExceptionContainerHandler.class);
      case NO_ALLOCATION_FOUND:
        LOGGER.info(
            "Selected bean for {} exception type is NoAllocationExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(NoAllocationExceptionContainerHandler.class);
      case CHANNEL_FLIP:
        LOGGER.info(
            "Selected bean for {} exception type is ChannelFlipExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(ChannelFlipExceptionContainerHandler.class);
      case DOCK_TAG:
        LOGGER.info(
            "Selected bean for {} exception type is DockTagExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(DockTagExceptionContainerHandler.class);
      case NO_DELIVERY_DOC:
        LOGGER.info(
            "Selected bean for {} exception type is NoDeliveryDocExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(NoDeliveryDocExceptionContainerHandler.class);
      case XBLOCK:
        LOGGER.info(
            "Selected bean for {} exception type is XBlockExceptionContainerHandler",
            containerException);
        return applicationContext.getBean(XBlockExceptionContainerHandler.class);
    }
    return applicationContext.getBean(ExceptionContainerHandler.class);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
