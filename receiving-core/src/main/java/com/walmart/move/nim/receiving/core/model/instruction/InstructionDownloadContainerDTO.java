package com.walmart.move.nim.receiving.core.model.instruction;

import com.walmart.move.nim.receiving.core.model.Facility;
import java.util.List;
import lombok.Data;

@Data
public class InstructionDownloadContainerDTO {

  private String trackingId;
  private String prevTrackingId;
  private String sscc;
  private String cartonTag;
  private String ctrType;
  private List<InstructionDownloadDistributionsDTO> distributions;
  private Facility finalDestination;
  private String outboundChannelMethod;
  private String channelMethod;
  private boolean ctrReusable;
  private String fulfillmentMethod;
  private String labelType;
  private String poEvent;
}
