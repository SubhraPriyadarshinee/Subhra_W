package com.walmart.move.nim.receiving.fixture.event.processor;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.fixture.client.ItemMDMServiceClient;
import com.walmart.move.nim.receiving.fixture.client.ItemREPServiceClient;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.model.FixturesItemAttribute;
import com.walmart.move.nim.receiving.fixture.utils.ItemMDMUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractEventProcessor<T> {

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ItemREPServiceClient itemREPServiceClient;

  private AbstractEventProcessor<T> nextProcessor;

  public AbstractEventProcessor<T> getNextProcessor() {
    return nextProcessor;
  }

  public void setNextProcessor(AbstractEventProcessor<T> nextProcessor) {
    this.nextProcessor = nextProcessor;
  }

  protected abstract AbstractEventProcessor<T> getSelfReference();

  @ManagedConfiguration private FixtureManagedConfig fixtureManagedConfig;

  @Autowired ItemMDMServiceClient itemMDMServiceClient;

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventProcessor.class);

  /**
   * guard condition for execution step
   *
   * @return true, then the execution step will be executed.
   * @return false, then the current execution step will be skipped and moved to next one if
   *     available.
   */
  protected abstract boolean canExecute(T t);

  public abstract void executeStep(T t);

  public void execute(T t) {
    // invoke through self reference to get the annotation applied if any
    if (getSelfReference().canExecute(t)) {
      getSelfReference().executeStep(t);
    }
    // invoke next step
    if (Objects.nonNull(getNextProcessor())) {
      getNextProcessor().execute(t);
    }
  }

  protected void persistContainersAndItems(List<Container> containerList) {
    List<ContainerItem> containerItems = new ArrayList<>();
    containerList.forEach(container -> containerItems.addAll(container.getContainerItems()));
    containerPersisterService.saveContainerAndContainerItems(containerList, containerItems);
  }

  protected void enrichItemAttributes(List<Container> containerList) {
    List<ContainerItem> containerItems = new ArrayList<>();
    containerList.forEach(container -> containerItems.addAll(container.getContainerItems()));
    setFixturesItemAttribute(containerItems);
  }

  private void setFixturesItemAttribute(List<ContainerItem> containerItems) {
    // enriching item attributes from REP
    Set<Long> items =
        containerItems.stream().map(ContainerItem::getItemNumber).collect(Collectors.toSet());
    Map<Integer, FixturesItemAttribute> fixturesItemAttributeMap =
        itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(items);
    Map<String, Map<String, Object>> processedItemMap = new HashMap<>();

    if (fixtureManagedConfig.isItemMdmEnabled()) {
      try {
        Map<String, Object> itemResponse =
            itemMDMServiceClient.retrieveItemDetails(items, ReceivingUtils.getHeaders(), false);
        if (!CollectionUtils.isEmpty(itemResponse)) {
          List<Map<String, Object>> foundItems =
              (List<Map<String, Object>>)
                  itemResponse.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM);
          foundItems.forEach(
              item -> {
                Double itemNumber = (Double) item.get(FixtureConstants.ITEM_NUMBER);
                processedItemMap.put(String.valueOf(itemNumber.intValue()), item);
              });
        }
      } catch (Exception e) {
        LOGGER.error("Item mdm call fails for reason ::", e);
      }
    }
    containerItems
        .stream()
        .forEach(
            containerItem -> {
              FixturesItemAttribute item =
                  fixturesItemAttributeMap.get(containerItem.getItemNumber().intValue());
              // setting item attributes
              containerItem.setVnpkWgtQty(FixtureConstants.DEFAULT_VNPK_WEIGHT_QTY);
              containerItem.setVnpkWgtUom(
                  (Objects.isNull(item) || StringUtils.isEmpty(item.getWeightUnit()))
                      ? FixtureConstants.DEFAULT_VNPK_WEIGHT_UOM
                      : item.getWeightUnit());
              containerItem.setVnpkcbqty(
                  (Objects.isNull(item)
                          || (item.getArticleVolume() != null && item.getArticleVolume() == 0))
                      ? FixtureConstants.DEFAULT_VNPK_CUBE_QTY
                      : item.getArticleVolume().floatValue());
              containerItem.setVnpkcbuomcd(
                  (Objects.isNull(item) || StringUtils.isEmpty(item.getVolumeUnit()))
                      ? FixtureConstants.DEFAULT_VNPK_CUBE_UOM
                      : item.getVolumeUnit());
              if (fixtureManagedConfig.isItemMdmEnabled()) {
                Map<String, Object> itemDetails =
                    processedItemMap.get(String.valueOf(containerItem.getItemNumber()));
                containerItem.setItemUPC(ItemMDMUtils.getUpc(itemDetails));
              }
            });
  }
}
