package com.walmart.move.nim.receiving.core.transformer;

import static com.walmart.move.nim.receiving.core.utils.UomUtils.getBaseUnitQuantity;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(ReceivingConstants.CONTAINER_TRANSFORMER_BEAN)
public class ContainerTransformer implements Transformer<Container, ContainerDTO> {

  private Gson gson;

  public ContainerTransformer() {
    this.gson = new Gson();
  }

  @Override
  public ContainerDTO transform(Container container) {

    List<ContainerTag> containerTag = convertToContainerTag(container.getContainerMiscInfo());
    String labelType = getLabelType(container.getContainerMiscInfo());
    String fulfillmentMethod = getFulfillmentMethod(container.getContainerMiscInfo());
    List<ContainerItem> containerItems =
        CollectionUtils.isEmpty(container.getChildContainers())
            ? getModifiedContainerItem(container.getContainerItems())
            : Collections.emptyList();

    return ContainerDTO.builder()
        .containerException(container.getContainerException())
        .tags(containerTag)
        .containerMiscInfo(container.getContainerMiscInfo())
        .containerType(container.getContainerType())
        .containerStatus(container.getContainerStatus())
        .containerItems(containerItems)
        .childContainers(container.getChildContainers())
        .activityName(container.getActivityName())
        .completeTs(container.getCompleteTs())
        .createTs(container.getCreateTs())
        .createUser(container.getCreateUser())
        .ctrReusable(container.getCtrReusable())
        .ctrShippable(container.getCtrShippable())
        .cube(container.getCube())
        .cubeUOM(container.getCubeUOM())
        .deliveryNumber(container.getDeliveryNumber())
        .destination(container.getDestination())
        .hasChildContainers(container.isHasChildContainers())
        .facility(container.getFacility())
        .instructionId(container.getInstructionId())
        .inventoryStatus(container.getInventoryStatus())
        .isAudited(container.isAudited())
        .isConveyable(container.getIsConveyable())
        .labelId(container.getLabelId())
        .lastChangedTs(container.getLastChangedTs())
        .lastChangedUser(container.getLastChangedUser())
        .location(container.getLocation())
        .messageId(container.getMessageId())
        .onConveyor(container.getOnConveyor())
        .orgUnitId(container.getSubcenterId())
        .parentTrackingId(container.getParentTrackingId())
        .publishTs(container.getPublishTs())
        .trackingId(container.getTrackingId())
        .weight(container.getWeight())
        .weightUOM(container.getWeightUOM())
        .ssccNumber(container.getSsccNumber())
        .shipmentId(container.getShipmentId())
        .isAuditRequired(container.getIsAuditRequired())
        .documentType(container.getDocumentType())
        .documentNumber(container.getDocumentNumber())
            .isCompliancePack(container.getIsCompliancePack())
        .labelType(labelType)
        .fulfillmentMethod(fulfillmentMethod)
        .asnNumber(container.getAsnNumber())
        .eligibility(container.getEligibility())
        .additionalInformation(container.getAdditionalInformation())
        .build();
  }

  private String getLabelType(Map<String, Object> containerMiscInfo) {
    return Objects.nonNull(containerMiscInfo)
            && containerMiscInfo.containsKey(ReceivingConstants.INVENTORY_LABEL_TYPE)
        ? containerMiscInfo.get(ReceivingConstants.INVENTORY_LABEL_TYPE).toString()
        : null;
  }

  private String getFulfillmentMethod(Map<String, Object> containerMiscInfo) {
    return Objects.nonNull(containerMiscInfo)
            && containerMiscInfo.containsKey(ReceivingConstants.OP_FULFILLMENT_METHOD)
        ? containerMiscInfo.get(ReceivingConstants.OP_FULFILLMENT_METHOD).toString()
        : null;
  }

  private List<ContainerItem> getModifiedContainerItem(List<ContainerItem> containerItems) {
    containerItems.stream().forEach(containerItem -> transformToContainerItem(containerItem));
    return containerItems;
  }

