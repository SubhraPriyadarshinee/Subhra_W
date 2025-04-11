package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.WitronPutawayHandler;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronSplitPalletServiceTest {
  @InjectMocks private WitronSplitPalletService witronSplitPalletService;

  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private WitronPutawayHandler witronPutawayHandler;
  @Mock private JmsPublisher jmsPublisher;

  @Captor ArgumentCaptor<Container> containerCaptor;
  @Captor ArgumentCaptor<String> containerActionCaptor;
  @Captor ArgumentCaptor<Container> putAwayContainerCaptor;
  @Captor ArgumentCaptor<String> putAwayActionCaptor;

  private Container mockContainer;
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
  private String originalContainerTrackingId = "B32612000020826581";
  private String newContainerTrackingId = "B32612000020829640";
  private Integer originalAvailableToSellQty = 24;
  private Integer adjustQty = -16;

  @BeforeMethod
  public void initMock() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    mockContainer = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId(originalContainerTrackingId);
    containerItem.setActualTi(4);
    containerItem.setActualHi(8);
    containerItem.setVnpkQty(8);
    containerItem.setWhpkQty(8);
    containerItem.setQuantity(120);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkWgtQty(1.0F);
    containerItem.setVnpkWgtUom(ReceivingConstants.Uom.LB);
    containerItems.add(containerItem);
    mockContainer.setTrackingId(originalContainerTrackingId);
    mockContainer.setWeight(15.0F);
    mockContainer.setContainerItems(containerItems);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerPersisterService);
    reset(gdcPutawayPublisher);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testSplitPallet_success() throws ReceivingException {
    doReturn(mockContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(
            any(Container.class), containerActionCaptor.capture(), any(HttpHeaders.class));

    String newContainerType = "iGPS Pallet";
    witronSplitPalletService.splitPallet(
        originalContainerTrackingId,
        originalAvailableToSellQty,
        newContainerTrackingId,
        newContainerType,
        adjustQty,
        httpHeaders);

    verify(containerPersisterService, times(2)).saveContainer(containerCaptor.capture());
    List<Container> capturedContainers = containerCaptor.getAllValues();

    // Original newContainer should update the quantity, weight and actualHI
    assertEquals(originalContainerTrackingId, capturedContainers.get(0).getTrackingId());
    assertEquals(13.0F, capturedContainers.get(0).getWeight());
    assertEquals(ReceivingConstants.Uom.LB, capturedContainers.get(0).getWeightUOM());
    AssertJUnit.assertSame(104, capturedContainers.get(0).getContainerItems().get(0).getQuantity());
    AssertJUnit.assertSame(4, capturedContainers.get(0).getContainerItems().get(0).getActualHi());
    assertEquals("witronTest", capturedContainers.get(0).getLastChangedUser());

    // New newContainer should be created with proper quantity, weight and actualHI
    assertEquals(newContainerTrackingId, capturedContainers.get(1).getTrackingId());
    assertEquals(2.0F, capturedContainers.get(1).getWeight());
    assertEquals(ReceivingConstants.Uom.LB, capturedContainers.get(1).getWeightUOM());
    AssertJUnit.assertSame(16, capturedContainers.get(1).getContainerItems().get(0).getQuantity());
    AssertJUnit.assertSame(1, capturedContainers.get(1).getContainerItems().get(0).getActualHi());
    assertEquals("witronTest", capturedContainers.get(1).getLastChangedUser());

    verify(gdcPutawayPublisher, times(2))
        .publishMessage(
            putAwayContainerCaptor.capture(),
            putAwayActionCaptor.capture(),
            any(HttpHeaders.class));

    assertEquals(
        containerActionCaptor.getAllValues(),
        Arrays.asList(
            ReceivingConstants.PUTAWAY_UPDATE_ACTION, ReceivingConstants.PUTAWAY_ADD_ACTION));

    assertEquals(
        putAwayActionCaptor.getAllValues(),
        Arrays.asList(
            ReceivingConstants.PUTAWAY_UPDATE_ACTION, ReceivingConstants.PUTAWAY_ADD_ACTION));

    List<Container> putAwaycapturedContainers = putAwayContainerCaptor.getAllValues();

    // Original newContainer RTU message with the quantity received from Inventory
    assertEquals(originalContainerTrackingId, putAwaycapturedContainers.get(0).getTrackingId());
    assertEquals(3.0F, putAwaycapturedContainers.get(0).getWeight());
    assertEquals(ReceivingConstants.Uom.LB, putAwaycapturedContainers.get(0).getWeightUOM());
    final ContainerItem originalContainerItem =
        putAwaycapturedContainers.get(0).getContainerItems().get(0);
    AssertJUnit.assertSame(originalAvailableToSellQty, originalContainerItem.getQuantity());
    AssertJUnit.assertSame(1, originalContainerItem.getActualHi());

    // New newContainer RTU message
    final Container newContainer = putAwaycapturedContainers.get(1);
    assertEquals(newContainerTrackingId, newContainer.getTrackingId());
    assertEquals(newContainer.getContainerType(), newContainerType);
    assertEquals(2.0F, newContainer.getWeight());
    assertEquals(ReceivingConstants.Uom.LB, newContainer.getWeightUOM());
    final ContainerItem newContainerItem = newContainer.getContainerItems().get(0);
    AssertJUnit.assertSame(16, newContainerItem.getQuantity());
    AssertJUnit.assertSame(1, newContainerItem.getActualHi());
  }

  @Test
  public void testSplitPallet_exception() throws Exception {
    doReturn(null)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    try {
      witronSplitPalletService.splitPallet(
          "B32612000020826581", 24, "B32612000020829640", null, -16, httpHeaders);
    } catch (ReceivingException receivingException) {
      assertNotNull(receivingException);
      assertNotNull(receivingException.getErrorResponse());
      assertEquals(HttpStatus.BAD_REQUEST, receivingException.getHttpStatus());
      assertEquals(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
          receivingException.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
          receivingException.getErrorResponse().getErrorMessage());
    }
  }
}
