package com.walmart.move.nim.receiving.core.advice;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.JMS_CORRELATION_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.JMS_USER_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.UUID;
import javax.jms.Message;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * * This will intercept all the JMS messages and set the {@link MDC}
 *
 * @author sitakant
 */
@Aspect
@Component
public class JMSMDCLogger {

  @Around("@annotation(org.springframework.jms.annotation.JmsListener)")
  public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
    Message message = null;
    for (Object object : joinPoint.getArgs()) {
      if (object instanceof Message) {
        message = (Message) object;
      }
    }

    if (message != null) {
      String coRelationId =
          message.getStringProperty(JMS_CORRELATION_ID) == null
              ? UUID.randomUUID().toString()
              : message.getStringProperty(JMS_CORRELATION_ID);
      MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, coRelationId);
      MDC.put(TENENT_FACLITYNUM, String.valueOf(message.getIntProperty(TENENT_FACLITYNUM)));
      MDC.put(TENENT_COUNTRY_CODE, message.getStringProperty(TENENT_COUNTRY_CODE));
      MDC.put(ReceivingConstants.USER_ID_HEADER_KEY, message.getStringProperty(JMS_USER_ID));
    }

    Object excutionObject = joinPoint.proceed();

    MDC.clear();

    return excutionObject;
  }
}
