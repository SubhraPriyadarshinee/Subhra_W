package com.walmart.move.nim.receiving.rx.builders;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.*;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockRDSContainer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxContainerLabelBuilderTest {
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private RxContainerLabelBuilder rxContainerLabelBuilder;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32898);
    doReturn("pallet_lpn_rx")
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString());
  }

  @Test
  public void testGenerateContainerLabel() {

    ReceiveContainersResponseBody slotDetails = MockRDSContainer.mockRdsContainer();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_LABEL_PARTIAL_TAG);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(123456l);
    deliveryDocumentLine.setDescription("MOCK Item Description");
    deliveryDocumentLine.setPurchaseReferenceNumber("Mock Purchase Ref Number");
    deliveryDocumentLine.setVendorPack(10);
    deliveryDocumentLine.setWarehousePack(30);
    deliveryDocumentLine.setNdc("001-123458");
    deliveryDocumentLine.setItemUpc("12345678984566");
    Container parentContainer = getMockContainerWhichOutChild();
    Instruction instruction = new Instruction();
    instruction.setReceivedQuantity(1);
    instruction.setReceivedQuantityUOM("ZA");
    instruction.setGtin("MOCK_GTIN");
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION, "PRIME_SLOT");
    instruction.setMove(moveTreeMap);

    PrintLabelData containerLabel =
        rxContainerLabelBuilder.generateContainerLabel(
            slotDetails.getReceived().get(0),
            deliveryDocumentLine,
            httpHeaders,
            parentContainer,
            instruction);

    assertNotNull(containerLabel);
    assertEquals("receiving", containerLabel.getClientId());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getHeaders()));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        containerLabel.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests()));

    assertEquals("pallet_lpn_rx", containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        slotDetails.getReceived().get(0).getDestinations().get(0).getSlot(),
        containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals(72, containerLabel.getPrintRequests().get(0).getTtlInHours());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests().get(0).getData()));

    assertEquals("123456", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getVendorPack()),
        containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getWarehousePack()),
        containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals(
        containerLabel.getPrintRequests().get(0).getData().get(7).getValue(),
        "MOCK_PARENT_CONTAINER_GTIN");
    assertEquals(
        "001-123458", containerLabel.getPrintRequests().get(0).getData().get(8).getValue());
    assertEquals("1", containerLabel.getPrintRequests().get(0).getData().get(17).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(19).getValue());
    assertEquals(VNPK, containerLabel.getPrintRequests().get(0).getData().get(18).getValue());
  }

  @Test
  public void testGenerateContainerLabel_Partial() {

    ReceiveContainersResponseBody slotDetails = MockRDSContainer.mockRdsContainer();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_LABEL_PARTIAL_TAG);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(123456l);
    deliveryDocumentLine.setDescription("MOCK Item Description");
    deliveryDocumentLine.setPurchaseReferenceNumber("Mock Purchase Ref Number");
    deliveryDocumentLine.setVendorPack(15);
    deliveryDocumentLine.setWarehousePack(5);
    deliveryDocumentLine.setNdc("001-123458");
    deliveryDocumentLine.setItemUpc("12345678984566");
    Container parentContainer = getMockContainerWhichOutChild();
    Instruction instruction = new Instruction();
    instruction.setReceivedQuantity(10);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setGtin("MOCK_GTIN");
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION, "PRIME_SLOT");
    instruction.setMove(moveTreeMap);

    PrintLabelData containerLabel =
        rxContainerLabelBuilder.generateContainerLabel(
            slotDetails.getReceived().get(0),
            deliveryDocumentLine,
            httpHeaders,
            parentContainer,
            instruction);

    assertNotNull(containerLabel);
    assertEquals("receiving", containerLabel.getClientId());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getHeaders()));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        containerLabel.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests()));

    assertEquals("pallet_lpn_rx", containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        slotDetails.getReceived().get(0).getDestinations().get(0).getSlot(),
        containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals(72, containerLabel.getPrintRequests().get(0).getTtlInHours());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests().get(0).getData()));

    assertEquals("123456", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getVendorPack()),
        containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getWarehousePack()),
        containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals(
        containerLabel.getPrintRequests().get(0).getData().get(7).getValue(),
        "MOCK_PARENT_CONTAINER_GTIN");
    assertEquals(
        "001-123458", containerLabel.getPrintRequests().get(0).getData().get(8).getValue());
    assertEquals("2", containerLabel.getPrintRequests().get(0).getData().get(17).getValue());
    assertEquals(PARTIAL, containerLabel.getPrintRequests().get(0).getData().get(19).getValue());
    assertEquals(WHPK, containerLabel.getPrintRequests().get(0).getData().get(18).getValue());
  }

  @Test
  public void testGenerateContainerLabel_PartialTagDisabled() {

    ReceiveContainersResponseBody slotDetails = MockRDSContainer.mockRdsContainer();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_LABEL_PARTIAL_TAG);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(123456l);
    deliveryDocumentLine.setDescription("MOCK Item Description");
    deliveryDocumentLine.setPurchaseReferenceNumber("Mock Purchase Ref Number");
    deliveryDocumentLine.setVendorPack(15);
    deliveryDocumentLine.setWarehousePack(5);
    deliveryDocumentLine.setNdc("001-123458");
    deliveryDocumentLine.setItemUpc("12345678984566");
    Container parentContainer = getMockContainerWhichOutChild();
    Instruction instruction = new Instruction();
    instruction.setReceivedQuantity(2);
    instruction.setReceivedQuantityUOM("ZA");
    instruction.setGtin("MOCK_GTIN");

    PrintLabelData containerLabel =
        rxContainerLabelBuilder.generateContainerLabel(
            slotDetails.getReceived().get(0),
            deliveryDocumentLine,
            httpHeaders,
            parentContainer,
            instruction);

    assertNotNull(containerLabel);
    assertEquals("receiving", containerLabel.getClientId());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getHeaders()));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        containerLabel.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests()));

    assertEquals("pallet_lpn_rx", containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        slotDetails.getReceived().get(0).getDestinations().get(0).getSlot(),
        containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals(72, containerLabel.getPrintRequests().get(0).getTtlInHours());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests().get(0).getData()));

    assertEquals("123456", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getVendorPack()),
        containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getWarehousePack()),
        containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals(
        containerLabel.getPrintRequests().get(0).getData().get(7).getValue(),
        "MOCK_PARENT_CONTAINER_GTIN");
    assertEquals(
        "001-123458", containerLabel.getPrintRequests().get(0).getData().get(8).getValue());
    assertEquals("2", containerLabel.getPrintRequests().get(0).getData().get(17).getValue());
    assertEquals(18, containerLabel.getPrintRequests().get(0).getData().size());
  }

  @Test
  public void testGenerateContainerLabel_fallback_to_container_gtin() {

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_LABEL_PARTIAL_TAG);
    ReceiveContainersResponseBody slotDetails = MockRDSContainer.mockRdsContainer();

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(123456l);
    deliveryDocumentLine.setDescription("MOCK Item Description");
    deliveryDocumentLine.setPurchaseReferenceNumber("Mock Purchase Ref Number");
    deliveryDocumentLine.setVendorPack(10);
    deliveryDocumentLine.setWarehousePack(30);
    deliveryDocumentLine.setNdc("001-123458");
    Instruction instruction = new Instruction();
    instruction.setReceivedQuantity(10);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setGtin("MOCK_GTIN");
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION, "PRIME_SLOT");
    instruction.setMove(moveTreeMap);

    Container parentContainer = getMockContainer();

    PrintLabelData containerLabel =
        rxContainerLabelBuilder.generateContainerLabel(
            slotDetails.getReceived().get(0),
            deliveryDocumentLine,
            httpHeaders,
            parentContainer,
            instruction);

    assertNotNull(containerLabel);
    assertEquals("receiving", containerLabel.getClientId());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getHeaders()));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        containerLabel.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        containerLabel.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests()));

    assertEquals("pallet_lpn_rx", containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        slotDetails.getReceived().get(0).getDestinations().get(0).getSlot(),
        containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals(72, containerLabel.getPrintRequests().get(0).getTtlInHours());

    assertFalse(CollectionUtils.isEmpty(containerLabel.getPrintRequests().get(0).getData()));

    assertEquals("123456", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals(
        ReceivingConstants.EMPTY_STRING,
        containerLabel.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getVendorPack()),
        containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        String.valueOf(deliveryDocumentLine.getWarehousePack()),
        containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals(
        containerLabel.getPrintRequests().get(0).getData().get(7).getValue(),
        "MOCK_CHILD_CONTAINER_GTIN");
    assertEquals(
        containerLabel.getPrintRequests().get(0).getData().get(8).getValue(), "001-123458");
  }

  private Container getMockContainer() {
    Container parentContainer = new Container();
    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setGtin("MOCK_PARENT_CONTAINER_GTIN");
    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    Container childContainer = new Container();
    ContainerItem childContainerItem = new ContainerItem();
    childContainerItem.setGtin("MOCK_CHILD_CONTAINER_GTIN");

    childContainer.setContainerItems(Arrays.asList(childContainerItem));

    Set<Container> childContainers = new HashSet<>();
    childContainers.add(childContainer);

    parentContainer.setChildContainers(childContainers);

    return parentContainer;
  }

  private Container getMockContainerWhichOutChild() {
    Container parentContainer = new Container();
    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setGtin("MOCK_PARENT_CONTAINER_GTIN");
    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    return parentContainer;
  }
}
