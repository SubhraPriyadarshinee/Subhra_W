package com.walmart.move.nim.receiving.core.model.inventory;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.Eligibility;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerDTO extends MessageData {

  @Expose private String trackingId;

  @Expose private String messageId;

  @Expose private String parentTrackingId;

  @Expose private Long instructionId;

  @Expose private String location;

  @Expose private Long deliveryNumber;

  @Expose private Map<String, String> facility;

  @Expose private Map<String, String> destination;
  @Expose private String containerType;
  @Expose private String labelType;
  @Expose private String containerStatus;
  @Expose private Float weight;
  @Expose private String weightUOM;
  @Expose private Float cube;
  @Expose private String cubeUOM;
  @Expose private Boolean ctrShippable;
  @Expose private Boolean ctrReusable;
  @Expose private String inventoryStatus;
  @Expose private Integer orgUnitId;
  @Expose private Date completeTs;
  @Expose private Date publishTs;
  @Expose private Date createTs;
  @Expose private String createUser;
  @Expose private Date lastChangedTs;
  @Expose private String lastChangedUser;
  @Expose private boolean isAudited;
  @Expose private Boolean isAuditRequired;

  /*
   New field to indicate if we have any childContainers or not.
  */ @Expose private boolean hasChildContainers = false;
  @Expose private Set<Container> childContainers;
  @Expose private Boolean onConveyor;
  @Expose private Boolean isConveyable;
  @Expose private String containerException;
  @Expose private Integer labelId;
  @Expose private String activityName;
  @Expose private String ssccNumber;
  @Expose private String eventType;
  @Expose private Map<String, Object> containerMiscInfo;
  @Expose private List<ContainerTag> tags;
  @Expose private String shipmentId;

  @Expose
  @SerializedName(value = "contents")
  private List<ContainerItem> containerItems;

  @Expose private String documentType;
  @Expose private String documentNumber;
  @Expose private Integer vendorNumber;
  @Expose private String labelPrintInd;
  @Expose private Integer subcenterId;
  @Expose private Boolean isCompliancePack;
  @Expose private SubcenterInfo subcenterInfo;
  @Expose private OrgUnitIdInfo orgUnitIdInfo;
  @Expose private String fulfillmentMethod;
  @Expose private String asnNumber;
  @Expose private Eligibility eligibility;

  private Map<String, Object> additionalInformation;
}
