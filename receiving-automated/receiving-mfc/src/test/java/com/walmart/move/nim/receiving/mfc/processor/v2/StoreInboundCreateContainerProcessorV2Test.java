package com.walmart.move.nim.receiving.mfc.processor.v2;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.OPERATION_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ORIGINAL_DELIVERY_NUMBER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.OVERAGE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.PALLET_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.STORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_OV;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.ContainerEventService;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.mfc.transformer.ContainerDTOEventTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreInboundCreateContainerProcessorV2Test extends ReceivingTestBase {

  @InjectMocks private StoreInboundCreateContainerProcessorV2 storeInboundCreateContainerProcessor;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @InjectMocks private MFCProblemService problemService;

  @Mock private ProblemRepository problemRepository;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private ContainerService containerService;

  @Mock private KafkaTemplate kafkaTemplate;

  @Mock private ContainerEventService containerEventService;

  @Mock private ContainerDTOEventTransformer containerDTOEventTransformer;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private AppConfig appConfig;

  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private ProcessInitiator processInitiator;

  @Mock private ContainerItemRepository containerItemRepository;

  @Mock private DecantService decantService;
  @Mock private ContainerTransformer containerTransformer;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private ProblemRegistrationService problemRegistrationService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor, "problemService", problemService);

    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor,
        "isMFCPalletAllowedPostDeliveryComplete",
        Boolean.TRUE);
    TenantContext.setFacilityNum(5505);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(containerDTOEventTransformer.transform(any())).thenCallRealMethod();
  }

  @AfterMethod
  public void resetMocks() {
    reset(mfcDeliveryMetadataService);
    reset(problemRepository);
    reset(deliveryService);
    reset(mfcContainerService);
    reset(containerService);
    reset(mfcManagedConfig);
    reset(appConfig);
    reset(retryableRestConnector);
    reset(processInitiator);
    reset(containerItemRepository);
    reset(containerTransformer);
    reset(tenantSpecificConfigReader);
    reset(problemRegistrationService);

    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_USER);
  }

  @Test
  public void testOverageContainerCreation() {
    when(mfcContainerService.createTransientContainer(any(), any())).thenReturn(getContainer());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"createException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);

    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(mfcContainerService, times(1)).getContainerService();
    verify(containerService, times(1)).publishMultipleContainersToInventory(any(List.class));
    verify(problemRepository, times(1)).save(any());
    verify(problemRegistrationService, times(1)).createProblem(any(), any(), any());
    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test
  public void testContainerCreation() {
    when(mfcContainerService.createTransientContainer(any(), any())).thenReturn(getContainer());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(RECEIVED, null));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());

    ContainerScanRequest containerScanRequest = containerScanRequest(null);
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(mfcContainerService, times(1)).getContainerService();
    verify(containerService, times(1)).publishMultipleContainersToInventory(any(List.class));
    verify(problemRepository, never()).save(any());

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test
  public void testOverageContainerUpdate() {
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(getDefaultProblemLabel());
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"closeException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainer());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);

    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(problemRegistrationService, times(1)).closeProblem(any(), any());
    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test(expectedExceptions = {ReceivingConflictException.class})
  public void testReceivedAgainstSameDelivery() {
    Container container = getContainer();
    container.setDeliveryNumber(123456789L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test(expectedExceptions = {ReceivingConflictException.class})
  public void testPartOfDeliveryButReceivedAgainstOtherDelivery() {
    Container container = getContainer();
    container.setDeliveryNumber(44444444L);
    container.getContainerMiscInfo().put(ORIGINAL_DELIVERY_NUMBER, 123456789L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test(expectedExceptions = {ReceivingConflictException.class})
  public void testReceivedAgainstOriginalDelivery() {
    Container container = getContainer();
    container.setDeliveryNumber(123455555L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test(expectedExceptions = {ReceivingConflictException.class})
  public void testReceivedAgainstOtherDelivery() {
    Container container = getContainer();
    container.setDeliveryNumber(44444444L);
    container.getContainerMiscInfo().put(ORIGINAL_DELIVERY_NUMBER, 123455555);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test
  public void testReceivedAgainstFireflyFlow_secondManualFlow() {
    Container container = getContainer();
    container.setCreateUser("AutoFinalized");
    container.setDeliveryNumber(123456789L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    when(containerTransformer.transform(any())).thenReturn(new ContainerDTO());
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");

    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerTransformer, times(1)).transform(any());
  }

  @Test
  public void testReceivedAgainstFireflyFlow_secondFireflyFlow() {
    Container container = getContainer();
    container.setCreateUser("AutoFinalized");
    container.setDeliveryNumber(123456789L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    when(containerTransformer.transform(any())).thenReturn(new ContainerDTO());
    ContainerScanRequest containerScanRequest = containerScanRequest(null);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.USER_ID_AUTO_FINALIZED);

    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerTransformer, times(1)).transform(any());
  }

  @Test
  public void testReceivedAgainstManualFlow_secondFireflyFlow() {
    Container container = getContainer();
    container.setDeliveryNumber(123456789L);
    when(mfcContainerService.findContainerBySSCC(anyString()))
        .thenReturn(Collections.singletonList(container));
    when(containerTransformer.transform(any())).thenReturn(new ContainerDTO());
    ContainerScanRequest containerScanRequest = containerScanRequest(null);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.USER_ID_AUTO_FINALIZED);

    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(0))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(0)).save(any());
    verify(mfcContainerService, times(0)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(0))
        .createContainer(any(Container.class), any(ASNDocument.class));

    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerTransformer, times(1)).transform(any());
  }

  @Test
  public void testGetMultipleASNDocument() {
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    ContainerScanRequest containerScanRequest =
        containerScanRequestWithMultipleShipments("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(mfcContainerService, times(1)).findContainerBySSCC(anyString());
  }

  @Test
  public void testCalculateWhenFreightIsFoundWithinThreshold() {
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    when(mfcManagedConfig.getPalletFoundAfterUnloadThresholdTimeMinutes()).thenReturn(5);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(getDefaultProblemLabel());
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainer());
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);

    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test
  public void testCalculateWhenFreightIsFoundAfterThreshold() {
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainerForThresholdCalc());
    when(mfcManagedConfig.getPalletFoundAfterUnloadThresholdTimeMinutes()).thenReturn(5);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(getDefaultProblemLabel());
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainerForThresholdCalc());
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");

    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  @Test(expectedExceptions = {ReceivingBadDataException.class})
  public void testInvalidProblemCreateTimestampException() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("dummyIssue");
    problemLabel.setProblemStatus(ProblemType.SHORTAGE.getName());
    problemLabel.setFacilityNum(5504);
    problemLabel.setCreateTs(null);
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    when(mfcManagedConfig.getPalletFoundAfterUnloadThresholdTimeMinutes()).thenReturn(5);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(Optional.of(problemLabel));
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainer());
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
  }

  @Test(expectedExceptions = {ReceivingBadDataException.class})
  public void testOverageContainerUpdateException() {
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(PROBLEM_OV, OVERAGE));
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(getProblemLAbelIncorrectDateFormat());
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainer());
    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(processInitiator, times(2)).initiateProcess(any(), any());
  }

  @Test
  public void testOverageContainerUpdateWhenCreateTsAfterThresholdTS() {
    ContainerDTO containerDTO = getContainerDTO(PROBLEM_OV, OVERAGE);
    containerDTO.getContainerMiscInfo().put(PALLET_TYPE, STORE);
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(containerDTO);
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcContainerService.findBySSCCAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(getContainer());
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), any()))
        .thenReturn(getProblemLabelWithPastCreateTs());
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                "{ \"data\": { \"closeException\": { \"exceptionId\": \"8d8556e6-c116-45e2-b583-b6a3a473ae62\", \"identifier\": \"221122-54553-8025-0000\" }}}",
                HttpStatus.OK));
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(getContainer());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), anyString(), any(Class.class)))
        .thenReturn(problemRegistrationService);

    ContainerScanRequest containerScanRequest = containerScanRequest("overage");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);

    verify(problemRepository, times(1))
        .findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(anyString(), anyString(), any());
    verify(problemRepository, times(1)).save(any());
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(problemRegistrationService, times(1)).closeProblem(any(), any());
    verify(processInitiator, times(2)).initiateProcess(any(), any());
  }

  @Test(expectedExceptions = ReceivingConflictException.class)
  public void testContainerCreationForACompletedDelivery() {

    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor,
        "isMFCPalletAllowedPostDeliveryComplete",
        Boolean.FALSE);
    when(mfcContainerService.createTransientContainer(any(), any())).thenReturn(getContainer());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTO(RECEIVED, null));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(getCompletedStatusDM());

    ContainerScanRequest containerScanRequest = containerScanRequest(null);
    containerScanRequest.setTrackingId("300008760310160015");
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
  }

  @Test
  public void testContainerCreationForPalletPublishingDisabled() {
    when(mfcContainerService.createTransientContainer(any(), any())).thenReturn(getContainer());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(getContainerDTOStoreType(RECEIVED, null));
    when(mfcContainerService.getContainerService()).thenReturn(containerService);
    when(mfcManagedConfig.isProblemRegistrationEnabled()).thenReturn(true);
    doNothing().when(containerService).publishMultipleContainersToInventory(any(List.class));
    when(problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
            anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());

    ContainerScanRequest containerScanRequest = containerScanRequest(null);
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(mfcContainerService, times(1)).createTransientContainer(any(), any());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    verify(mfcContainerService, times(0)).getContainerService();
    verify(containerService, times(0)).publishMultipleContainersToInventory(any(List.class));
    verify(problemRepository, never()).save(any());

    verify(containerItemRepository, times(0)).findByTrackingId(anyString());
    verify(containerTransformer, times(0)).transform(any());
  }

  private Optional<DeliveryMetaData> getCompletedStatusDM() {

    return Optional.of(DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.COMPLETE).build());
  }

  private Optional<ProblemLabel> getProblemLAbelIncorrectDateFormat() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("dummyIssue");
    problemLabel.setProblemStatus(ProblemType.SHORTAGE.getName());
    problemLabel.setFacilityNum(5504);
    problemLabel.setCreateTs(null);
    return Optional.of(problemLabel);
  }

  private Optional<ProblemLabel> getDefaultProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("dummyIssue");
    problemLabel.setProblemStatus(ProblemType.SHORTAGE.getName());
    problemLabel.setFacilityNum(5504);
    problemLabel.setCreateTs(Date.from(Instant.now()));
    return Optional.of(problemLabel);
  }

  private Optional<ProblemLabel> getProblemLabelWithPastCreateTs() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("dummyIssue");
    problemLabel.setProblemStatus(ProblemType.SHORTAGE.getName());
    problemLabel.setFacilityNum(5504);
    problemLabel.setCreateTs(new Date(100, 00, 02, 00, 00, 00));
    return Optional.of(problemLabel);
  }

  private ContainerScanRequest containerScanRequest(String type) {
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(getASNDocument())
            .deliveryNumber(123456789L)
            .originalDeliveryNumber(123456789L)
            .trackingId("1234567890986756")
            .loadNumber("load1234")
            .trailerNumber("tlr12345")
            .build();

    if (Objects.nonNull(type) && StringUtils.equalsIgnoreCase("overage", type)) {
      containerScanRequest.setOverageType(OverageType.UKNOWN);
      containerScanRequest.setOriginalDeliveryNumber(123455555L);
    }
    return containerScanRequest;
  }

  private ContainerScanRequest containerScanRequestWithMultipleShipments(String type) {
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(getASNDocumentWithMultipleShipments())
            .deliveryNumber(123456789L)
            .originalDeliveryNumber(123456789L)
            .trackingId("1234567890986756")
            .loadNumber("load1234")
            .trailerNumber("tlr12345")
            .build();

    if (Objects.nonNull(type) && StringUtils.equalsIgnoreCase("overage", type)) {
      containerScanRequest.setOverageType(OverageType.UKNOWN);
      containerScanRequest.setOriginalDeliveryNumber(123455555L);
    }
    return containerScanRequest;
  }

  private ContainerScanRequest containerScanRequestWithoutOriginalDeliveryNumber(String type) {
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(getASNDocument())
            .deliveryNumber(123456789L)
            .trackingId("1234567890986756")
            .loadNumber("load1234")
            .trailerNumber("tlr12345")
            .build();

    if (Objects.nonNull(type) && StringUtils.equalsIgnoreCase("overage", type)) {
      containerScanRequest.setOverageType(OverageType.UKNOWN);
    }
    return containerScanRequest;
  }

  private ContainerDTO getContainerDTO(String status, String operation) {
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put(OPERATION_TYPE, operation);
    return ContainerDTO.builder()
        .trackingId("9876543210")
        .deliveryNumber(123456789L)
        .ssccNumber("dummySSCC")
        .containerMiscInfo(miscInfo)
        .containerItems(new ArrayList<>())
        .containerStatus(status)
        .shipmentId("1234567890")
        .build();
  }

  private ContainerDTO getContainerDTOStoreType(String status, String operation) {
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put(OPERATION_TYPE, operation);
    miscInfo.put(PALLET_TYPE, "STORE");
    return ContainerDTO.builder()
        .trackingId("9876543210")
        .deliveryNumber(123456789L)
        .ssccNumber("dummySSCC")
        .containerMiscInfo(miscInfo)
        .containerItems(new ArrayList<>())
        .containerStatus(status)
        .shipmentId("1234567890")
        .build();
  }

  private ASNDocument getASNDocument() {
    return MFCTestUtils.getASNDocument(
        "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
  }

  private Container getContainer() {
    return MFCTestUtils.getContainer(
        "../../receiving-test/src/main/resources/json/mfc/MFCContainer2.json");
  }

  private ASNDocument getASNDocumentWithMultipleShipments() {
    return MFCTestUtils.getASNDocument(
        "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMulitpleShipments.json");
  }

  private Container getContainerForThresholdCalc() {
    return MFCTestUtils.getContainer(
        "../../receiving-test/src/main/resources/json/mfc/MFCContainer3.json");
  }
}
