package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.SHORTAGE;
import static com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType.UNRESOLVED;
import static com.walmart.move.nim.receiving.mfc.common.ProblemType.OVERAGE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DELIM_DASH;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCOSDRService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreProblemHandingProcessorTest extends ReceivingTestBase {

  @InjectMocks private StoreProblemHandingProcessor storeProblemHandingProcessor;

  @Mock private MFCOSDRService mfcosdrService;

  @InjectMocks private MFCProblemService problemService;

  @Mock private ProblemRepository problemRepository;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private ContainerTransformer containerTransformer;

  @Mock private ContainerService containerService;

  @Mock private ProcessInitiator processInitiator;

  @Mock private AsyncPersister asyncPersister;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private AppConfig appConfig;

  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private ReceivingCounterService receivingCounterService;

  @Mock private ProblemRegistrationService problemRegistrationService;

  @BeforeClass
  private void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(storeProblemHandingProcessor, "problemService", problemService);
    ReflectionTestUtils.setField(
        mfcContainerService, "receivingCounterService", receivingCounterService);
    TenantContext.setFacilityNum(5505);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    ReflectionTestUtils.setField(mfcContainerService, "appConfig", appConfig);
    ReflectionTestUtils.setField(
        mfcContainerService, "tenantSpecificConfigReader", tenantSpecificConfigReader);
  }

  @AfterMethod
  public void resetMock() {
    reset(mfcosdrService);
    reset(problemRepository);
    reset(mfcContainerService);
    reset(tenantSpecificConfigReader);
    reset(containerTransformer);
    reset(mfcManagedConfig);
    reset(appConfig);
    reset(retryableRestConnector);
    reset(processInitiator);
  }

  @Test
  public void testProblemShortage() {
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(Optional.empty());
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"createException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    ReceivingEvent receivingEvents =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(new ContainerDTO()))
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .build();
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetails(
                            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json")
                        .stream()
                        .findAny()
                        .get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    doCallRealMethod().when(processInitiator).initiateProcess(receivingEvents, null);
    doCallRealMethod().when(mfcContainerService).populateContainerMiscInfo(any(), any(), any());
    storeProblemHandingProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerTransformer, times(1)).transform(containerArgumentCaptor.capture());
    Container containerObj = containerArgumentCaptor.getValue();
    assertNotNull(containerObj);
    assertEquals(54.0, containerObj.getContainerItems().get(0).getDerivedQuantity());
    validateDeptCategory(containerObj);
    verify(problemRepository, times(1)).save(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(problemRegistrationService, times(1)).createProblem(any(), any(), any());
  }

  private void validateDeptCategory(Container containerObj) {
    for (ContainerItem containerItem : containerObj.getContainerItems()) {
      if (containerItem.getItemNumber().equals("574153621")) {
        assertEquals(containerItem.getDeptCatNbr().intValue(), 9340);
      } else if (containerItem.getGtin().equals("572519460")) {
        assertEquals(containerItem.getDeptCatNbr().intValue(), 1519);
      } else if (containerItem.getGtin().equals("563866383")) {
        assertNull(containerItem.getDeptCatNbr());
      }
    }
  }

  @Test
  public void testProblemOverageUpdate() {
    when(mfcosdrService.getAsnDocuments(anyLong(), anyList()))
        .thenReturn(
            getASNDetails("../../receiving-test/src/main/resources/json/mfc/ASNDocument.json"));
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getDefaultProblemLabel(OVERAGE + DELIM_DASH + UNRESOLVED));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(
            MFCTestUtils.getContainer(
                "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json"));

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"closeException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetails(
                            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json")
                        .stream()
                        .findAny()
                        .get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    storeProblemHandingProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerTransformer, times(1)).transform(containerArgumentCaptor.capture());
    Container containerObj = containerArgumentCaptor.getValue();
    assertNotNull(containerObj);
    assertEquals("EA", containerObj.getContainerItems().get(0).getDerivedQuantityUOM());
    assertEquals(54.0, containerObj.getContainerItems().get(0).getDerivedQuantity());
    validateDeptCategory(containerObj);
    verify(problemRepository, times(1)).save(any());
    verify(problemRegistrationService, times(1)).closeProblem(any(), any());
  }

  @Test
  public void testProblem_shortageExists() {
    when(mfcosdrService.getAsnDocuments(anyLong(), anyList()))
        .thenReturn(
            getASNDetails("../../receiving-test/src/main/resources/json/mfc/ASNDocument.json"));
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getDefaultProblemLabel(SHORTAGE + DELIM_DASH + UNRESOLVED));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(
            MFCTestUtils.getContainer(
                "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json"));

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"closeException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetails(
                            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json")
                        .stream()
                        .findAny()
                        .get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    storeProblemHandingProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerTransformer, times(1)).transform(containerArgumentCaptor.capture());
    Container containerObj = containerArgumentCaptor.getValue();
    assertNotNull(containerObj);
    assertEquals("EA", containerObj.getContainerItems().get(0).getDerivedQuantityUOM());
    assertEquals(54.0, containerObj.getContainerItems().get(0).getDerivedQuantity());
    validateDeptCategory(containerObj);
    verify(problemRepository, never()).save(any());
    verify(problemRegistrationService, times(1)).closeProblem(any(), any());
  }

  @Test
  public void testStorePalletDisabled() {
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(mfcosdrService.getAsnDocuments(anyLong(), anyList()))
        .thenReturn(getASNDetailsOnlyStores());
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(Optional.empty());
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"createException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));

    ReceivingEvent receivingEvents =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(new ContainerDTO()))
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .build();
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetailsOnlyStores().stream().findAny().get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    doCallRealMethod().when(processInitiator).initiateProcess(receivingEvents, null);
    doCallRealMethod().when(mfcContainerService).populateContainerMiscInfo(any(), any(), any());
    storeProblemHandingProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerTransformer, times(1)).transform(containerArgumentCaptor.capture());
    Container containerObj = containerArgumentCaptor.getValue();
    assertNotNull(containerObj);
    assertEquals(containerObj.getContainerItems().get(0).getDerivedQuantity(), 54.0);
    validateDeptCategory(containerObj);
    verify(problemRepository, times(1)).save(any());
    verify(processInitiator, times(0)).initiateProcess(any(), any());
    verify(mfcContainerService, times(0)).getContainerService();
  }

  @Test
  public void testStorePalletEnabled() {
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(mfcosdrService.getAsnDocuments(anyLong(), anyList()))
        .thenReturn(getASNDetailsOnlyStores());
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(Optional.empty());
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"createException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);

    ReceivingEvent receivingEvents =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(new ContainerDTO()))
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .build();
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetailsOnlyStores().stream().findAny().get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    doCallRealMethod().when(processInitiator).initiateProcess(receivingEvents, null);
    doCallRealMethod().when(mfcContainerService).populateContainerMiscInfo(any(), any(), any());
    storeProblemHandingProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerTransformer, times(1)).transform(containerArgumentCaptor.capture());
    Container containerObj = containerArgumentCaptor.getValue();
    assertNotNull(containerObj);
    assertEquals(containerObj.getContainerItems().get(0).getDerivedQuantity(), 54.0);
    validateDeptCategory(containerObj);
    verify(problemRepository, times(1)).save(any());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcContainerService, times(1)).getContainerService();
  }

  @Test
  public void testProblem_InvalidSourceType() {
    when(problemRepository.findOneByProblemTagIdAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getDefaultProblemLabel(SHORTAGE + DELIM_DASH + UNRESOLVED));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(
            MFCTestUtils.getContainer(
                "../../receiving-test/src/main/resources/json/mfc/MFCContainer.json"));
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);

    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setCounterNumber(100l);
    receivingCounter.setPrefix("PA");
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(receivingCounter);
    when(mfcContainerService.createContainer(any(ASNDocument.class), any(Pack.class), anyMap()))
        .thenCallRealMethod();
    doCallRealMethod()
        .when(mfcContainerService)
        .populateContainerMiscInfo(
            any(ASNDocument.class), any(Container.class), isNull(), any(Map.class));
    when(mfcContainerService.createPackItem(
            any(Pack.class), any(Item.class), any(ItemDetails.class), isNull()))
        .thenCallRealMethod();
    when(containerTransformer.transformList(any())).thenCallRealMethod();
    when(containerTransformer.transform(any())).thenCallRealMethod();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"closeException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getScalingQtyEnabledForReplenishmentTypes())
        .thenReturn(Collections.singletonList(MARKET_FULFILLMENT_CENTER));
    when(mfcManagedConfig.getEligibleSourceTypeForShortageContainerCreation())
        .thenReturn(Arrays.asList(SourceType.DC.name()));

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(
                JacksonParser.writeValueAsString(
                    getASNDetails(
                            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentSourceVendor.json")
                        .stream()
                        .findAny()
                        .get()))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(new HashMap<>())
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    storeProblemHandingProcessor.doExecute(receivingEvent);
    verify(problemRepository, never()).save(any());
  }

  private List<ASNDocument> getASNDetails(String path) {
    return Arrays.asList(MFCTestUtils.getASNDocument(path));
  }

  private List<ASNDocument> getASNDetailsOnlyStores() {
    return Arrays.asList(
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentPalletTypeStore.json"));
  }

  private Optional<ProblemLabel> getDefaultProblemLabel(String problemStatus) {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("dummyIssue");
    problemLabel.setProblemStatus(problemStatus);
    problemLabel.setFacilityNum(5504);
    return Optional.of(problemLabel);
  }
}
