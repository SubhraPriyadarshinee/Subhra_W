package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.utils.Constants.ENABLE_STORE_PALLET_PUBLISH;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_ON_SCAN_RECEIPT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.utils.SIBTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PalletContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private PackContainerService packContainerService;

  @Mock private StoreDeliveryService deliveryService;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private ReceiptService receiptService;

  @Mock private ContainerService containerService;

  @Mock private ReceivingCounterService receivingCounterService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock SIBManagedConfig sibManagedConfig;

  @Mock private AppConfig appConfig;
  private Gson gson;
  private ContainerTransformer containerTransformer;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    containerTransformer = new ContainerTransformer();
    ReflectionTestUtils.setField(
        packContainerService, "containerTransformer", containerTransformer);
    ReflectionTestUtils.setField(packContainerService, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(receivingCounterService);
    Mockito.reset(tenantSpecificConfigReader);
    Mockito.reset(sibManagedConfig);
    Mockito.reset(appConfig);
    Mockito.reset(containerService);
    Mockito.reset(receiptService);
    Mockito.reset(containerPersisterService);
    Mockito.reset(deliveryService);
  }

  @Test
  public void testCreateCaseContainers() {
    when(sibManagedConfig.getEligibleDeliverySourceTypeForCaseCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(deliveryService.getAsnDocuments(any()))
        .thenReturn(
            Arrays.asList(
                SIBTestUtils.getASNDocument("src/test/resource/asn/asnWithCasePacks.json")));
    when(containerPersisterService.findSsccByDelivery(any())).thenReturn(Collections.emptySet());
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)).thenReturn(false);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_PUBLISH))
        .thenReturn(true);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setPrefix("SS");
    receivingCounter.setCounterNumber(12345L);
    when(receivingCounterService.counterUpdation(any(Long.class), any()))
        .thenReturn(receivingCounter);
    doNothing().when(containerService).publishMultipleContainersToInventory(any());
    when(sibManagedConfig.getPublishContainerKafkaBatchSize()).thenReturn(5);
    List<ContainerDTO> containerDTOs = packContainerService.createCaseContainers(550478600053907L);
    assertEquals(1, containerDTOs.size());
    for (ContainerItem containerItem : containerDTOs.get(0).getContainerItems()) {
      if (containerItem.getItemNumber().equals(567234238)) {
        assertEquals(5196, containerItem.getDeptCatNbr().intValue());
      } else {
        assertNull(containerItem.getDeptCatNbr());
      }
    }
  }

  @Test
  public void testCreateCaseContainersStorePalletPublishEnabled() {
    when(deliveryService.getAsnDocuments(any()))
        .thenReturn(
            Arrays.asList(
                SIBTestUtils.getASNDocument("src/test/resource/asn/asnWithCasePacks.json")));
    when(containerPersisterService.findSsccByDelivery(any())).thenReturn(Collections.emptySet());
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)).thenReturn(false);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_PUBLISH))
        .thenReturn(false);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setPrefix("SS");
    receivingCounter.setCounterNumber(12345L);
    when(receivingCounterService.counterUpdation(any(Long.class), any()))
        .thenReturn(receivingCounter);
    doNothing().when(containerService).publishMultipleContainersToInventory(any());
    when(sibManagedConfig.getPublishContainerKafkaBatchSize()).thenReturn(5);
    List<ContainerDTO> containerDTOs = packContainerService.createCaseContainers(550478600053907L);
    assertEquals(0, containerDTOs.size());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
  }

  @Test
  public void testCreateCaseContainers_VendorASN() {
    when(deliveryService.getAsnDocuments(any()))
        .thenReturn(
            Arrays.asList(
                SIBTestUtils.getASNDocument("src/test/resource/asn/ASNDocumentSourceVendor.json")));
    when(containerPersisterService.findSsccByDelivery(any())).thenReturn(Collections.emptySet());
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)).thenReturn(false);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setPrefix("SS");
    receivingCounter.setCounterNumber(12345L);
    when(receivingCounterService.counterUpdation(any(Long.class), any()))
        .thenReturn(receivingCounter);
    doNothing().when(containerService).publishMultipleContainersToInventory(any());
    when(sibManagedConfig.getPublishContainerKafkaBatchSize()).thenReturn(5);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForCaseCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    List<ContainerDTO> containerDTOs = packContainerService.createCaseContainers(32840000000994L);
    assertEquals(0, containerDTOs.size());
  }
}