  private void transformToContainerItem(ContainerItem containerItem) {
    Pair<Double, String> baseUnitQuantity =
        getBaseUnitQuantity(containerItem.getQuantity(), containerItem.getQuantityUOM());
    containerItem.setDerivedQuantity(baseUnitQuantity.getFirst());
    containerItem.setDerivedQuantityUOM(baseUnitQuantity.getSecond());
  }

  @Override
  public List<ContainerDTO> transformList(List<Container> containers) {
    return containers.stream().map(container -> transform(container)).collect(Collectors.toList());
  }

  private List<ContainerTag> convertToContainerTag(Map<String, Object> containerMiscInfo) {
    if (Objects.isNull(containerMiscInfo)
        || Objects.isNull(containerMiscInfo.get(ReceivingConstants.CONTAINER_TAG))) {
      return null;
    }

    return this.gson.fromJson(
        containerMiscInfo.get(ReceivingConstants.CONTAINER_TAG).toString(), List.class);
  }

  @Override
  public Container reverseTransform(ContainerDTO containerDTO) {
    Map<String, Object> miscValues = new HashMap<>();
    populateContainerTag(containerDTO, miscValues);
    Container container =
        Container.builder()
            .trackingId(containerDTO.getTrackingId())
            .messageId(containerDTO.getMessageId())
            .parentTrackingId(containerDTO.getParentTrackingId())
            .instructionId(containerDTO.getInstructionId())
            .location(containerDTO.getLocation())
            .deliveryNumber(containerDTO.getDeliveryNumber())
            .facility(containerDTO.getFacility())
            .destination(containerDTO.getDestination())
            .containerType(containerDTO.getContainerType())
            .containerStatus(containerDTO.getContainerStatus())
            .weight(containerDTO.getWeight())
            .weightUOM(containerDTO.getWeightUOM())
            .cube(containerDTO.getCube())
            .cubeUOM(containerDTO.getCubeUOM())
            .ctrShippable(containerDTO.getCtrShippable())
            .ctrReusable(containerDTO.getCtrReusable())
            .inventoryStatus(containerDTO.getInventoryStatus())
            .subcenterId(containerDTO.getOrgUnitId())
            .completeTs(containerDTO.getCompleteTs())
            .publishTs(containerDTO.getPublishTs())
            .createTs(containerDTO.getCreateTs())
            .createUser(containerDTO.getCreateUser())
            .lastChangedTs(containerDTO.getLastChangedTs())
            .lastChangedUser(containerDTO.getLastChangedUser())
            .onConveyor(containerDTO.getOnConveyor())
            .isConveyable(containerDTO.getIsConveyable())
            .containerException(containerDTO.getContainerException())
            .labelId(containerDTO.getLabelId())
            .activityName(containerDTO.getActivityName())
            .containerMiscInfo(miscValues)
            .containerItems(
                containerDTO
                    .getContainerItems()) // Required as tight dependency on Move Generation :
            // InstructionUtils.getMoveQuantity(container)
            .isAudited(containerDTO.isAudited())
            .hasChildContainers(containerDTO.isHasChildContainers())
            .childContainers(containerDTO.getChildContainers())
            .ssccNumber(containerDTO.getSsccNumber())
            .shipmentId(containerDTO.getShipmentId())
            .documentType(containerDTO.getDocumentType())
            .documentNumber(containerDTO.getDocumentNumber())
            .build();

    return container;
  }

  @Override
  public List<Container> reverseTransformList(List<ContainerDTO> containerVOs) {
    return containerVOs
        .stream()
        .map(containerDTO -> reverseTransform(containerDTO))
        .collect(Collectors.toList());
  }

  private void populateContainerTag(ContainerDTO containerDTO, Map<String, Object> miscValues) {

    if (Objects.isNull(containerDTO.getTags())) {
      return;
    }

    miscValues.put(
        ReceivingConstants.CONTAINER_TAG, ReceivingUtils.stringfyJson(containerDTO.getTags()));
  }
}
