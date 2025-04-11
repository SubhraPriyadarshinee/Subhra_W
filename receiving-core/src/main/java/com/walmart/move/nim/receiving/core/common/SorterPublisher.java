package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.core.model.sorter.SorterMessageAttribute;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;

public abstract class SorterPublisher {
  private static final String LABEL_TYPE_STORE = "STORE";
  private static final String LABEL_TYPE_EXCEPTION = "EXCEPTION";

  public void publishException(String lpn, SorterExceptionReason exceptionReason, Date labelDate) {}

  public void publishStoreLabel(Container container) {}

  protected ProgramSorterTO getSorterDivertPayloadForStoreLabel(Container container) {
    // prepare payload
    return ProgramSorterTO.builder()
        .uid(container.getTrackingId())
        .labelType(LABEL_TYPE_STORE)
        .labelDate(container.getPublishTs())
        .countryCode(container.getDestination().get(ReceivingConstants.COUNTRY_CODE))
        .storeNbr(container.getDestination().get(ReceivingConstants.BU_NUMBER))
        .build();
  }

  public ProgramSorterTO getSorterDivertPayLoadByLabelType(Container container, String labelType) {
    return ProgramSorterTO.builder()
        .uid(container.getTrackingId())
        .labelType(labelType)
        .labelDate(container.getPublishTs())
        .countryCode(container.getDestination().get(ReceivingConstants.COUNTRY_CODE))
        .storeNbr(container.getDestination().get(ReceivingConstants.BU_NUMBER))
        .build();
  }

  /**
   * This method builds the version 2 contract for lpn-create message to Athena.
   *
   * @param container
   * @param labelType
   * @return Sorter message payload
   */
  public ProgramSorterTO getSorterDivertPayLoadByLabelTypeV2(
      Container container, String labelType) {

    List<ContainerItem> containerItemList;
    List<Distribution> containerItemDistributionList = null;
    SorterMessageAttribute sorterMessageAttribute = null;
    ContainerItem firstContainerItem;
    if (Objects.nonNull(container)) {
      containerItemList = container.getContainerItems();
      if (CollectionUtils.isNotEmpty(containerItemList)) {
        firstContainerItem = containerItemList.get(0);
        containerItemDistributionList = firstContainerItem.getDistributions();
        sorterMessageAttribute =
            SorterMessageAttribute.builder()
                .upc(firstContainerItem.getItemUPC())
                .item(Math.toIntExact(firstContainerItem.getItemNumber()))
                .deptNbr(getDeptNbr(containerItemDistributionList))
                .poEvent(getPoEvent(firstContainerItem))
                .symbotic(getSymbotic(labelType))
                .mfc(getMfc(labelType))
                .build();
      }
    }
    String evaluatedLabelType = evaluateLabelType(labelType);
    return ProgramSorterTO.builder()
        .uid(getUid(container))
        .labelType(evaluatedLabelType)
        .storeNbr(getStoreNbr(container))
        .divisionNbr(getDivisionNbr(containerItemDistributionList))
        .originDcNbr(getOriginDcNbr(container))
        .countryCode(getCountryCode(container))
        .labelDate(getPublishTs(container))
        .attributes(sorterMessageAttribute)
        .build();
  }

  private Date getPublishTs(Container container) {
    if (Objects.nonNull(container)) {
      return container.getPublishTs();
    }
    return null;
  }

  private String getCountryCode(Container container) {
    if (Objects.nonNull(container)) {
      return container.getDestination().get(ReceivingConstants.COUNTRY_CODE);
    }
    return null;
  }

  private String getStoreNbr(Container container) {
    if (Objects.nonNull(container)) {
      return container.getDestination().get(ReceivingConstants.BU_NUMBER);
    }
    return null;
  }

  private String getUid(Container container) {
    if (Objects.nonNull(container)) {
      return container.getTrackingId();
    }
    return null;
  }

  private String getOriginDcNbr(Container container) {
    if (Objects.nonNull(container) && Objects.nonNull(container.getContainerMiscInfo())) {
      return container.getContainerMiscInfo().containsKey(ReceivingConstants.ORIGIN_FACILITY_NUM)
              && Objects.nonNull(
                  container.getContainerMiscInfo().get(ReceivingConstants.ORIGIN_FACILITY_NUM))
          ? container.getContainerMiscInfo().get(ReceivingConstants.ORIGIN_FACILITY_NUM).toString()
          : null;
    }
    return null;
  }

  private Integer getDivisionNbr(List<Distribution> containerItemDistributionList) {
    return CollectionUtils.isNotEmpty(containerItemDistributionList)
            && Objects.nonNull(containerItemDistributionList.get(0).getItem())
            && containerItemDistributionList
                .get(0)
                .getItem()
                .containsKey(ReceivingConstants.DIVISION_NUMBER)
            && Objects.nonNull(
                containerItemDistributionList
                    .get(0)
                    .getItem()
                    .get(ReceivingConstants.DIVISION_NUMBER))
        ? Integer.valueOf(
            containerItemDistributionList.get(0).getItem().get(ReceivingConstants.DIVISION_NUMBER))
        : null;
  }

  private Integer getDeptNbr(List<Distribution> containerItemDistributionList) {
    return CollectionUtils.isNotEmpty(containerItemDistributionList)
            && Objects.nonNull(containerItemDistributionList.get(0).getItem())
            && containerItemDistributionList
                .get(0)
                .getItem()
                .containsKey(ReceivingConstants.ITEM_DEPT)
        ? Integer.valueOf(
            containerItemDistributionList.get(0).getItem().get(ReceivingConstants.ITEM_DEPT))
        : null;
  }

  private String getMfc(String labelType) {
    return labelType.equalsIgnoreCase(LabelType.MFC.name()) ? LabelType.MFC.name() : null;
  }

  private String getSymbotic(String labelType) {
    return (SymAsrsSorterMapping.SYM2.getSymLabelType().equalsIgnoreCase(labelType)
            || SymAsrsSorterMapping.SYM2_5.getSymLabelType().equalsIgnoreCase(labelType)
            || SymAsrsSorterMapping.SYM3.getSymLabelType().equalsIgnoreCase(labelType))
        ? "SYM"
        : null;
  }

  private String getPoEvent(ContainerItem firstContainerItem) {
    return Objects.nonNull(firstContainerItem.getContainerItemMiscInfo())
            && firstContainerItem
                .getContainerItemMiscInfo()
                .containsKey(ReceivingConstants.PO_EVENT)
        ? String.valueOf(
            firstContainerItem.getContainerItemMiscInfo().get(ReceivingConstants.PO_EVENT))
        : null;
  }

  private String evaluateLabelType(String labelType) {
    if (LabelType.PUT.name().equalsIgnoreCase(labelType)) {
      return ReceivingConstants.PUT_INBOUND;
    } else if (LabelType.MFC.name().equalsIgnoreCase(labelType)
        || LabelType.STORE.name().equalsIgnoreCase(labelType)
        || labelType.contains("SYM")) {
      return LabelType.STORE.name();
    }
    return labelType;
  }

  protected ProgramSorterTO getSorterDivertPayloadForExceptionContainer(
      String lpn, SorterExceptionReason exceptionReason, Date labelDate) {
    // prepare payload
    return ProgramSorterTO.builder()
        .uid(lpn)
        .labelType(LABEL_TYPE_EXCEPTION)
        .labelDate(labelDate)
        .exceptionReason(exceptionReason)
        .build();
  }

  public void publishLabelToSorter(Container container, String labelType) {};
}
