package com.walmart.move.nim.receiving.core.message.common;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpHeaders;

/**
 * * This is the contract for deliveryUpdate from GDM
 *
 * @see <a href="https://collaboration.wal-mart.com/display/GDM/GDM+-+Events">Reference docs</a>
 * @author sitakant
 */
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdateMessage extends MessageData {
  private String eventType;
  private String deliveryStatus;
  private String deliveryNumber;
  private String countryCode;
  private String siteNumber;
  private String url;
  private DeliveryMessageEvent event;
  private DeliveryPayload payload;
  private String shipmentDocumentId;
  private String shipmentDocumentType;
  private String poNumber;
  private Integer poLineNumber;
  private HttpHeaders httpHeaders;
  private String doorNumber;
}
