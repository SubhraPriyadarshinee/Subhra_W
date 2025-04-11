package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import java.util.Arrays;
import java.util.Optional;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** @author v0k00fe */
public class RdcUpdateInstructionHandlerTest {

  @Mock InstructionPersisterService instructionPersisterService;
  @Mock InstructionStateValidator instructionStateValidator;
  @Mock ReceiptService receiptService;
  @Mock LPNCacheService lpnCacheService;
  @Mock RdcInstructionHelper rdcInstructionHelper;
  @InjectMocks RdcUpdateInstructionHandler rdcUpdateInstructionHandler;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private ContainerService containerService;
  private Gson gson;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcUpdateInstructionHandler, "gson", new Gson());
    gson = new Gson();
  }

  @Test
  @SneakyThrows
  public void testUpdateInstruction() {

    Instruction mockInstruction = MockInstructionResponse.getMockInstruction();
    mockInstruction.setCreateUserId("sysadmin");

    when(containerService.findOneContainerByInstructionId(anyLong())).thenReturn(Optional.empty());
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Arrays.<Receipt>asList(new Receipt()));
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn("getLPNBasedOnTenantResponse");
    when(rdcInstructionHelper.buildContainerAndContainerItem(
            any(), any(), any(), anyInt(), anyString(), anyString()))
        .thenReturn(new Container());
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "4920");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "Door-6");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "001001001");

    UpdateInstructionRequest mockUpdateInstructionRequest = new UpdateInstructionRequest();
    DocumentLine mockDocumentLine = new DocumentLine();
    mockDocumentLine.setQuantity(1);
    mockUpdateInstructionRequest.setDeliveryDocumentLines(Arrays.asList(mockDocumentLine));
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);
    InstructionResponse result =
        rdcUpdateInstructionHandler.updateInstruction(
            Long.valueOf(1), mockUpdateInstructionRequest, "parentTrackingId", mockHttpHeaders);

    assertNotNull(result.getInstruction());

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(receiptService, times(0))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class),
            anyString(),
            nullable(String.class),
            anyString(),
            anyInt());
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any());
    verify(rdcInstructionHelper, times(1))
        .buildContainerAndContainerItem(any(), any(), any(), anyInt(), anyString(), anyString());
    verify(containerService, times(1)).findOneContainerByInstructionId(anyLong());
  }

  @Test
  @SneakyThrows
  public void testUpdateInstruction_existing_container() {

    Instruction mockInstruction = MockInstructionResponse.getMockInstruction();
    mockInstruction.setCreateUserId("sysadmin");

    when(containerService.findOneContainerByInstructionId(anyLong()))
        .thenAnswer(
            invocation -> {
              Container mockContainer = new Container();
              mockContainer.setTrackingId("MOCK_TRACKING_ID");

              ContainerItem containerItem = new ContainerItem();
              containerItem.setTrackingId("MOCK_TRACKING_ID");
              mockContainer.setContainerItems(Arrays.asList(containerItem));

              return Optional.of(mockContainer);
            });
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Arrays.<Receipt>asList(new Receipt()));
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn("getLPNBasedOnTenantResponse");
    when(rdcInstructionHelper.buildContainerAndContainerItem(
            any(), any(), any(), anyInt(), anyString(), anyString()))
        .thenReturn(new Container());
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "4920");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "Door-6");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "001001001");

    UpdateInstructionRequest mockUpdateInstructionRequest = new UpdateInstructionRequest();
    DocumentLine mockDocumentLine = new DocumentLine();
    mockDocumentLine.setQuantity(1);
    mockUpdateInstructionRequest.setDeliveryDocumentLines(Arrays.asList(mockDocumentLine));

    InstructionResponse result =
        rdcUpdateInstructionHandler.updateInstruction(
            Long.valueOf(1), mockUpdateInstructionRequest, "parentTrackingId", mockHttpHeaders);

    assertNotNull(result.getInstruction());

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(receiptService, times(0))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class),
            anyString(),
            nullable(String.class),
            anyString(),
            anyInt());
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(any());
    verify(rdcInstructionHelper, times(0))
        .buildContainerAndContainerItem(any(), any(), any(), anyInt(), anyString(), anyString());
  }

  @Test
  @SneakyThrows
  public void testUpdateInstruction_atlas_converted_item_receipt_update() {

    Instruction mockInstruction = MockInstructionResponse.getMockInstruction();
    mockInstruction.setCreateUserId("sysadmin");

    when(containerService.findOneContainerByInstructionId(anyLong())).thenReturn(Optional.empty());
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Arrays.<Receipt>asList(new Receipt()));
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn("getLPNBasedOnTenantResponse");
    when(rdcInstructionHelper.buildContainerAndContainerItem(
            any(), any(), any(), anyInt(), anyString(), anyString()))
        .thenReturn(new Container());
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .verifyAndPopulateProDateInfo(
            any(DeliveryDocument.class), any(Instruction.class), any(HttpHeaders.class));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "4920");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "Door-6");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "001001001");

    UpdateInstructionRequest mockUpdateInstructionRequest = new UpdateInstructionRequest();
    mockUpdateInstructionRequest.setDoorNumber("102");
    DocumentLine mockDocumentLine = new DocumentLine();
    mockDocumentLine.setQuantity(1);
    mockUpdateInstructionRequest.setDeliveryDocumentLines(Arrays.asList(mockDocumentLine));

    InstructionResponse result =
        rdcUpdateInstructionHandler.updateInstruction(
            Long.valueOf(1), mockUpdateInstructionRequest, "parentTrackingId", mockHttpHeaders);

    assertNotNull(result.getInstruction());

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class),
            anyString(),
            nullable(String.class),
            anyString(),
            anyInt());
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any());
    verify(rdcInstructionHelper, times(1))
        .buildContainerAndContainerItem(any(), any(), any(), anyInt(), anyString(), anyString());
    verify(containerService, times(1)).findOneContainerByInstructionId(anyLong());
  }
}
