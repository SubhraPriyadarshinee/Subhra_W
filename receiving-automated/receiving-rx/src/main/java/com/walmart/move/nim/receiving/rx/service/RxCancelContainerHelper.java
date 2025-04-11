package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RxCancelContainerHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(RxCancelContainerHelper.class);

  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ReceiptService receiptService;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Autowired private ContainerService containerService;
  @ManagedConfiguration RxManagedConfig rxManagedConfig;

  @Transactional
  @InjectTenantFilter
  public void persistCancelledContainers(
      List<Container> modifiedContainers,
      List<ContainerItem> modifiedContainerItems,
      Receipt receipt) {
    receiptService.saveReceipt(receipt);
    containerPersisterService.saveContainers(modifiedContainers);
    containerItemRepository.saveAll(modifiedContainerItems);
  }

  @Transactional
  @InjectTenantFilter
  public void persistCancelledContainers(
      List<Container> modifiedContainers,
      List<ContainerItem> modifiedContainerItems,
      List<Receipt> receipts) {
    receiptService.saveAll(receipts);
    containerPersisterService.saveContainers(modifiedContainers);
    containerItemRepository.saveAll(modifiedContainerItems);
  }

  public void publishCancelledContainers(List<Container> containers) {
    if (rxManagedConfig.isPublishContainersToKafkaEnabled()) {
      try {
        containerService.publishMultipleContainersToInventory(
            transformer.transformList(containers));
      } catch (Exception e) {
        LOGGER.error("Exception while publishing Containers to Kafka.");
      }
    }
  }
}
