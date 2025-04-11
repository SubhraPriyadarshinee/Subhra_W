package com.walmart.move.nim.receiving.core.message.common;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;

/**
 * *
 *
 * <p>RecievingJMSEvent is the POJO class for publishing or retrying the JMS Event. Below sample to
 * create the messageBody
 *
 * <pre>
 *
 * Gson gson = new Gson();
 * String messageBody = gson.toJson(object)
 *
 * </pre>
 *
 * @author sitakant
 */
public class ReceivingJMSEvent implements Serializable {
  /** */
  private static final long serialVersionUID = 1L;

  private Map<String, Object> headers;
  private String messageBody;

  private String contentType = MediaType.APPLICATION_JSON_VALUE;
  /**
   * @param headers
   * @param messageBody
   */
  public ReceivingJMSEvent(Map<String, Object> headers, String messageBody) {
    super();
    if (headers != null) {
      this.headers = headers;
      this.headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
      this.headers.put(
          ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
      this.messageBody = messageBody;
    }
  }

  public ReceivingJMSEvent(
      String facilityCode, String countryCode, Map<String, Object> headers, String messageBody) {
    super();

    if (headers == null) {
      headers = new HashMap<>();
    }
    this.headers = headers;

    this.headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityCode);
    this.headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, countryCode);
    this.headers.put(ReceivingConstants.JMS_CONTENT_TYPE_KEY, this.contentType);
    this.messageBody = messageBody;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
    this.headers.put(ReceivingConstants.JMS_CONTENT_TYPE_KEY, contentType);
  }
  /** @return the headers */
  public Map<String, Object> getHeaders() {
    return headers;
  }
  /** @param headers the headers to set */
  public void setHeaders(Map<String, Object> headers) {
    this.headers = headers;
  }
  /** @return the messageBody */
  public String getMessageBody() {
    return messageBody;
  }
  /** @param messageBody the messageBody to set */
  public void setMessageBody(String messageBody) {
    this.messageBody = messageBody;
  }
}
