package com.walmart.move.nim.receiving.rc.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.ItemTrackerRequest;
import com.walmart.move.nim.receiving.core.service.ItemTrackerService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRcManagedConfig;
import com.walmart.move.nim.receiving.rc.config.RcManagedConfig;
import com.walmart.move.nim.receiving.rc.contants.ActionType;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerDetails;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateReturnOrderDataRequest;
import com.walmart.move.nim.receiving.rc.repositories.ContainerRLogRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowItemRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowRepository;
import com.walmart.move.nim.receiving.rc.transformer.ReceivingWorkflowTransformer;
import com.walmart.move.nim.receiving.rc.util.ReceivingWorkflowUtil;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcContainerServiceTest extends ReceivingTestBase {
  @InjectMocks private RcContainerService rcContainerService;
  @Mock private ContainerRLogRepository containerRLogRepository;

  @Mock private ReceivingWorkflowItemRepository receivingWorkflowItemRepository;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private ItemTrackerService itemTrackerService;
  @Mock private RcManagedConfig rcManagedConfig;
  @Mock private ReceivingWorkflowTransformer receivingWorkflowTransformer;
  @Mock private RcWorkflowService rcWorkflowService;
  @Mock private ReceivingWorkflowRepository receivingWorkflowRepository;
  @Spy private ReceivingWorkflowUtil receivingWorkflowUtil;

  @Mock private MaasTopics masstopics;
  private Gson gson;
  private ReceiveContainerRequest receiveContainerRequest;
  private ReceiveContainerRequest receiveContainerRequestWithoutSalesOrder;
  private ReceiveContainerRequest receiveContainerRequestWithoutItemDetails;
  private ReceiveContainerRequest receiveContainerRequestWithoutItemUPCAndCaseUPCForRTV;
  private ReceiveContainerRequest receiveContainerRequestWithoutItemUPCAndCaseUPCForRestock;
  private ReceiveContainerRequest receiveContainerRequestWithoutItemUPCAndCaseUPCForDispose;
  private ReceiveContainerRequest
      receiveContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter;
  private ReceiveContainerRequest receiveContainerRequestWithOnlyOneItemDescription;
  private ReceiveContainerRequest receiveContainerRequestWithoutReturnTrackingNumber;
  private ReceiveContainerRequest receiveContainerRequestWithoutDispositionType;
  private ReceiveContainerRequest receiveContainerRequestWithoutProposedDispositionType;
  private ReceiveContainerRequest receiveContainerRequestWithoutFinalDispositionType;
  private ReceiveContainerRequest receiveContainerRequestForSerialNumberMismatch;
  private ReceiveContainerRequest receiveContainerRequestForReprintGtinLabel;
  private ReceiveContainerRequest receiveContainerRequestWithoutContainerTagForRestock;
  private ReceiveContainerRequest receiveContainerRequestWithoutContainerTagForRTV;
  private ReceiveContainerRequest receiveContainerRequestWithRegulatedItemInfo;
  private ReceiveContainerRequest receiveContainerRequestWithSerialNumbers;
  private ReceiveContainerRequest receiveContainerRequestForPotentialFraud;
  private ReceiveContainerRequest receiveContainerRequestWithTrackingId;

  private ReceiveContainerRequest receiveContainerRequestWithQuestions;

  private ReceiveContainerRequest receiveContainerRequestWithSplitLine;
  private ReceiveContainerRequest receiveContainerRequestMismatchLineNumber;

  private UpdateContainerRequest updateContainerRequest;
  private UpdateContainerRequest updateContainerRequestForDispose;
  private UpdateContainerRequest updateContainerRequestWithoutContainerTagForRestock;
  private UpdateContainerRequest updateContainerRequestWithoutContainerTagForRTV;

  private UpdateContainerRequest updateContainerRequestWithQuestions;

  private UpdateContainerRequest updateContainerRequestWithFraudDisposition;

  private UpdateReturnOrderDataRequest updateReturnOrderDataRequest;

  private ContainerRLog containerRLog,
      containerRLogForPurgeTest,
      containerRLogForPurgeTestNotToBeDeleted,
      containerRLogWithSplitLine,
      containerRLogWithReturnOrderAsNull,
      containerRLogWithReturnOrderPresentAfterAutoReturn;
  private ItemTracker itemTrackerSerialNumberMismatch;
  private ItemTracker itemTrackerSerialNumberMatched;
  private ItemTracker itemTrackerReprintGtinLabel;
  private ItemTracker itemTrackerSerialNumberMissing;
  private PageRequest pageReq;
  private PurgeData purgeData;

  @BeforeClass
  public void initMocksAndFields() throws IOException {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(rcContainerService, "gson", gson);
    TenantContext.setFacilityNum(9074);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    pageReq = PageRequest.of(0, 10);
    purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.CONTAINER_RLOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    String dataPathContainerRequest =
        new File("../../receiving-test/src/main/resources/json/RcReceiveContainerRequest.json")
            .getCanonicalPath();
    receiveContainerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequest))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithoutSalesOrder =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutSalesOrder.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutSalesOrder =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithoutSalesOrder))),
            ReceiveContainerRequest.class);
    String dataPathContainerRequestWithoutItemDetails =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutItemDeatils.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutItemDetails =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithoutItemDetails))),
            ReceiveContainerRequest.class);
    String dataPathContainerRequestWithoutItemUPCAndCaseUPCForRTV =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutItemUPCAndCaseUPCForRTV.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutItemUPCAndCaseUPCForRTV =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerRequestWithoutItemUPCAndCaseUPCForRTV))),
            ReceiveContainerRequest.class);
    String dataPathContainerRequestWithoutItemUPCAndCaseUPCForRestock =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutItemUPCAndCaseUPCForRestock.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutItemUPCAndCaseUPCForRestock =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerRequestWithoutItemUPCAndCaseUPCForRestock))),
            ReceiveContainerRequest.class);
    String dataPathContainerRequestWithoutItemUPCAndCaseUPCForDispose =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutItemUPCAndCaseUPCForDispose.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutItemUPCAndCaseUPCForDispose =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerRequestWithoutItemUPCAndCaseUPCForDispose))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter.json")
            .getCanonicalPath();
    receiveContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        dataPathContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithOnlyOneItemDescription =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithOnlyOneItemDescription.json")
            .getCanonicalPath();
    receiveContainerRequestWithOnlyOneItemDescription =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithOnlyOneItemDescription))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithoutReturnTrackingNumber =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutReturnTrackingNumber.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutReturnTrackingNumber =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithoutReturnTrackingNumber))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithoutDispositionType =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutDispositionType.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutDispositionType =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithoutDispositionType))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithoutProposedDispositionType =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutProposedDispositionType.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutProposedDispositionType =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerRequestWithoutProposedDispositionType))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithoutFinalDispositionType =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutFinalDispositionType.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutFinalDispositionType =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithoutFinalDispositionType))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestForNoUpcLabel =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestForSerialNumberMismatch.json")
            .getCanonicalPath();
    receiveContainerRequestForSerialNumberMismatch =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestForNoUpcLabel))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestForReprintGtinLabel =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestForReprintGtinLabel.json")
            .getCanonicalPath();
    receiveContainerRequestForReprintGtinLabel =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestForReprintGtinLabel))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithNoContainerTagForRestock =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutContainerTagForRestock.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutContainerTagForRestock =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerRequestWithNoContainerTagForRestock))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithNoContainerTagForRtv =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithoutContainerTagForRTV.json")
            .getCanonicalPath();
    receiveContainerRequestWithoutContainerTagForRTV =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithNoContainerTagForRtv))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithRegulatedItemInfo =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithRegulatedItemInfo.json")
            .getCanonicalPath();
    receiveContainerRequestWithRegulatedItemInfo =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathContainerRequestWithRegulatedItemInfo))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithSerialNumbers =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithSerialNumbers.json")
            .getCanonicalPath();
    receiveContainerRequestWithSerialNumbers =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithSerialNumbers))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestForPotentialFraud =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestForPotentialFraud.json")
            .getCanonicalPath();
    receiveContainerRequestForPotentialFraud =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestForPotentialFraud))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithTrackingId =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithTrackingId.json")
            .getCanonicalPath();
    receiveContainerRequestWithTrackingId =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithTrackingId))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestMismatchLineNumber =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestMismatchLineNumber.json")
            .getCanonicalPath();
    receiveContainerRequestMismatchLineNumber =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestMismatchLineNumber))),
            ReceiveContainerRequest.class);

    String dataPathUpdateContainerRequest =
        new File("../../receiving-test/src/main/resources/json/RcUpdateContainerRequest.json")
            .getCanonicalPath();
    updateContainerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateContainerRequest))),
            UpdateContainerRequest.class);

    String dataPathUpdateContainerRequestForDispose =
        new File(
                "../../receiving-test/src/main/resources/json/RcUpdateContainerRequestForDispose.json")
            .getCanonicalPath();
    updateContainerRequestForDispose =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateContainerRequestForDispose))),
            UpdateContainerRequest.class);
    String dataPathUpdateContainerRequestWithQuestions =
        new File(
                "../../receiving-test/src/main/resources/json/RcUpdateContainerRequestWithQuestions.json")
            .getCanonicalPath();
    updateContainerRequestWithQuestions =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateContainerRequestWithQuestions))),
            UpdateContainerRequest.class);

    String dataPathUpdateRequestWithoutContainerTagRestock =
        new File(
                "../../receiving-test/src/main/resources/json/RcUpdateContainerRequestWithoutContainerTagRestock.json")
            .getCanonicalPath();
    updateContainerRequestWithoutContainerTagForRestock =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathUpdateRequestWithoutContainerTagRestock))),
            UpdateContainerRequest.class);

    String dataPathUpdateRequestWithoutContainerTagRTV =
        new File(
                "../../receiving-test/src/main/resources/json/RcUpdateContainerRequestWithoutContainerTagRTV.json")
            .getCanonicalPath();
    updateContainerRequestWithoutContainerTagForRTV =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateRequestWithoutContainerTagRTV))),
            UpdateContainerRequest.class);

    String dataPathContainer =
        new File("../../receiving-test/src/main/resources/json/RcContainer.json")
            .getCanonicalPath();
    containerRLog =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainer))), ContainerRLog.class);
    containerRLogForPurgeTest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainer))), ContainerRLog.class);
    containerRLogForPurgeTestNotToBeDeleted =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainer))), ContainerRLog.class);
    String dataPathItemTrackerReprintGtinLabel =
        new File("../../receiving-test/src/main/resources/json/ItemTrackerReprintGtinLabel.json")
            .getCanonicalPath();
    String dataPathContainerRequestWithQuestions =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithQuestions.json")
            .getCanonicalPath();
    receiveContainerRequestWithQuestions =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithQuestions))),
            ReceiveContainerRequest.class);

    String dataPathContainerRequestWithSplitLine =
        new File(
                "../../receiving-test/src/main/resources/json/RcReceiveContainerRequestWithSplitLineDetails.json")
            .getCanonicalPath();
    receiveContainerRequestWithSplitLine =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerRequestWithSplitLine))),
            ReceiveContainerRequest.class);
    String dataPathContainerWithSplitLine =
        new File("../../receiving-test/src/main/resources/json/RcContainerWithSplitLine.json")
            .getCanonicalPath();
    containerRLogWithSplitLine =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerWithSplitLine))),
            ContainerRLog.class);

    itemTrackerReprintGtinLabel =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerReprintGtinLabel))),
            ItemTracker.class);
    String dataPathItemTrackerSerialNumberMatched =
        new File("../../receiving-test/src/main/resources/json/ItemTrackerSerialNumberMatched.json")
            .getCanonicalPath();
    itemTrackerSerialNumberMatched =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerSerialNumberMatched))),
            ItemTracker.class);
    String dataPathItemTrackerSerialNumberMismatch =
        new File(
                "../../receiving-test/src/main/resources/json/ItemTrackerSerialNumberMismatch.json")
            .getCanonicalPath();
    itemTrackerSerialNumberMismatch =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerSerialNumberMismatch))),
            ItemTracker.class);
    String dataPathItemTrackerSerialNumberMissing =
        new File("../../receiving-test/src/main/resources/json/ItemTrackerSerialNumberMissing.json")
            .getCanonicalPath();
    itemTrackerSerialNumberMissing =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerSerialNumberMissing))),
            ItemTracker.class);
    String dataPathUpdateContainerRequestWithFraudDisposition =
        new File(
                "../../receiving-test/src/main/resources/json/RcUpdateContainerRequestWithFraudDisposition.json")
            .getCanonicalPath();
    updateContainerRequestWithFraudDisposition =
        gson.fromJson(
            new String(
                Files.readAllBytes(Paths.get(dataPathUpdateContainerRequestWithFraudDisposition))),
            UpdateContainerRequest.class);
    String dataPathUpdateReturnOrderDataRequest =
        new File("../../receiving-test/src/main/resources/json/RcUpdateReturnOrderDataRequest.json")
            .getCanonicalPath();
    updateReturnOrderDataRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathUpdateReturnOrderDataRequest))),
            UpdateReturnOrderDataRequest.class);
    String dataPathContainerWhenReturnOrderNull =
        new File("../../receiving-test/src/main/resources/json/RcContainerWithoutReturnOrder.json")
            .getCanonicalPath();
    containerRLogWithReturnOrderAsNull =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathContainerWhenReturnOrderNull))),
            ContainerRLog.class);
    String dataPathContainerWhenReturnOrderPresentAfterAutoReturn =
        new File(
                "../../receiving-test/src/main/resources/json/RcContainerWithReturnOrderPresentAfterAutoReturn.json")
            .getCanonicalPath();
    containerRLogWithReturnOrderPresentAfterAutoReturn =
        gson.fromJson(
            new String(
                Files.readAllBytes(
                    Paths.get(dataPathContainerWhenReturnOrderPresentAfterAutoReturn))),
            ContainerRLog.class);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(containerRLogRepository);
    Mockito.reset(jmsPublisher);
    Mockito.reset(lpnCacheService);
    Mockito.reset(itemTrackerService);
    Mockito.reset(rcManagedConfig);
    Mockito.reset(rcWorkflowService);
    Mockito.reset(receivingWorkflowTransformer);
    Mockito.reset(receivingWorkflowRepository);
  }

  @Test
  public void testReceiveContainer() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.findBySalesOrderNumberAndSalesOrderLineNumber(
            "5512098217046", Integer.valueOf(1)))
        .thenReturn(Collections.singletonList(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
    assertEquals(response.getContainerRLog().getReturnOrderLineNumber(), Integer.valueOf(1));
    assertEquals(response.getContainerRLog().getSalesOrderLineNumber(), Integer.valueOf(1));
  }

  @Test
  public void testReceiveContainerMismatchLineNumber() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.findBySalesOrderNumberAndSalesOrderLineNumber(
            "5512098217046", Integer.valueOf(1)))
        .thenReturn(Collections.singletonList(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestMismatchLineNumber, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
    assertEquals(response.getContainerRLog().getReturnOrderLineNumber(), Integer.valueOf(1));
    assertEquals(response.getContainerRLog().getSalesOrderLineNumber(), Integer.valueOf(1));
  }

  @Test
  public void testReceiveContainerWithSplitLineDetails() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class)))
        .thenReturn(containerRLogWithSplitLine);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestWithSplitLine, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
    assertEquals(response.getContainerRLog().getReturnOrderLineNumber(), Integer.valueOf(1));
    assertEquals(response.getContainerRLog().getSalesOrderLineNumber(), Integer.valueOf(10001));
  }

  @Test
  public void testReceiveContainerForReprintGtinLabel() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestForReprintGtinLabel, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(1)).trackItems(any());
  }

  @Test
  public void testReceiveContainerForSerialNumberMismatch() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestForSerialNumberMismatch, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(1)).trackItems(anyList());
  }

  @Test
  public void testReceiveContainerWithItemDescriptionLessThanMaxAllowedCharacter() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithItemDescriptionLessThanMaxAllowedCharacter,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithOnlyOneItemDescription() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithOnlyOneItemDescription,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutSalesOrder() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutSalesOrder, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutItemDetails() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutItemDetails, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutItemUPCAndCaseUPCForRTV() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutItemUPCAndCaseUPCForRTV,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutItemUPCAndCaseUPCForRestock() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutItemUPCAndCaseUPCForRestock,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutItemUPCAndCaseUPCForDispose() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutItemUPCAndCaseUPCForDispose,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "LPNs are currently not available")
  public void testReceiveContainerWithNoLpn() {
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn(null);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithoutReturnTrackingNumberForItemNotExpectedBack() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutReturnTrackingNumber,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithRegulatedItemInfo() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any()))
        .thenAnswer((Answer) invocation -> invocation.getArguments()[0]);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestWithRegulatedItemInfo, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
    assertEquals(response.getContainerRLog().getRegulatedItemType(), "LITHIUM");
    assertEquals(response.getContainerRLog().getRegulatedItemLabelCode(), "UN3480");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be created without disposition type.")
  public void testReceiveContainerWithoutProposedDisposition() {
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutProposedDispositionType,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be created without disposition type.")
  public void testReceiveContainerWithoutFinalDisposition() {
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutFinalDispositionType,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be created without disposition type.")
  public void testReceiveContainerWithoutDispositionType() {
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutDispositionType, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be created without container tag.")
  public void testReceiveContainerWithoutContainerTagForRtv() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutContainerTagForRTV, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be created without container tag.")
  public void testReceiveContainerWithoutContainerTagForRestock() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequestWithoutContainerTagForRestock,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Missing destination parent container type in CCM config for dispositionType = RTV")
  public void testReceiveContainerWithDestinationParentContainerTypeAsNull() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE_AS_NULL);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Missing destination parent container type in CCM config for dispositionType = RTV")
  public void testReceiveContainerWithDestinationParentContainerTypeEmptyObject() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE_AS_EMPTY_OBJECT);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Missing destination parent container type in CCM config for dispositionType = RTV")
  public void
      testReceiveContainerWithDestinationParentContainerTypeHavingDifferentFacilityConfiguration() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(
            MockRcManagedConfig
                .DESTINATION_PARENT_CONTAINER_TYPE_HAVING_DIFFERENT_FACILITY_CONFIGURATION);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Missing destination parent container type in CCM config for dispositionType = RTV")
  public void
      testReceiveContainerWithDestinationParentContainerTypeHavingFacilityConfigurationAsEmptyObject() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(
            MockRcManagedConfig
                .DESTINATION_PARENT_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_AS_EMPTY_OBJECT);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Missing destination parent container type in CCM config for dispositionType = RTV")
  public void
      testReceiveContainerWithDestinationParentContainerTypeHavingFacilityConfigurationWithMissingDispositionType() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(
            MockRcManagedConfig
                .DESTINATION_PARENT_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_WITH_MISSING_DISPOSITION_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithDestinationContainerTypeAsNull() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE_AS_NULL);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithDestinationContainerTypeEmptyObject() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE_AS_EMPTY_OBJECT);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void
      testReceiveContainerWithDestinationContainerTypeHavingDifferentFacilityConfiguration() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(
            MockRcManagedConfig.DESTINATION_CONTAINER_TYPE_HAVING_DIFFERENT_FACILITY_CONFIGURATION);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void
      testReceiveContainerWithDestinationContainerTypeHavingFacilityConfigurationAsEmptyObject() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(
            MockRcManagedConfig
                .DESTINATION_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_AS_EMPTY_OBJECT);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void
      testReceiveContainerWithDestinationContainerTypeHavingFacilityConfigurationWithMissingDispositionTypeRtv() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(
            MockRcManagedConfig
                .DESTINATION_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_WITH_MISSING_DISPOSITION_TYPE_RTV);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test
  public void testReceiveContainerWithSerialNumbers() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any()))
        .thenAnswer((Answer) invocation -> invocation.getArguments()[0]);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestWithSerialNumbers, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(1)).trackItems(any());
    assertEquals(response.getContainerRLog().getScannedSerialNumber(), "SR-1N231A");
    assertEquals(
        response.getContainerRLog().getExpectedSerialNumbers(),
        Arrays.asList("SR-1N231A", "SR-2J903H"));
  }

  @Test
  public void testReceiveContainerForPotentialFraud() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any()))
        .thenAnswer((Answer) invocation -> invocation.getArguments()[0]);
    when(rcWorkflowService.createWorkflow(any(), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    when(receivingWorkflowTransformer.transformContainerToWorkflowRequest(
            any(), any(), anyString()))
        .thenReturn(RcWorkflowCreateRequest.builder().build());
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestForPotentialFraud, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));

    verify(rcWorkflowService, times(1)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(1))
        .transformContainerToWorkflowRequest(any(), any(), any());

    Assert.assertEquals(response.getReceivingWorkflow().getId(), new Long(10001));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Container with potential fraud disposition cannot be created without workflowId")
  public void testReceiveContainerForPotentialFraud_missingWorkflowId() {
    ReceiveContainerRequest requestWithoutCreateReason =
        gson.fromJson(
            gson.toJson(receiveContainerRequestForPotentialFraud), ReceiveContainerRequest.class);
    requestWithoutCreateReason.setWorkflowId(null);
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            requestWithoutCreateReason, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(rcWorkflowService, times(0)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(0))
        .transformContainerToWorkflowRequest(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Container with potential fraud disposition cannot be created without workflowCreateReason")
  public void testReceiveContainerForPotentialFraud_missingWorkflowCreateReason() {
    ReceiveContainerRequest requestWithoutCreateReason =
        gson.fromJson(
            gson.toJson(receiveContainerRequestForPotentialFraud), ReceiveContainerRequest.class);
    requestWithoutCreateReason.setWorkflowCreateReason(null);
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            requestWithoutCreateReason, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(rcWorkflowService, times(0)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(0))
        .transformContainerToWorkflowRequest(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Container with potential fraud disposition cannot be created without scannedLabel")
  public void testReceiveContainerForPotentialFraud_missingPackageLabel() {
    ReceiveContainerRequest requestWithoutPackageLabel =
        gson.fromJson(
            gson.toJson(receiveContainerRequestForPotentialFraud), ReceiveContainerRequest.class);
    requestWithoutPackageLabel.setScannedLabel(null);
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            requestWithoutPackageLabel, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(rcWorkflowService, times(0)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(0))
        .transformContainerToWorkflowRequest(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Container with potential fraud disposition cannot be created without scannedItemLabel")
  public void testReceiveContainerForPotentialFraud_missingItemLabel() {
    ReceiveContainerRequest requestWithoutItemLabel =
        gson.fromJson(
            gson.toJson(receiveContainerRequestForPotentialFraud), ReceiveContainerRequest.class);
    requestWithoutItemLabel.setScannedItemLabel(null);
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            requestWithoutItemLabel, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
    verify(rcWorkflowService, times(0)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(0))
        .transformContainerToWorkflowRequest(any(), any(), any());
  }

  @Test
  public void testReceiveContainerWithTrackingId() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString())).thenReturn(Optional.empty());
    when(containerRLogRepository.save(any())).thenReturn(containerRLog);
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestWithTrackingId, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).findByTrackingId(anyString());
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
  }

  @Test
  public void testReceiveContainerWithQuestions() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class)))
        .thenReturn("b090740000200000000679908");
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.receiveContainer(
        receiveContainerRequestWithQuestions, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(itemTrackerService, times(0)).trackItem(any(ItemTrackerRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Container already exists for Tracking Id=e090740001000200030004")
  public void testReceiveContainerWithTrackingId_containerAlreadyExists() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(new ContainerRLog()));
    RcContainerDetails response =
        rcContainerService.receiveContainer(
            receiveContainerRequestWithTrackingId, MockHttpHeaders.getHeaders("9074", "US"));
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(containerRLogRepository, times(1)).findByTrackingId(anyString());
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
  }

  @Test
  public void testUpdateContainer() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequest,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
  }

  @Test
  public void testUpdateContainerForDispose() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequestForDispose,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
  }

  @Test
  public void testUpdateContainerWithQuestions() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);

    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequestWithQuestions,
        MockHttpHeaders.getHeaders("9074", "US"));

    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be updated without container tag.")
  public void testUpdateContainerWithoutContainerTagForRestock() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequestWithoutContainerTagForRestock,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Container cannot be updated without container tag.")
  public void testUpdateContainerWithoutContainerTagForRTV() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequestWithoutContainerTagForRTV,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for Tracking Id=b090740000200000000679908")
  public void testUpdateContainerNotFound() {
    when(containerRLogRepository.findByTrackingId(anyString())).thenReturn(Optional.empty());
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequest,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(0)).save(any(ContainerRLog.class));
  }

  @Test
  public void testPublishContainer() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    rcContainerService.publishContainer(
        RcContainerDetails.builder().containerRLog(containerRLog).build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishContainerMissingreturnInitiatedFalse() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    containerRLog.setIsMissingReturnInitiated(false);
    containerRLog.setReturnOrderNumber(null);
    rcContainerService.publishContainer(
        RcContainerDetails.builder().containerRLog(containerRLog).build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishContainerForSerialNumberMatched() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    rcContainerService.publishContainer(
        RcContainerDetails.builder()
            .containerRLog(containerRLog)
            .itemTrackers(Collections.singletonList(itemTrackerSerialNumberMatched))
            .build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishContainerForSerialNumberMismatch() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    rcContainerService.publishContainer(
        RcContainerDetails.builder()
            .containerRLog(containerRLog)
            .itemTrackers(Collections.singletonList(itemTrackerSerialNumberMismatch))
            .build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishContainerForSerialNumberMissing() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    rcContainerService.publishContainer(
        RcContainerDetails.builder()
            .containerRLog(containerRLog)
            .itemTrackers(Collections.singletonList(itemTrackerSerialNumberMissing))
            .build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishContainerForReprintGtinLabel() {
    when(masstopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    rcContainerService.publishContainer(
        RcContainerDetails.builder()
            .containerRLog(containerRLog)
            .itemTrackers(Collections.singletonList(itemTrackerReprintGtinLabel))
            .build(),
        MockHttpHeaders.getHeaders("9074", "US"),
        ActionType.RECEIPT,
        false,
        "false",
        "false");
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testDeleteContainerByPackageBarcodeValue() {
    when(containerRLogRepository.findByPackageBarCodeValue(anyString()))
        .thenReturn(Collections.singletonList(containerRLog));
    doNothing().when(containerRLogRepository).deleteByPackageBarCodeValue(anyString());
    rcContainerService.deleteContainersByPackageBarcode("5512098217046");
    verify(containerRLogRepository, times(1)).findByPackageBarCodeValue(anyString());
    verify(containerRLogRepository, times(1)).deleteByPackageBarCodeValue(anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Container not found for packageBarcodeValue=5512098217046")
  public void testDeleteContainerByPackageBarcodeValueNotFound() {
    when(containerRLogRepository.findByPackageBarCodeValue(anyString()))
        .thenReturn(Collections.emptyList());
    rcContainerService.deleteContainersByPackageBarcode("5512098217046");
    verify(containerRLogRepository, times(1)).findByPackageBarCodeValue(anyString());
    verify(containerRLogRepository, times(0)).deleteByPackageBarCodeValue(anyString());
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    containerRLogForPurgeTest.setCreateTs(cal.getTime());

    when(containerRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(containerRLogForPurgeTest));
    doNothing().when(containerRLogRepository).deleteAll();
    long lastDeletedId = rcContainerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    containerRLogForPurgeTest.setCreateTs(cal.getTime());

    when(containerRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(containerRLogForPurgeTest));
    doNothing().when(containerRLogRepository).deleteAll();
    long lastDeletedId = rcContainerService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    cal1.add(Calendar.HOUR, -60 * 24);
    containerRLogForPurgeTest.setCreateTs(cal1.getTime());
    cal2.add(Calendar.HOUR, -1 * 24);
    containerRLogForPurgeTestNotToBeDeleted.setId(2L);
    containerRLogForPurgeTestNotToBeDeleted.setCreateTs(cal2.getTime());

    when(containerRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(
            Arrays.asList(containerRLogForPurgeTest, containerRLogForPurgeTestNotToBeDeleted));
    doNothing().when(containerRLogRepository).deleteAll();
    long lastDeletedId = rcContainerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testGetLatestReceivedContainerByGtin() {
    when(containerRLogRepository.findFirstByGtinOrderByCreateTsDesc(anyString()))
        .thenReturn(Optional.of(containerRLog));
    ContainerRLog containerRLogActual =
        rcContainerService.getLatestReceivedContainerByGtin("00604015693198", null);
    assertEquals(containerRLogActual, containerRLog);
    verify(containerRLogRepository, times(1)).findFirstByGtinOrderByCreateTsDesc(anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Container not found by gtin=00604015693198")
  public void testGetLatestReceivedContainerByGtin_NotFound() {
    when(containerRLogRepository.findFirstByGtinOrderByCreateTsDesc(anyString()))
        .thenReturn(Optional.empty());
    ContainerRLog containerRLogActual =
        rcContainerService.getLatestReceivedContainerByGtin("00604015693198", null);
    verify(containerRLogRepository, times(1)).findFirstByGtinOrderByCreateTsDesc(anyString());
  }

  @Test
  public void testGetLatestReceivedContainerByGtinAndDispositionType() {
    when(containerRLogRepository.findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(
            anyString(), anyString()))
        .thenReturn(Optional.of(containerRLog));
    ContainerRLog containerRLogActual =
        rcContainerService.getLatestReceivedContainerByGtin("00604015693198", "RTV");
    assertEquals(containerRLogActual, containerRLog);
    verify(containerRLogRepository, times(1))
        .findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(anyString(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found by gtin=00604015693198 for disposition type=RESTOCK")
  public void testGetLatestReceivedContainerByGtinAndDispositionType_NotFound() {
    when(containerRLogRepository.findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(
            anyString(), anyString()))
        .thenReturn(Optional.empty());
    ContainerRLog containerRLogActual =
        rcContainerService.getLatestReceivedContainerByGtin("00604015693198", "RESTOCK");
    verify(containerRLogRepository, times(1))
        .findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(anyString(), anyString());
  }

  @Test
  public void testGetLatestReceivedContainerByTrackingId() {
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    ContainerRLog containerRLogActual =
        rcContainerService.getReceivedContainerByTrackingId("b090740000100000001352859");
    assertEquals(containerRLogActual, containerRLog);
    verify(containerRLogRepository, times(1)).findByTrackingId(anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for Tracking Id=b090740000100000001352859")
  public void testGetLatestReceivedContainerByTrackingId_NotFound() {
    when(containerRLogRepository.findByTrackingId(anyString())).thenReturn(Optional.empty());
    ContainerRLog containerRLogActual =
        rcContainerService.getReceivedContainerByTrackingId("b090740000100000001352859");
    verify(containerRLogRepository, times(1)).findByTrackingId(anyString());
  }

  @Test
  public void testGetLatestReceivedContainersByPackageBarCode() {
    when(containerRLogRepository.findByPackageBarCodeValue(anyString()))
        .thenReturn(Collections.singletonList(containerRLog));
    List<ContainerRLog> containerRLogList =
        rcContainerService.getReceivedContainersByPackageBarCode("ba12cd456000100000001352859");
    assertEquals(containerRLogList, Collections.singletonList(containerRLog));
    verify(containerRLogRepository, times(1)).findByPackageBarCodeValue(anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for packageBarcodeValue=ba12cd456000100000001234567")
  public void testGetLatestReceivedContainersByPackageBarCode_NotFound() {
    when(containerRLogRepository.findByPackageBarCodeValue(anyString()))
        .thenReturn(Collections.emptyList());
    List<ContainerRLog> containerRLogList =
        rcContainerService.getReceivedContainersByPackageBarCode("ba12cd456000100000001234567");
    verify(containerRLogRepository, times(1)).findByPackageBarCodeValue(anyString());
  }

  @Test
  public void testGetLatestReceivedContainersBySalesOrderNumber_WorkflowItemExists() {
    ContainerRLog containerRLog = new ContainerRLog();
    containerRLog.setTrackingId("trackingId1");
    when(containerRLogRepository.findBySalesOrderNumber(anyString()))
        .thenReturn(Collections.singletonList(containerRLog));
    ReceivingWorkflowItem receivingWorkflowItem = mock(ReceivingWorkflowItem.class);
    ReceivingWorkflow receivingWorkflow = mock(ReceivingWorkflow.class);
    when(receivingWorkflowItem.getItemTrackingId()).thenReturn("trackingId1");
    when(receivingWorkflowItem.getReceivingWorkflow()).thenReturn(receivingWorkflow);
    when(receivingWorkflow.getWorkflowId()).thenReturn("workflowId1");

    when(receivingWorkflowItemRepository.findByItemTrackingIdIn(anyList()))
        .thenReturn(Collections.singletonList(receivingWorkflowItem));
    List<ContainerRLog> containerRLogList =
        rcContainerService.getReceivedContainersBySoNumber("ba12cd456000100000001352859");
    assertNotNull(containerRLogList);
    assertEquals(1, containerRLogList.size());
    assertEquals("workflowId1", containerRLogList.get(0).getWorkFlowId());
    verify(containerRLogRepository, times(1)).findBySalesOrderNumber(anyString());
    verify(receivingWorkflowItemRepository, times(1)).findByItemTrackingIdIn(anyList());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for salesOrderNumber=ba12cd456000100000001234567")
  public void testGetLatestReceivedContainersBySalesOrderNumber_NotFound() {
    when(containerRLogRepository.findBySalesOrderNumber(anyString()))
        .thenReturn(Collections.emptyList());
    List<ContainerRLog> containerRLogList =
        rcContainerService.getReceivedContainersBySoNumber("ba12cd456000100000001234567");
    verify(containerRLogRepository, times(1)).findBySalesOrderNumber(anyString());
  }

  @Test
  public void testUpdateContainerWithFraudDisposition() {
    when(rcManagedConfig.getDestinationParentContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_PARENT_CONTAINER_TYPE);
    when(rcManagedConfig.getDestinationContainerType())
        .thenReturn(MockRcManagedConfig.DESTINATION_CONTAINER_TYPE);
    when(containerRLogRepository.findByTrackingId(anyString()))
        .thenReturn(Optional.of(containerRLog));
    when(containerRLogRepository.save(any(ContainerRLog.class))).thenReturn(containerRLog);
    when(rcWorkflowService.createWorkflow(any(), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    when(receivingWorkflowTransformer.transformContainerToWorkflowRequest(
            any(), any(), anyString()))
        .thenReturn(RcWorkflowCreateRequest.builder().build());
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(any())).thenReturn(null);
    rcContainerService.updateContainer(
        "b090740000200000000679908",
        updateContainerRequestWithFraudDisposition,
        MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1)).findByTrackingId(any(String.class));
    verify(containerRLogRepository, times(1)).save(any(ContainerRLog.class));
    verify(rcWorkflowService, times(1)).createWorkflow(any(), any(), any());
    verify(receivingWorkflowTransformer, times(1))
        .transformContainerToWorkflowRequest(any(), any(), any());
    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(any());
  }

  @Test
  public void updateReturnOrderDataTest() {

    when(containerRLogRepository.findByTrackingId(updateReturnOrderDataRequest.getRcTrackingId()))
        .thenReturn(Optional.of(containerRLogWithReturnOrderAsNull));
    doNothing()
        .when(containerRLogRepository)
        .updateReturnOrderData(
            updateReturnOrderDataRequest.getRoNumber(),
            updateReturnOrderDataRequest.getRoLineNumber(),
            updateReturnOrderDataRequest.getSoLineNumber(),
            updateReturnOrderDataRequest.getRcTrackingId());
    rcContainerService.updateReturnOrderData(
        updateReturnOrderDataRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1))
        .findByTrackingId(updateReturnOrderDataRequest.getRcTrackingId());
    verify(containerRLogRepository, times(1))
        .updateReturnOrderData(
            updateReturnOrderDataRequest.getRoNumber(),
            updateReturnOrderDataRequest.getRoLineNumber(),
            updateReturnOrderDataRequest.getSoLineNumber(),
            updateReturnOrderDataRequest.getRcTrackingId());
  }

  @Test
  public void updateReturnOrderDataTestWhenReturnIsPresent() {

    when(containerRLogRepository.findByTrackingId(updateReturnOrderDataRequest.getRcTrackingId()))
        .thenReturn(Optional.of(containerRLogWithReturnOrderPresentAfterAutoReturn));
    doNothing()
        .when(containerRLogRepository)
        .updateReturnOrderData(
            updateReturnOrderDataRequest.getRoNumber(),
            updateReturnOrderDataRequest.getRoLineNumber(),
            updateReturnOrderDataRequest.getSoLineNumber(),
            updateReturnOrderDataRequest.getRcTrackingId());
    rcContainerService.updateReturnOrderData(
        updateReturnOrderDataRequest, MockHttpHeaders.getHeaders("9074", "US"));
    verify(containerRLogRepository, times(1))
        .findByTrackingId(updateReturnOrderDataRequest.getRcTrackingId());
    verify(containerRLogRepository, times(0))
        .updateReturnOrderData(
            updateReturnOrderDataRequest.getRoNumber(),
            updateReturnOrderDataRequest.getRoLineNumber(),
            updateReturnOrderDataRequest.getSoLineNumber(),
            updateReturnOrderDataRequest.getRcTrackingId());
  }
}
