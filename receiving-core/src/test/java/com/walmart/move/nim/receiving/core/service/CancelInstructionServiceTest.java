package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CancelInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private InstructionService instructionService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;

  @Mock private InstructionRepository instructionRepository;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private LPNCacheService lpnCacheService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private DockTagService dockTagService;

  private Instruction instruction;
  private Instruction cancelledInstruction;
  private ContainerDetails containerDetails;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    TenantContext.setFacilityNum(32612);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(instructionService, "pubReceiptsTopic", "TOPIC/RECEIVE/RECEIPTS");

    // Mock container data
    containerDetails = new ContainerDetails();
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setQuantity(1);

    // Mock instruction data
    instruction = new Instruction();
    instruction.setId(Long.valueOf("11120"));
    instruction.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    instruction.setContainer(containerDetails);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("12e1df00-ebf6-11e8-9c25-dd4bfc2a06fa");
    instruction.setMove(null);
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(1);
    instruction.setProviderId("DA");
    instruction.setReceivedQuantity(1);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"CROSSU\",\n"
            + "                \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "                \"purchaseReferenceLineNumber\": 9,\n"
            + "                \"purchaseReferenceLineStatus\": \"PARTIALLY_RECEIVED\",\n"
            + "                \"itemNbr\": 574322171,\n"
            + "                \"itemUPC\": \"00673419302784\",\n"
            + "                \"caseUPC\": \"10673419302781\",\n"
            + "                \"expectedQty\": 600,\n"
            + "                \"expectedQtyUOM\": \"ZA\",\n"
            + "                \"vnpkQty\": 3,\n"
            + "                \"whpkQty\": 3,\n"
            + "                \"vendorStockNumber\": \"6251454\",\n"
            + "                \"vendorPackCost\": 41.97,\n"
            + "                \"whpkSell\": 42.41,\n"
            + "                \"vnpkWgtQty\": 2.491,\n"
            + "                \"vnpkWgtUom\": \"LB\",\n"
            + "                \"vnpkcbqty\": 0.405,\n"
            + "                \"vnpkcbuomcd\": \"CF\",\n"
            + "                \"event\": \"POS REPLEN\",\n"
            + "                \"palletTi\": 30,\n"
            + "                \"palletHi\": 4,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }");

    // Mock cancelled instruction
    cancelledInstruction = new Instruction();
    cancelledInstruction.setId(Long.valueOf("11120"));
    cancelledInstruction.setContainer(containerDetails);
    cancelledInstruction.setChildContainers(null);
    cancelledInstruction.setCreateTs(new Date());
    cancelledInstruction.setCreateUserId("sysadmin");
    cancelledInstruction.setLastChangeTs(new Date());
    cancelledInstruction.setLastChangeUserId("sysadmin");
    cancelledInstruction.setCompleteTs(new Date());
    cancelledInstruction.setCompleteUserId("sysadmin");
    cancelledInstruction.setDeliveryNumber(Long.valueOf("21119003"));
    cancelledInstruction.setGtin("00000943037194");
    cancelledInstruction.setInstructionCode("Build Container");
    cancelledInstruction.setInstructionMsg("Build the Container");
    cancelledInstruction.setItemDescription("HEM VALUE PACK (4)");
    cancelledInstruction.setMessageId("12e1df00-ebf6-11e8-9c25-dd4bfc2a06fa");
    cancelledInstruction.setMove(null);
    cancelledInstruction.setPoDcNumber("32899");
    cancelledInstruction.setPrintChildContainerLabels(false);
    cancelledInstruction.setPurchaseReferenceNumber("9763140004");
    cancelledInstruction.setPurchaseReferenceLineNumber(Integer.valueOf(1));
    cancelledInstruction.setProjectedReceiveQty(1);
    cancelledInstruction.setProviderId("DA");
    cancelledInstruction.setReceivedQuantity(0);
    cancelledInstruction.setActivityName("SSTKU");
    cancelledInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    cancelledInstruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    cancelledInstruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"CROSSU\",\n"
            + "                \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "                \"purchaseReferenceLineNumber\": 9,\n"
            + "                \"purchaseReferenceLineStatus\": \"PARTIALLY_RECEIVED\",\n"
            + "                \"itemNbr\": 574322171,\n"
            + "                \"itemUPC\": \"00673419302784\",\n"
            + "                \"caseUPC\": \"10673419302781\",\n"
            + "                \"expectedQty\": 600,\n"
            + "                \"expectedQtyUOM\": \"ZA\",\n"
            + "                \"vnpkQty\": 3,\n"
            + "                \"whpkQty\": 3,\n"
            + "                \"vendorStockNumber\": \"6251454\",\n"
            + "                \"vendorPackCost\": 41.97,\n"
            + "                \"whpkSell\": 42.41,\n"
            + "                \"vnpkWgtQty\": 2.491,\n"
            + "                \"vnpkWgtUom\": \"LB\",\n"
            + "                \"vnpkcbqty\": 0.405,\n"
            + "                \"vnpkcbuomcd\": \"CF\",\n"
            + "                \"event\": \"POS REPLEN\",\n"
            + "                \"palletTi\": 30,\n"
            + "                \"palletHi\": 4,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }");
  }

  @AfterMethod
  public void tearDown() {
    reset(instructionRepository);
  }

  @Test
  public void testCancelInstructionException() {
    when(instructionRepository.findById(Long.valueOf("11125"))).thenReturn(Optional.empty());

    try {
      instructionService.cancelInstruction(Long.valueOf("11125"), httpHeaders);
    } catch (ReceivingException error) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getHttpStatus());
      assertEquals(error.getErrorResponse().getErrorKey(), ExceptionCodes.INSTRUCTION_NOT_FOUND);
      assertEquals(
          error.getErrorResponse().getErrorMessage(), ReceivingException.INSTRUCTION_NOT_FOUND);
    }
    verify(instructionRepository, times(1)).findById(Long.valueOf("11125"));
    reset(instructionRepository);
  }

  @Test
  public void testAutoCancelInstruction() throws Exception {
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(instruction));
    when(tenantSpecificConfigReader.getCcmConfigValue(
            anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES)))
        .thenReturn(new JsonParser().parse("5"));
    when(instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                any(Date.class), anyInt()))
        .thenReturn(new ArrayList<>(Collections.singletonList(instruction)));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(cancelledInstruction);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionService.autoCancelInstruction(4093);
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(jmsPublisher);
    reset(instructionRepository);
  }

  @Test
  public void testCancelInstructionSuccess() throws ReceivingException {
    when(instructionRepository.findById(Long.valueOf("11120")))
        .thenReturn(Optional.of(instruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(cancelledInstruction);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));

    InstructionSummary response =
        instructionService.cancelInstruction(Long.valueOf("11120"), httpHeaders);

    assertEquals(cancelledInstruction.getId(), response.getId());
    assertEquals(cancelledInstruction.getId(), response.get_id());
    assertEquals(cancelledInstruction.getGtin(), response.getGtin());
    assertEquals(cancelledInstruction.getActivityName(), response.getActivityName());
    assertEquals(cancelledInstruction.getItemDescription(), response.getItemDescription());
    assertEquals(
        cancelledInstruction.getContainer().getTrackingId(),
        response.getInstructionData().getContainer().getTrackingId());
    assertEquals(cancelledInstruction.getPoDcNumber(), response.getPoDcNumber());
    assertEquals(cancelledInstruction.getMessageId(), response.getInstructionData().getMessageId());
    assertEquals(
        cancelledInstruction.getPurchaseReferenceNumber(), response.getPurchaseReferenceNumber());
    assertEquals(
        cancelledInstruction.getPurchaseReferenceLineNumber(),
        Integer.valueOf(response.getPurchaseReferenceLineNumber()));
    assertEquals(
        cancelledInstruction.getProjectedReceiveQty(),
        response.getProjectedReceiveQty().intValue());
    assertEquals(
        cancelledInstruction.getProjectedReceiveQtyUOM(), response.getProjectedReceiveQtyUOM());
    assertEquals(
        cancelledInstruction.getReceivedQuantity(), response.getReceivedQuantity().intValue());
    assertEquals(cancelledInstruction.getReceivedQuantityUOM(), response.getReceivedQuantityUOM());
    assertEquals(cancelledInstruction.getCompleteUserId(), response.getCompleteUserId());

    verify(instructionRepository, times(1)).findById(Long.valueOf("11120"));
    verify(jmsPublisher, times(1)).publish(any(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(jmsPublisher);
    reset(instructionRepository);
  }

  @Test
  public void testCancelInstructionForDockTag_Success() throws ReceivingException {
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    when(instructionRepository.findById(Long.valueOf("11120")))
        .thenReturn(Optional.of(instruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(cancelledInstruction);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    InstructionSummary response =
        instructionService.cancelInstruction(Long.valueOf("11120"), httpHeaders);

    assertEquals(cancelledInstruction.getId(), response.getId());
    assertEquals(cancelledInstruction.getId(), response.get_id());
    assertEquals(cancelledInstruction.getGtin(), response.getGtin());
    assertEquals(cancelledInstruction.getItemDescription(), response.getItemDescription());
    assertEquals(
        cancelledInstruction.getContainer().getTrackingId(),
        response.getInstructionData().getContainer().getTrackingId());
    assertEquals(cancelledInstruction.getPoDcNumber(), response.getPoDcNumber());
    assertEquals(cancelledInstruction.getMessageId(), response.getInstructionData().getMessageId());
    assertEquals(
        cancelledInstruction.getPurchaseReferenceNumber(), response.getPurchaseReferenceNumber());
    assertEquals(
        cancelledInstruction.getPurchaseReferenceLineNumber(),
        Integer.valueOf(response.getPurchaseReferenceLineNumber()));
    assertEquals(
        cancelledInstruction.getProjectedReceiveQty(),
        response.getProjectedReceiveQty().intValue());
    assertEquals(
        cancelledInstruction.getProjectedReceiveQtyUOM(), response.getProjectedReceiveQtyUOM());
    assertEquals(
        cancelledInstruction.getReceivedQuantity(), response.getReceivedQuantity().intValue());
    assertEquals(cancelledInstruction.getReceivedQuantityUOM(), response.getReceivedQuantityUOM());
    assertEquals(cancelledInstruction.getCompleteUserId(), response.getCompleteUserId());

    verify(instructionRepository, times(1)).findById(Long.valueOf("11120"));
    verify(dockTagService, times(1))
        .updateDockTagById(
            cancelledInstruction.getDockTagId(),
            InstructionStatus.COMPLETED,
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    verify(jmsPublisher, times(0))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(jmsPublisher);
    reset(instructionRepository);
    instruction.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
  }
}
