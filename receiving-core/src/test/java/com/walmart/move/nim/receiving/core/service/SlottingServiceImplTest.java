package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ContainerOrder;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.MockSlottingUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SlottingServiceImplTest {
  @Mock SlottingRestApiClient slottingRestApiClient;
  @InjectMocks SlottingServiceImpl slottingServiceImpl;
  private Gson gson = new Gson();

  @BeforeMethod
  public void createRdcSlottingServiceImpl() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
    TenantContext.setCorrelationId("2323-32323-32323-2323");
  }

  @AfterMethod
  public void resetMocks() {
    reset(slottingRestApiClient);
  }

  @Test
  public void testGetPrimeSlot_Success() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(12123L);
    ItemData itemData = new ItemData();
    itemData.setAtlasConvertedItem(true);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    SlottingPalletResponse acquiredPrimeSlot =
        slottingServiceImpl.getPrimeSlot(
            deliveryDocumentLine, "2323-23223", MockHttpHeaders.getHeaders());

    assertNotNull(acquiredPrimeSlot);
    assertTrue(StringUtils.isNotBlank(acquiredPrimeSlot.getMessageId()));
    assertEquals(acquiredPrimeSlot.getLocations().get(0).getLocation(), "H0002");
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetPrimeSlot_ErrorThrown() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(12123L);
    ItemData itemData = new ItemData();
    itemData.setAtlasConvertedItem(true);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    slottingServiceImpl.getPrimeSlot(
        deliveryDocumentLine, "2323-23223", MockHttpHeaders.getHeaders());
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePallet_Success() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    receiveInstructionRequest.setDoorNumber("321");
    receiveInstructionRequest.setQuantity(1);
    doReturn(getAutoSlotFromSlotting())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    SlottingPalletResponse acquiredSlot =
        slottingServiceImpl.receivePallet(
            receiveInstructionRequest, "F32818000020003005", MockHttpHeaders.getHeaders(), null);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A0001");
    assertEquals(acquiredSlot.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(acquiredSlot.getLocations().get(0).getSlotType(), "prime");
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePalletWithManualSlot_Success() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("G431");
    receiveInstructionRequest.setDoorNumber("321");
    receiveInstructionRequest.setQuantity(1);
    doReturn(getManualSlotFromSlotting())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    SlottingPalletResponse acquiredSlot =
        slottingServiceImpl.receivePallet(
            receiveInstructionRequest, "F32818000020003005", MockHttpHeaders.getHeaders(), null);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "G431");
    assertEquals(acquiredSlot.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(acquiredSlot.getLocations().get(0).getSlotType(), "prime");
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePalletWithManualSlotSize_Success() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotSize(64);
    receiveInstructionRequest.setDoorNumber("321");
    receiveInstructionRequest.setQuantity(1);
    doReturn(getManualSlotFromSlotting())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    SlottingPalletResponse acquiredSlot =
        slottingServiceImpl.receivePallet(
            receiveInstructionRequest, "F32818000020003005", MockHttpHeaders.getHeaders(), null);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocationSize(), 64);
    assertEquals(acquiredSlot.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(acquiredSlot.getLocations().get(0).getSlotType(), "prime");
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void test_freeSlot() {
    doReturn(true)
        .when(slottingRestApiClient)
        .freeSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    slottingServiceImpl.freeSlot("1234", "M8023", "A123", MockHttpHeaders.getHeaders());

    verify(slottingRestApiClient, times(1))
        .freeSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void test_freeSlot_exception() {

    RuntimeException mockException = new RuntimeException();
    doThrow(mockException)
        .when(slottingRestApiClient)
        .freeSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    slottingServiceImpl.freeSlot("1234", "M0803", "A123", MockHttpHeaders.getHeaders());

    verify(slottingRestApiClient, times(1))
        .freeSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testPrimeSlotCompatibleForSplitPallet_HappyPath() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(52324344L);
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("B");
    itemData.setHandlingCode("C");
    deliveryDocumentLine.setAdditionalInfo(itemData);

    SlottingPalletResponse slottingResponse = new SlottingPalletResponse();
    slottingResponse.setMessageId("2334-32332-34332323e");
    when(slottingRestApiClient.getPrimeSlotForSplitPallet(
            any(SlottingPalletRequest.class), anyList(), any(HttpHeaders.class)))
        .thenReturn(slottingResponse);

    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.getPrimeSlotForSplitPallet(
            deliveryDocumentLine,
            MockSplitPalletInstructionDeliveryDocuments(),
            "3232-23232",
            MockHttpHeaders.getHeaders());

    assertNotNull(slottingPalletResponse);

    verify(slottingRestApiClient, times(1))
        .getPrimeSlotForSplitPallet(
            any(SlottingPalletRequest.class), anyList(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testPrimeSlotCompatibleForSplitPallet_Error() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(52324344L);
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("B");
    itemData.setHandlingCode("C");
    deliveryDocumentLine.setAdditionalInfo(itemData);

    SlottingPalletResponse slottingResponse = new SlottingPalletResponse();
    slottingResponse.setMessageId("2334-32332-34332323e");
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingRestApiClient)
        .getPrimeSlotForSplitPallet(
            any(SlottingPalletRequest.class), anyList(), any(HttpHeaders.class));

    slottingServiceImpl.getPrimeSlotForSplitPallet(
        deliveryDocumentLine,
        MockSplitPalletInstructionDeliveryDocuments(),
        "3232-23232",
        MockHttpHeaders.getHeaders());

    verify(slottingRestApiClient, times(1))
        .getPrimeSlotForSplitPallet(
            any(SlottingPalletRequest.class), anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePalletWithRdsPayLoad_Success() throws Exception {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse = null;
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    receiveInstructionRequest.setDoorNumber("321");
    receiveInstructionRequest.setQuantity(1);

    doReturn(getSlottingPalletResponseWithRdsContainers())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));
    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.receivePallet(
            receiveInstructionRequest,
            "F32818000020003005",
            MockHttpHeaders.getHeaders(),
            getMockNimRdsReceiveContainersPayLoad());

    if (slottingPalletResponse instanceof SlottingPalletResponseWithRdsResponse) {
      slottingPalletResponseWithRdsResponse =
          (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
    }

    assertNotNull(slottingPalletResponseWithRdsResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponseWithRdsResponse.getMessageId()));
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getLocation(), "K7298");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getSlotType(), "prime");
    assertNotNull(slottingPalletResponseWithRdsResponse.getRds());

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceivePalletWithRdsPayLoad_RdsThrowsError() throws Exception {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse = null;
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    receiveInstructionRequest.setDoorNumber("321");
    receiveInstructionRequest.setQuantity(1);

    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_RDS_SLOTTING_REQ,
                String.format(
                    ReceivingConstants.SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG, "PO Not found"),
                "NIMRDS-017",
                "PO Not found"))
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));

    slottingServiceImpl.receivePallet(
        receiveInstructionRequest,
        "F32818000020003005",
        MockHttpHeaders.getHeaders(),
        getMockNimRdsReceiveContainersPayLoad());

    assertNotNull(slottingPalletResponseWithRdsResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponseWithRdsResponse.getMessageId()));
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getLocation(), "K7298");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getSlotType(), "prime");
    assertNotNull(slottingPalletResponseWithRdsResponse.getRds());

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetPrimeSlotByItemNumbers_Success_AllItemNumbers() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setItemNumber(4545452324L);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setItemNumber(4545452325L);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    doReturn(MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.getPrimeSlot(containerItems, MockHttpHeaders.getHeaders());

    assertNotNull(slottingPalletResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponse.getMessageId()));
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "A0002");
    assertEquals(slottingPalletResponse.getLocations().get(1).getLocation(), "A0002");

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetPrimeSlotByItemNumbers_SlottingThrowsException() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setItemNumber(4545452324L);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setItemNumber(4545452325L);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    slottingServiceImpl.getPrimeSlot(containerItems, MockHttpHeaders.getHeaders());
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetPrimeSlotByItemNumbers_Success_PartialItemNumbers() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setItemNumber(343434L);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setItemNumber(343433L);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    doReturn(MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting_PartialSuccess())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.getPrimeSlot(containerItems, MockHttpHeaders.getHeaders());

    assertNotNull(slottingPalletResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponse.getMessageId()));
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "A0002");
    assertNull(slottingPalletResponse.getLocations().get(1).getLocation());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getDesc());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getCode());

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetPrimeSlotByItemNumbers_Error_AllItemNumbers() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setItemNumber(343434L);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setItemNumber(343433L);
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    doReturn(MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting_Error())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));

    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.getPrimeSlot(containerItems, MockHttpHeaders.getHeaders());

    assertNotNull(slottingPalletResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponse.getMessageId()));
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertNull(slottingPalletResponse.getLocations().get(0).getLocation());
    assertNull(slottingPalletResponse.getLocations().get(1).getLocation());
    assertNotNull(slottingPalletResponse.getLocations().get(0).getDesc());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getDesc());
    assertNotNull(slottingPalletResponse.getLocations().get(0).getCode());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getCode());

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceivePalletForDAPalletWithRdsPayLoad_Success() throws Exception {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse = null;
    List<DeliveryDocument> deliveryDocumentList =
        MockGdmResponse.getDeliveryDocumentsForDA_NonAtlasItem();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocumentList);
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocumentList.get(0).getDeliveryDocumentLines());
    receiveInstructionRequest.setDoorNumber("321");
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("A0002");
    receiveInstructionRequest.setSlotDetails(slotDetails);
    receiveInstructionRequest.setQuantity(1);

    doReturn(getSlottingPalletResponseWithRdsContainers())
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));
    SlottingPalletResponse slottingPalletResponse =
        slottingServiceImpl.receivePallet(
            receiveInstructionRequest,
            "F32818000020003005",
            MockHttpHeaders.getHeaders(),
            getMockNimRdsReceiveContainersPayLoad());

    if (slottingPalletResponse instanceof SlottingPalletResponseWithRdsResponse) {
      slottingPalletResponseWithRdsResponse =
          (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
    }

    assertNotNull(slottingPalletResponseWithRdsResponse);
    assertTrue(StringUtils.isNotBlank(slottingPalletResponseWithRdsResponse.getMessageId()));
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getLocation(), "K7298");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getAsrsAlignment(), "SYM1");
    assertEquals(
        slottingPalletResponseWithRdsResponse.getLocations().get(0).getSlotType(), "prime");
    assertNotNull(slottingPalletResponseWithRdsResponse.getRds());

    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequestWithRdsPayLoad.class), any(HttpHeaders.class));
  }

  private ReceiveContainersRequestBody getMockNimRdsReceiveContainersPayLoad() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setManifest("2323232");
    containerOrder.setDoorNum("132");
    containerOrder.setQty(32);
    containerOrder.setExpectedTi(4);
    containerOrder.setExpectedHi(4);
    containerOrder.setContainerGroupId("3232-223");
    containerOrders.add(containerOrder);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private List<DeliveryDocument> MockSplitPalletInstructionDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setItemNbr(4343434L);
    ItemData itemData1 = new ItemData();
    itemData1.setHandlingCode("C");
    itemData1.setPackTypeCode("B");
    itemData1.setPrimeSlot("G4344");
    deliveryDocumentLine1.setAdditionalInfo(itemData1);
    deliveryDocument1.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine1));

    DeliveryDocument deliveryDocument2 = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setItemNbr(4343435L);
    ItemData itemData2 = new ItemData();
    itemData1.setHandlingCode("C");
    itemData1.setPackTypeCode("B");
    itemData1.setPrimeSlot("G4444");
    deliveryDocumentLine2.setAdditionalInfo(itemData2);
    deliveryDocument2.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine2));
    deliveryDocuments.add(deliveryDocument1);
    deliveryDocuments.add(deliveryDocument2);
    return deliveryDocuments;
  }

  public static SlottingPalletResponse getPrimeSlotFromSmartSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingDivertLocations.setLocation("H0002");
    slottingDivertLocations.setType("success");
    slottingDivertLocations.setLocationSize(72);
    slottingDivertLocations.setSlotType("prime");
    slottingDivertLocations.setAsrsAlignment("NULL");
    slottingPalletResponse.setLocations(Collections.singletonList(slottingDivertLocations));
    return slottingPalletResponse;
  }

  public static SlottingPalletResponse getAutoSlotFromSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingDivertLocations.setLocation("A0001");
    slottingDivertLocations.setType("success");
    slottingDivertLocations.setLocationSize(72);
    slottingDivertLocations.setSlotType("prime");
    slottingDivertLocations.setAsrsAlignment("SYM1");
    slottingPalletResponse.setLocations(Collections.singletonList(slottingDivertLocations));
    return slottingPalletResponse;
  }

  public SlottingPalletResponse getSlottingPalletResponseWithRdsContainers() throws Exception {
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingResponseWithRdsContainers.json");
    return gson.fromJson(slottingResponse, SlottingPalletResponseWithRdsResponse.class);
  }

  public static SlottingPalletResponse getManualSlotFromSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingDivertLocations.setLocation("G431");
    slottingDivertLocations.setType("success");
    slottingDivertLocations.setLocationSize(64);
    slottingDivertLocations.setSlotType("prime");
    slottingDivertLocations.setAsrsAlignment("SYM1");
    slottingPalletResponse.setLocations(Collections.singletonList(slottingDivertLocations));
    return slottingPalletResponse;
  }
}
