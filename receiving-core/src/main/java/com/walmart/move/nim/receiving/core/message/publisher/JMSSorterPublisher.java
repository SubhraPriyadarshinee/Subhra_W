package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.sorter.Pick;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(ReceivingConstants.JMS_SORTER_PUBLISHER)
public class JMSSorterPublisher extends SorterPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(JMSSorterPublisher.class);

  @Autowired private JmsPublisher jmsPublisher;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private MaasTopics maasTopics;

  public void publishException(String lpn, SorterExceptionReason exceptionReason, Date labelDate) {
    // prepare headers
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(ReceivingConstants.EVENT, ReceivingConstants.LPN_EXCEPTION);

    ProgramSorterTO programSorterTO =
        getSorterDivertPayloadForExceptionContainer(lpn, exceptionReason, labelDate);

    ReceivingJMSEvent jmsEvent =
        new ReceivingJMSEvent(messageHeaders, JacksonParser.writeValueAsString(programSorterTO));
    jmsPublisher.publish(appConfig.getSorterExceptionTopic(), jmsEvent, Boolean.TRUE);

    LOGGER.info("Successfully published exception container to Sorter {}", programSorterTO);
  }

  public void publishStoreLabel(Container container) {
    // prepare headers
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(ReceivingConstants.EVENT, ReceivingConstants.LPN_CREATE);

    ProgramSorterTO programSorterTO = getSorterDivertPayloadForStoreLabel(container);

    ReceivingJMSEvent jmsEvent =
        new ReceivingJMSEvent(messageHeaders, JacksonParser.writeValueAsString(programSorterTO));
    jmsPublisher.publish(appConfig.getSorterTopic(), jmsEvent, Boolean.TRUE);

    LOGGER.info("Successfully published diversion to Sorter {}", programSorterTO);
  }

  @Override
  public void publishLabelToSorter(Container container, String labelType) {
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(ReceivingConstants.EVENT, ReceivingConstants.LPN_CREATE);
    messageHeaders.put(ReceivingConstants.IS_ATLAS_ITEM, ReceivingConstants.Y);
    // label_type header is needed on Sorter end for PUT labels so supply demand file
    // create request can be directed to legacy PUT system
    if ((LabelType.XDK1.name()).equals(labelType) || labelType.equals(LabelType.PUT.name())) {
      messageHeaders.put(
          ReceivingConstants.LABEL_TYPE_FOR_PUT_SYSTEM, ReceivingConstants.LABEL_TYPE_PUT);
    }
    ProgramSorterTO programSorterTO = getSorterDivertPayloadWithInnerPicks(container, labelType);
    ReceivingJMSEvent jmsEvent =
        new ReceivingJMSEvent(messageHeaders, JacksonParser.writeValueAsString(programSorterTO));
    jmsPublisher.publish(maasTopics.getSorterDivertTopic(), jmsEvent, Boolean.TRUE);
    LOGGER.info(
        "Successfully published LPN Cross reference to Sorter topic:{} with payLoad:{}",
        maasTopics.getSorterDivertTopic(),
        programSorterTO);
  }

  private ProgramSorterTO getSorterDivertPayloadWithInnerPicks(
      Container container, String labelType) {

    if ((LabelType.XDK1.name()).equals(labelType) || labelType.equals(LabelType.PUT.name())) {
      return buildSorterPayLoadForPutLabels(container, labelType);
    }

    return getSorterDivertPayLoadByLabelType(container, labelType);
  }

  /**
   * This method prepares the sorter divert payload along with the innerpicks needed for PUT system.
   * In case of PUT labels, the destination store number was supposed to be DC number.
   *
   * @param container
   * @param labelType
   * @return
   */
  private ProgramSorterTO buildSorterPayLoadForPutLabels(Container container, String labelType) {
    List<Pick> innerPicks = null;
    Integer totalInnerPickQty = 0;
    Set<Container> childContainers = container.getChildContainers();
    String dcNumber =
        StringUtils.leftPad(
            Objects.requireNonNull(TenantContext.getFacilityNum()).toString(),
            ReceivingConstants.STORE_NUMBER_MAX_LENGTH,
            "0");
    if (Objects.nonNull(childContainers)) {
      innerPicks = new ArrayList<>();
      for (Container childContainer : childContainers) {
        if (!CollectionUtils.isEmpty(childContainer.getContainerItems())) {
          for (ContainerItem containerItem : childContainer.getContainerItems()) {
            Pick innerPick = new Pick();
            innerPick.setQuantity(containerItem.getQuantity());
            innerPick.setItemNbr(
                Objects.nonNull(containerItem.getItemNumber())
                    ? Math.toIntExact(containerItem.getItemNumber())
                    : null);
            innerPick.setDc(dcNumber);
            if (containerItem.getTrackingId().length() == ReceivingConstants.LPN_LENGTH_18) {
              innerPick.setStoreNbr(
                  containerItem
                      .getTrackingId()
                      .substring(
                          ReceivingConstants.CARTON_TAG_STORE_NUMBER_START_POSITION,
                          ReceivingConstants.CARTON_TAG_DIVISION_NUMBER_START_POSITION));
              innerPick.setDivisionNbr(
                  Integer.parseInt(
                      containerItem
                          .getTrackingId()
                          .substring(
                              ReceivingConstants.CARTON_TAG_DIVISION_NUMBER_START_POSITION,
                              ReceivingConstants.CARTON_TAG_DIVISION_NUMBER_END_POSITION)));
              innerPick.setCartonTag(
                  containerItem
                      .getTrackingId()
                      .substring(
                          ReceivingConstants.CARTON_TAG_DIVISION_NUMBER_END_POSITION,
                          ReceivingConstants.CARTON_TAG_END_POSITION));
            }
            innerPick.setDeptNbr(containerItem.getDeptNumber());
            innerPick.setWhpkQty(containerItem.getWhpkQty());
            innerPick.setReportingGroup(containerItem.getFinancialReportingGroupCode());
            innerPick.setBaseDivisionCode(containerItem.getBaseDivisionCode());

            if (MapUtils.isNotEmpty(childContainer.getContainerMiscInfo())) {
              if (childContainer
                  .getContainerMiscInfo()
                  .containsKey(ReceivingConstants.STORE_PICK_BATCH)) {
                innerPick.setPickBatchNbr(
                    childContainer
                        .getContainerMiscInfo()
                        .get(ReceivingConstants.STORE_PICK_BATCH)
                        .toString());
              }
              if (childContainer
                  .getContainerMiscInfo()
                  .containsKey(ReceivingConstants.STORE_PRINT_BATCH)) {
                innerPick.setPrintBatchNbr(
                    childContainer
                        .getContainerMiscInfo()
                        .get(ReceivingConstants.STORE_PRINT_BATCH)
                        .toString());
              }
              if (childContainer
                  .getContainerMiscInfo()
                  .containsKey(ReceivingConstants.STORE_AISLE)) {
                innerPick.setAisleNbr(
                    childContainer
                        .getContainerMiscInfo()
                        .get(ReceivingConstants.STORE_AISLE)
                        .toString());
              }
            }

            innerPicks.add(innerPick);
            totalInnerPickQty += innerPick.getQuantity();
          }
        }
      }
    }

    return ProgramSorterTO.builder()
        .uid(container.getTrackingId())
        .quantity(totalInnerPickQty)
        .labelType(labelType)
        .innerPicks(innerPicks)
        .labelDate(container.getPublishTs())
        .countryCode(container.getDestination().get(ReceivingConstants.COUNTRY_CODE))
        .storeNbr(dcNumber)
        .build();
  }
}
