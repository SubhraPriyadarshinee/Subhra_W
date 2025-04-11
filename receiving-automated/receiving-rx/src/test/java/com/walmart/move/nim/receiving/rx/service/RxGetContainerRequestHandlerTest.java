package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxGetContainerRequestHandlerTest {

  @Mock private ContainerService containerService;
  @Mock private ContainerRepository containerRepository;
  @Mock private ContainerItemRepository containerItemRepository;

  @InjectMocks private RxGetContainerRequestHandler rxGetContainerRequestHandler;
  private final boolean isReEngageDecantFlow = Boolean.FALSE;
  HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetContainerByTrackingId_za() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doReturn(mockContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(48);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    Container containerResponse =
        rxGetContainerRequestHandler.getContainerByTrackingId(
            "97123456789", true, "ZA", isReEngageDecantFlow, mockHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getContainerItems().get(0).getQuantity(), 1);
    assertSame(containerResponse.getContainerItems().get(0).getQuantityUOM(), "ZA");
  }

  @Test
  public void testGetContainerByTrackingId_EA() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doReturn(mockContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(48);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    Container containerResponse =
        rxGetContainerRequestHandler.getContainerByTrackingId(
            "97123456789", true, "EA", isReEngageDecantFlow, mockHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getContainerItems().get(0).getQuantity(), 48);
    assertSame(containerResponse.getContainerItems().get(0).getQuantityUOM(), "EA");
  }

  @Test
  public void testGetContainerByTrackingId_PH() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doReturn(mockContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(48);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    Container containerResponse =
        rxGetContainerRequestHandler.getContainerByTrackingId(
            "97123456789", true, "PH", isReEngageDecantFlow, mockHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getContainerItems().get(0).getQuantity(), 24);
    assertSame(containerResponse.getContainerItems().get(0).getQuantityUOM(), "PH");
  }

  @Test
  public void testGetContainerByTrackingId_auto() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doReturn(mockContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(12);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    Container containerResponse =
        rxGetContainerRequestHandler.getContainerByTrackingId(
            "97123456789", true, "AUTO", isReEngageDecantFlow, mockHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getContainerItems().get(0).getQuantity(), 6);
    assertSame(containerResponse.getContainerItems().get(0).getQuantityUOM(), "PH");
  }

  @Test
  public void testGetContainerByTrackingId_auto_1() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doReturn(mockContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(48);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    Container containerResponse =
        rxGetContainerRequestHandler.getContainerByTrackingId(
            "97123456789", false, "AUTO", isReEngageDecantFlow, mockHeaders);

    assertNotNull(containerResponse);
    assertSame(containerResponse.getContainerItems().get(0).getQuantity(), 1);
    assertSame(containerResponse.getContainerItems().get(0).getQuantityUOM(), "ZA");
  }

  @Test
  public void testGetContainerByTrackingId_auto_exception() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setTrackingId("97123456789");

    doReturn(mockContainer).when(containerRepository).findByTrackingId(anyString());
    doThrow(
            new ReceivingException(
                "Either parentContainer is null or trackingId is not present",
                HttpStatus.BAD_REQUEST))
        .when(containerService)
        .getContainerIncludingChild(any(Container.class), anyString());

    List<ContainerItem> mockContainerItems = new ArrayList<>();
    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setQuantity(48);
    mockContainerItem.setQuantityUOM("EA");
    mockContainerItem.setVnpkQty(48);
    mockContainerItem.setWhpkQty(2);
    mockContainerItem.setTrackingId("97123456789");
    mockContainerItems.add(mockContainerItem);

    doReturn(mockContainerItems).when(containerItemRepository).findByTrackingId(anyString());

    try {
      Container containerResponse =
          rxGetContainerRequestHandler.getContainerByTrackingId(
              "97123456789", true, "AUTO", isReEngageDecantFlow, mockHeaders);
    } catch (ReceivingBadDataException rbde) {
      assertSame(rbde.getErrorCode(), "GLS-RCV-INT-500");
      assertSame(
          rbde.getDescription(), "Either parentContainer is null or trackingId is not present");
    }
  }

  @Test
  public void test_getContainersSummary_withlot() {

    long mockInstructionId = -1l;
    String mockSerial = "MOCK_SERIAL";
    String mockLot = "MOCK_LOT";

    doReturn(Collections.emptyList())
        .when(containerRepository)
        .findByInstructionIdLotSerial(anyLong(), anyString(), anyString());
    rxGetContainerRequestHandler.getContainersSummary(mockInstructionId, mockSerial, mockLot);
    verify(containerRepository, times(1))
        .findByInstructionIdLotSerial(anyLong(), anyString(), anyString());
  }
}
