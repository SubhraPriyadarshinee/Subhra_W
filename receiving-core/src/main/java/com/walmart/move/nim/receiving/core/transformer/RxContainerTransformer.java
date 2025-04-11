package com.walmart.move.nim.receiving.core.transformer;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import static com.walmart.move.nim.receiving.core.utils.UomUtils.getBaseUnitQuantity;

@Component(ReceivingConstants.RX_CONTAINER_TRANSFORMER_BEAN)
@Slf4j
public class RxContainerTransformer extends ContainerTransformer {

    private Gson gson;

    @Override
    public List<ContainerDTO> transformList(List<Container> containers) {
        List<ContainerDTO> containerDTOList =
        containers.stream().map(this::transform).collect(Collectors.toList());
        replaceMessageIdWithRandomId(containerDTOList);
        return containerDTOList;
    }

    // DCFIN is using this messageId as TransactionId and It needs to be unique
    private static void replaceMessageIdWithRandomId(List<ContainerDTO> containerDTOList) {
        String idempotencyKey = TenantContext.getFacilityCountryCode() + "_" + TenantContext.getFacilityNum() + "_";
        containerDTOList.forEach(containerDTO -> {
            log.info("Container trackingId: {}, Old messageId: {} is replaced", containerDTO.getTrackingId(), containerDTO.getMessageId());
            containerDTO.setMessageId(idempotencyKey + containerDTO.getTrackingId());
            containerDTO.getChildContainers().forEach(childContainer -> {
                log.info("ChildContainer trackingId: {}, Old messageId: {} is replaced", childContainer.getTrackingId(), childContainer.getMessageId());
                childContainer.setMessageId(idempotencyKey + childContainer.getTrackingId());
            });
        });
    }

  @Override
  public ContainerDTO transform(Container container) {
    List<ContainerTag> containerTag = convertToContainerTag(container.getContainerMiscInfo());
    return ContainerDTO.builder()
        .containerException(container.getContainerException())
        .tags(containerTag)
        .containerMiscInfo(container.getContainerMiscInfo())
        .containerType(container.getContainerType())
        .containerStatus(container.getContainerStatus())
        .containerItems(getModifiedContainerItem(container.getContainerItems()))
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
        .build();
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

    private List<ContainerTag> convertToContainerTag(Map<String, Object> containerMiscInfo) {
        if (Objects.isNull(containerMiscInfo)
                || Objects.isNull(containerMiscInfo.get(ReceivingConstants.CONTAINER_TAG))) {
            return null;
        }

        return this.gson.fromJson(
                containerMiscInfo.get(ReceivingConstants.CONTAINER_TAG).toString(), List.class);
    }
}
