package com.walmart.move.nim.receiving.rdc.label;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.SLOTTING_DA_RECEIVING_METHOD;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.Test;

public class LabelGeneratorTest {

  LabelAttributes labelAttributes = new LabelAttributes("123", 4, 4, "230", "1234567");
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  @Test
  public void test_generatePalletLabels_split_pallet_AtlasItems() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            null,
            LabelFormat.ATLAS_RDC_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && LabelConstants.LBL_SPLIT_PALLET.equals(e.getValue())));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(StringUtils.EMPTY));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_SSTK.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_SSTK.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(1).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(2).getTtlInHours(), RdcConstants.LBL_TTL);
  }

  @Test
  public void test_generatePalletLabels_split_pallet_receiver_not_null_NonAtlasItems()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Integer receiver = 1234;
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().receiver(receiver).build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            null,
            LabelFormat.LEGACY_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(receiver)));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.LBL_TTL);
  }

  @Test
  public void test_generatePalletLabels_SSTK_Freights() throws IOException {
    String partialTag = null;
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Integer receiver = 1234;
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().receiver(receiver).build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            false,
            null,
            LabelFormat.LEGACY_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    List<LabelData> labelDataList = printLabelRequestList.get(2).getData();
    Optional<LabelData> LabelDataForPartialTag =
        labelDataList
            .stream()
            .filter(labelData1 -> labelData1.getKey().equals(LabelConstants.LBL_PARTIAL_TAG))
            .findAny();
    if (LabelDataForPartialTag.isPresent()) {
      partialTag = LabelDataForPartialTag.get().getValue();
    }

    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(receiver)));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertNotNull(printLabelRequestList.get(2).getData());
    assertTrue(printLabelRequestList.get(2).getData().size() > 0);
    assertEquals(printLabelRequestList.get(1).getTtlInHours(), RdcConstants.LBL_TTL);
    assertNotNull(partialTag);
  }

  @Test
  public void test_generatePalletLabels_DA_Freights() throws IOException {
    String partialTag = null;
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Integer receiver = 1234;
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().receiver(receiver).build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            false,
            null,
            LabelFormat.LEGACY_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    List<LabelData> labelDataList = printLabelRequestList.get(2).getData();
    Optional<LabelData> LabelDataForPartialTag =
        labelDataList
            .stream()
            .filter(labelData1 -> labelData1.getKey().equals(LabelConstants.LBL_PARTIAL_TAG))
            .findAny();
    if (LabelDataForPartialTag.isPresent()) {
      partialTag = LabelDataForPartialTag.get().getValue();
    }

    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(receiver)));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.LEGACY_SSTK.getFormat()));
    assertNotNull(printLabelRequestList.get(2).getData());
    assertTrue(printLabelRequestList.get(2).getData().size() > 0);
    assertEquals(partialTag, StringUtils.EMPTY);
  }

  @Test
  public void testGenerateDACaseLabelForStoreFriendlyWithEventCharAsStars() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers = getMockReceivedDAContainers("R8000");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_EVENTCHAR))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf("*")));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DA_STORE_FRIENDLY.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDACaseLabelForStoreFriendlyWithEventCharAsMFC() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers = getMockReceivedDAContainersForMFC("R8000");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            true);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_EVENTCHAR))
            .findFirst()
            .get()
            .getValue()
            .equals(""));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DA_STORE_FRIENDLY.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_PO_EVENT))
            .findFirst()
            .get()
            .getValue()
            .equals(LabelConstants.LBL_MFC_PO_EVENT));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_STOREZONE))
            .findFirst()
            .get()
            .getValue()
            .equals("M"));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_AISLE))
            .findFirst()
            .get()
            .getValue()
            .equals("FC"));
  }

  @Test
  public void testGenerateDACaseLabelForStoreFriendlyLabelsAtlasItems() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers = getMockReceivedDAContainers("R8000");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_EVENTCHAR))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf("*")));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_DA_STORE_FRIENDLY.getFormat()));
  }

  @Test
  public void testGenerateDACaseLabelForStoreFriendlyWithRoutingLabel() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers =
        getMockReceivedDAContainersWithStoreZoneAndAisle("R8000");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_EVENTCHAR))
            .findFirst()
            .get()
            .getValue()
            .equals(""));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DA_STORE_FRIENDLY.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDANonConveyable_Voice_Put() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers = getMockReceivedDAContainersForInductItems("V0050");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DA_NON_CONVEYABLE_VOICE_PUT.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_SECTION))
            .findFirst()
            .get()
            .getValue()
            .equals("3-3"));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDAConveyableInductPut() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers = getMockReceivedDAContainersForInductItems("P1001");

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DA_CONVEYABLE_INDUCT_PUT.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_SECTION))
            .findFirst()
            .get()
            .getValue()
            .equals("3-3"));
    assertEquals(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_DEPT))
            .findFirst()
            .get()
            .getValue(),
        "1");
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDsdcPackLabel() {
    long printJobId = 1;
    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    DsdcReceiveResponse dsdcReceiveResponse = getMockReceivedDsdcContainers("R8002", "");

    Map<String, Object> result =
        LabelGenerator.generateDsdcPackLabel(
            dsdcReceiveRequest,
            dsdcReceiveResponse,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DSDC.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDsdcAuditPackLabel() throws IOException {
    long printJobId = 1;
    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    DsdcReceiveResponse dsdcReceiveResponse = getMockReceivedDsdcContainers("AUDIT", "Y");

    Map<String, Object> result =
        LabelGenerator.generateDsdcPackLabel(
            dsdcReceiveRequest,
            dsdcReceiveResponse,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.DSDC_AUDIT.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDACaseLabelForSymAlignmentWithRoutingLabelType() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers =
        getMockReceivedDAContainersToSwitchLabelType("R8000", Boolean.FALSE);

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_STORE))
            .findFirst()
            .get()
            .getValue()
            .equals(RdcConstants.SYM_ROUTING_LABEL));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  @Test
  public void testGenerateDACaseLabelForSymAlignmentWithShippingLabelType() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int receivedQty = 1;
    long printJobId = 1;
    List<ReceivedContainer> receivedContainers =
        getMockReceivedDAContainersToSwitchLabelType("R8000", Boolean.TRUE);

    Map<String, Object> result =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            receivedQty,
            receivedContainers,
            printJobId,
            MockHttpHeaders.getHeaders(),
            null,
            false);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertFalse(
        printLabelRequestList
            .get(0)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_STORE))
            .findFirst()
            .get()
            .getValue()
            .equals(RdcConstants.SYM_ROUTING_LABEL));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.DA_LBL_TTL);
  }

  private static List<ReceivedContainer> getMockReceivedDAContainers(String slot) {
    List<ReceivedContainer> receivedContainers = new ArrayList<ReceivedContainer>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<Destination>();
    Destination destination = new Destination();

    destination.setDivision("01");
    destination.setSlot(slot);
    destination.setSlot_size(0);
    destination.setStore("01234");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPoNumber("0123456789");
    receivedContainer.setPoLine(1);
    receivedContainer.setReturnCode(200);
    receivedContainer.setMessage("test");
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setCarton("012340101234012345");
    receivedContainer.setBatch(123);
    receivedContainer.setShippingLane(1);
    receivedContainer.setReceiver(123456);
    receivedContainer.setPack(6);
    receivedContainer.setDepartment(1);
    receivedContainer.setDivision(12);
    receivedContainer.setPocode(33);
    receivedContainer.setAisle(" ");
    receivedContainer.setPoevent("TEST");
    receivedContainer.setStorezone(" ");
    receivedContainer.setEventchar(null);
    receivedContainer.setPri_loc("A12-3|");
    receivedContainer.setSec_loc("B56-7");
    receivedContainer.setTer_loc("C89-1");
    receivedContainer.setTag("96123456123");
    receivedContainer.setId("ABCD1234");
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());

    receivedContainers.add(0, receivedContainer);
    return receivedContainers;
  }

  private static List<ReceivedContainer> getMockReceivedDAContainersForMFC(String slot) {
    List<ReceivedContainer> receivedContainers = new ArrayList<ReceivedContainer>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<Destination>();
    Destination destination = new Destination();

    destination.setDivision("01");
    destination.setSlot(slot);
    destination.setSlot_size(0);
    destination.setStore("01234");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPoNumber("0123456789");
    receivedContainer.setPoLine(1);
    receivedContainer.setReturnCode(200);
    receivedContainer.setMessage("test");
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setCarton("012340101234012345");
    receivedContainer.setBatch(123);
    receivedContainer.setShippingLane(1);
    receivedContainer.setReceiver(123456);
    receivedContainer.setPack(6);
    receivedContainer.setDepartment(1);
    receivedContainer.setDivision(12);
    receivedContainer.setPocode(33);
    receivedContainer.setAisle(" ");
    receivedContainer.setPoevent("TEST");
    receivedContainer.setStorezone(" ");
    receivedContainer.setEventchar(null);
    receivedContainer.setPri_loc("A12-3|");
    receivedContainer.setSec_loc("B56-7");
    receivedContainer.setTer_loc("C89-1");
    receivedContainer.setTag("96123456123");
    receivedContainer.setId("ABCD1234");
    receivedContainer.setDestType("MFC");
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());

    receivedContainers.add(0, receivedContainer);
    return receivedContainers;
  }

  private static List<ReceivedContainer> getMockReceivedDAContainersForInductItems(String slot) {
    List<ReceivedContainer> receivedContainers = new ArrayList<ReceivedContainer>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<Destination>();
    Destination destination = new Destination();

    destination.setDivision("01");
    destination.setSlot(slot);
    destination.setSlot_size(0);
    destination.setStore("01234");
    destination.setZone("3");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPoNumber("0123456789");
    receivedContainer.setPoLine(1);
    receivedContainer.setReturnCode(200);
    receivedContainer.setMessage("test");
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setCarton("012340101234012345");
    receivedContainer.setBatch(123);
    receivedContainer.setShippingLane(1);
    receivedContainer.setReceiver(123456);
    receivedContainer.setPack(6);
    receivedContainer.setDepartment(1);
    receivedContainer.setDivision(12);
    receivedContainer.setPocode(33);
    receivedContainer.setPoevent("TEST");
    receivedContainer.setEventchar(null);
    receivedContainer.setTag("96123456123");
    receivedContainer.setId("ABCD1234");

    receivedContainers.add(0, receivedContainer);
    return receivedContainers;
  }

  private static List<ReceivedContainer> getMockReceivedDAContainersWithStoreZoneAndAisle(
      String slot) {
    List<ReceivedContainer> receivedContainers = new ArrayList<ReceivedContainer>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<Destination>();
    Destination destination = new Destination();

    destination.setDivision("01");
    destination.setSlot(slot);
    destination.setSlot_size(0);
    destination.setStore("01234");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPoNumber("0123456789");
    receivedContainer.setPoLine(1);
    receivedContainer.setReturnCode(200);
    receivedContainer.setMessage("test");
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setCarton("012340101234012345");
    receivedContainer.setBatch(123);
    receivedContainer.setShippingLane(1);
    receivedContainer.setReceiver(123456);
    receivedContainer.setPack(6);
    receivedContainer.setDepartment(1);
    receivedContainer.setDivision(12);
    receivedContainer.setPocode(33);
    receivedContainer.setAisle("R");
    receivedContainer.setPoevent("TEST");
    receivedContainer.setStorezone("L");
    receivedContainer.setEventchar(null);
    receivedContainer.setPri_loc("A12-3|");
    receivedContainer.setSec_loc("B56-7");
    receivedContainer.setTer_loc("C89-1");
    receivedContainer.setTag("96123456123");
    receivedContainer.setId("ABCD1234");

    receivedContainers.add(0, receivedContainer);
    return receivedContainers;
  }

  private static DsdcReceiveResponse getMockReceivedDsdcContainers(String slot, String audit_flag) {
    DsdcReceiveResponse dsdcReceiveResponse = new DsdcReceiveResponse();
    dsdcReceiveResponse.setAuditFlag(audit_flag);
    dsdcReceiveResponse.setPocode("73");
    dsdcReceiveResponse.setBatch("123");
    dsdcReceiveResponse.setDccarton("12345123456");
    dsdcReceiveResponse.setDept("01");
    dsdcReceiveResponse.setDiv("23");
    dsdcReceiveResponse.setErrorCode("0");
    dsdcReceiveResponse.setEvent("TEST");
    dsdcReceiveResponse.setHazmat("N");
    dsdcReceiveResponse.setLabel_bar_code("123451212345123456");
    dsdcReceiveResponse.setLane_nbr("12");
    dsdcReceiveResponse.setMessage("Hello, world!");
    dsdcReceiveResponse.setPacks("1234567890123456789012345678");
    dsdcReceiveResponse.setRcvr_nbr("123456");
    dsdcReceiveResponse.setScanned("1");
    dsdcReceiveResponse.setUnscanned("0");
    dsdcReceiveResponse.setSlot(slot);
    dsdcReceiveResponse.setSneEnabled("Y");
    dsdcReceiveResponse.setStore("12345");
    return dsdcReceiveResponse;
  }

  private static List<ReceivedContainer> getMockReceivedDAContainersToSwitchLabelType(
      String slot, Boolean shippingLabelType) {
    List<ReceivedContainer> receivedContainers = new ArrayList<ReceivedContainer>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<Destination>();
    Destination destination = new Destination();

    destination.setDivision("01");
    destination.setSlot(slot);
    destination.setSlot_size(0);
    destination.setStore("01234");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPoNumber("0123456789");
    receivedContainer.setPoLine(1);
    receivedContainer.setReturnCode(200);
    receivedContainer.setMessage("test");
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setCarton("012340101234012345");
    receivedContainer.setBatch(123);
    receivedContainer.setShippingLane(1);
    receivedContainer.setReceiver(123456);
    receivedContainer.setPack(6);
    receivedContainer.setDepartment(1);
    receivedContainer.setDivision(12);
    receivedContainer.setPocode(33);
    receivedContainer.setAisle(" ");
    receivedContainer.setPoevent("TEST");
    receivedContainer.setStorezone(" ");
    receivedContainer.setEventchar(null);
    receivedContainer.setPri_loc("A12-3|");
    receivedContainer.setSec_loc("B56-7");
    receivedContainer.setTer_loc("C89-1");
    receivedContainer.setTag("96123456123");
    receivedContainer.setId("ABCD1234");
    if (shippingLabelType) {
      receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    } else {
      receivedContainer.setRoutingLabel(true);
    }

    receivedContainers.add(0, receivedContainer);
    return receivedContainers;
  }

  @Test
  public void testReprintLabelForConReceived() throws IOException, ReceivingException {
    Map<String, Object> printJob =
        LabelGenerator.reprintLabel(
            getReceiveExceptionRequest(""),
            getMockMirageExceptionResponse(),
            null,
            MockHttpHeaders.getHeaders(),
            false);
    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) printJob.get(ReceivingConstants.PRINT_REQUEST_KEY);
    PrintLabelRequest printLabelRequest = printLabelRequests.get(0);
    assertEquals(printLabelRequests.size(), 1);
    assertEquals(printLabelRequest.getPrintJobId(), "550000000");
    assertEquals(printLabelRequest.getLabelIdentifier(), "099970200000087625");
  }

  private ReceiveExceptionRequest getReceiveExceptionRequest(String exceptionMessage) {
    return ReceiveExceptionRequest.builder()
        .exceptionMessage(exceptionMessage)
        .receiver("123456")
        .lpns(Collections.singletonList("1234567890"))
        .itemNumber(550000000)
        .slot("R8000")
        .deliveryNumbers(Collections.singletonList("345123"))
        .tokenId("12345")
        .build();
  }

  private MirageExceptionResponse getMockMirageExceptionResponse() throws IOException {
    MirageExceptionResponse mirageExceptionResponse = new MirageExceptionResponse();
    mirageExceptionResponse.setColor(null);
    mirageExceptionResponse.setDesc("EQ TEST 2CT");
    mirageExceptionResponse.setDeliveryNumber("123456");
    mirageExceptionResponse.setItemNumber(123456L);
    mirageExceptionResponse.setItemReceived(true);
    mirageExceptionResponse.setLpn("1234567890");
    mirageExceptionResponse.setContainerType("RECEIPT");
    mirageExceptionResponse.setPackinfo(null);
    mirageExceptionResponse.setPurchaseReferenceNumber("2579170260");
    mirageExceptionResponse.setPurchaseReferenceLineNumber(1);
    mirageExceptionResponse.setStoreInfo(
        MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived().get(0));
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setSlot("P1001");
    mirageExceptionResponse.getStoreInfo().getDestinations().get(0).setZone("12");
    mirageExceptionResponse.setVendorPack(4);
    mirageExceptionResponse.setWarehousePack(2);
    mirageExceptionResponse.setRdsHandlingCode("C");
    mirageExceptionResponse.setRdsPackTypeCode("C");
    return mirageExceptionResponse;
  }

  @Test
  public void test_populateSSTKRePalletLabel_split_pallet() throws IOException {
    ContainerItemDetails containerItemDetails =
        MockDeliveryDocuments.getInventoryContainerDetails_withChannelType();
    PrintLabelRequest printLabelRequest =
        LabelGenerator.populateReprintPalletSSTKLabel(containerItemDetails, labelAttributes, true);
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && LabelConstants.LBL_SPLIT_PALLET.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(StringUtils.EMPTY));

    assertTrue(
        printLabelRequest
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
  }

  @Test
  public void test_populateSSTKRePalletLabel_split_pallet_NoChannelType() throws IOException {
    ContainerItemDetails containerItemDetails =
        MockDeliveryDocuments.getInventoryContainerDetails_withOutChannelType();
    PrintLabelRequest printLabelRequest =
        LabelGenerator.populateReprintPalletSSTKLabel(containerItemDetails, labelAttributes, true);

    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && StringUtils.EMPTY.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_POLINE.equals(e.getKey())
                        && String.valueOf(1).equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_NUM))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(1)));
    assertTrue(
        printLabelRequest
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
  }

  public void test_populateSSTKRePalletLabel_Non_Split_pallet_N0_PoLine() throws IOException {
    ContainerItemDetails containerItemDetails =
        MockDeliveryDocuments
            .getInventoryContainerDetails_withOutChannelType_WithoutPoLineDetails();
    PrintLabelRequest printLabelRequest =
        LabelGenerator.populateReprintPalletSSTKLabel(containerItemDetails, labelAttributes, false);

    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && LabelConstants.LBL_PARTIAL.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_POLINE.equals(e.getKey())
                        && StringUtils.EMPTY.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_USERID))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(123)));
    assertTrue(
        printLabelRequest.getFormatName().equalsIgnoreCase(LabelFormat.ATLAS_RDC_SSTK.getFormat()));
  }

  @Test
  public void test_populateSSTKRePalletLabel_split_pallet_isSplitPallet_False() throws IOException {
    ContainerItemDetails containerItemDetails =
        MockDeliveryDocuments.getInventoryContainerDetails_withChannelType();
    PrintLabelRequest printLabelRequest =
        LabelGenerator.populateReprintPalletSSTKLabel(containerItemDetails, labelAttributes, false);

    assertFalse(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && StringUtils.EMPTY.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_POLINE.equals(e.getKey())
                        && String.valueOf(1).equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_NUM))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(1)));
    assertTrue(
        printLabelRequest
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
  }

  @Test
  public void testGenerateAtlasDsdcPackLabel() throws Exception {
    long printJobId = 1;
    InstructionRequest instructionRequest = mockInstructionForDSDCPacks();
    List<DeliveryDocument> gdmDeliveryDocumentList = mockDeliveryDocument();
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("097123456");
    receivedContainer1.setParentTrackingId("E23434576534576");
    receivedContainer1.setPoNumber("MOCK_PO");
    receivedContainer1.setPoLine(1);
    receivedContainer1.setReceiver(1);
    receivedContainers.add(receivedContainer1);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("097123457");
    receivedContainer2.setParentTrackingId("E23434576534576");
    receivedContainer2.setPoNumber("MOCK_PO");
    receivedContainer2.setPoLine(2);
    receivedContainer2.setReceiver(2);
    receivedContainers.add(receivedContainer2);
    ReceivedContainer parentReceivedContainer = new ReceivedContainer();
    parentReceivedContainer.setLabelTrackingId("E23434576534576");
    parentReceivedContainer.setPoNumber("MOCK_PO");
    parentReceivedContainer.setPoLine(2);
    parentReceivedContainer.setReceiver(2);
    parentReceivedContainer.setShippingLane(18);
    parentReceivedContainer.setBatch(4);
    receivedContainers.add(parentReceivedContainer);
    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    DsdcReceiveResponse dsdcReceiveResponse = getMockReceivedDsdcContainers("R8002", "");

    Map<String, Object> result =
        LabelGenerator.generateAtlasDsdcPackLabel(
            instructionRequest,
            gdmDeliveryDocumentList,
            parentReceivedContainer,
            false,
            printJobId,
            httpHeaders,
            null);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_DSDC.getFormat()));
  }

  private InstructionRequest mockInstructionForDSDCPacks() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);
    instructionRequest.setSscc("43432323");
    instructionRequest.setDeliveryNumber("21688370");
    instructionRequest.setDoorNumber(ReceivingConstants.DEFAULT_DOOR);
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    return instructionRequest;
  }

  private List<DeliveryDocument> mockDeliveryDocument() throws Exception {
    Type listType = new TypeToken<List<DeliveryDocument>>() {}.getType();
    return new Gson()
        .fromJson(readFileData("dsdc_receive_pack_mock_data/gdm_response.json"), listType);
  }

  private String readFileData(String path) throws IOException {
    File resource = new ClassPathResource(path).getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  @Test
  public void test_generatePalletLabels_split_pallet_AtlasItems_With_DeliveryNumber()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            deliveryDocuments.get(0).getDeliveryNumber(),
            LabelFormat.ATLAS_RDC_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && LabelConstants.LBL_SPLIT_PALLET.equals(e.getValue())));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_RCVR))
            .findFirst()
            .get()
            .getValue()
            .equals(StringUtils.EMPTY));
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_SSTK.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_SSTK.getFormat()));
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(1).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(2).getTtlInHours(), RdcConstants.LBL_TTL);

    assertTrue(
        printLabelRequestList
            .stream()
            .anyMatch(
                labelRequest ->
                    Optional.ofNullable(labelRequest.getData())
                        .orElse(Collections.emptyList())
                        .stream()
                        .anyMatch(
                            labelData ->
                                LabelConstants.LBL_DELIVERY_NUMBER.equalsIgnoreCase(
                                        labelData.getKey())
                                    && labelData
                                        .getValue()
                                        .equals(
                                            String.valueOf(
                                                deliveryDocuments.get(0).getDeliveryNumber())))));
  }

  @Test
  public void test_generatePalletLabels_With_FreightType_SSTK() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            null,
            LabelFormat.ATLAS_RDC_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_FREIGHT_TYPE))
            .findFirst()
            .get()
            .getValue()
            .equalsIgnoreCase(SLOTTING_SSTK_RECEIVING_METHOD));
  }

  @Test
  public void test_generatePalletLabels_With_FreightType_DA() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            null,
            LabelFormat.ATLAS_RDC_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_FREIGHT_TYPE))
            .findFirst()
            .get()
            .getValue()
            .equalsIgnoreCase(SLOTTING_DA_RECEIVING_METHOD));
  }

  @Test
  public void test_generatePalletLabels_With_Empty_PurchaseRefType() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseRefType("");
    Map<String, Object> result =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            Integer.valueOf(0),
            CommonLabelDetails.builder().build(),
            Long.valueOf(1),
            MockHttpHeaders.getHeaders(),
            null,
            true,
            null,
            LabelFormat.ATLAS_RDC_SSTK);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertFalse(
        printLabelRequestList
            .get(1)
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_FREIGHT_TYPE))
            .findFirst()
            .isPresent());
  }

  @Test
  public void test_populateSSTKRePalletLabel_split_pallet_NoDeliveryNumber() throws IOException {
    ContainerItemDetails containerItemDetails =
        MockDeliveryDocuments.getInventoryContainerDetails_withOutChannelType();
    containerItemDetails.setDeliveryNumber(null);
    PrintLabelRequest printLabelRequest =
        LabelGenerator.populateReprintPalletSSTKLabel(containerItemDetails, labelAttributes, true);

    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_PARTIAL_TAG.equals(e.getKey())
                        && StringUtils.EMPTY.equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .anyMatch(
                e ->
                    LabelConstants.LBL_POLINE.equals(e.getKey())
                        && String.valueOf(1).equals(e.getValue())));
    assertTrue(
        printLabelRequest
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals(LabelConstants.LBL_NUM))
            .findFirst()
            .get()
            .getValue()
            .equals(String.valueOf(1)));
    assertTrue(
        printLabelRequest
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
  }

  @Test
  public void testGenerateDAPalletLabels() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForDACasePackAutomationSlotting();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setTrackingId("0123456789012345678");
    Map<String, Integer> trackingIdQuantityMap = new HashMap<>();
    List<ReceivedContainer> receivedContainerList = getMockReceivedContainerList();
    Map<ReceivedContainer, PrintJob> receivedContainerPrintJobMap = new HashMap<>();

    ReceivedContainer receivedContainer = receivedContainerList.get(0);
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    receivedContainerPrintJobMap.put(receivedContainer, printJob);
    trackingIdQuantityMap.put(receivedContainer.getLabelTrackingId(), 1);

    ReceivedContainer receivedContainer2 = receivedContainerList.get(1);
    PrintJob printJob2 = new PrintJob();
    printJob2.setId(232324L);
    printJob2.setInstructionId(Long.valueOf(1235));
    receivedContainerPrintJobMap.put(receivedContainer2, printJob2);
    trackingIdQuantityMap.put(receivedContainer2.getLabelTrackingId(), 1);

    ReceivedContainer receivedContainer3 = receivedContainerList.get(2);
    PrintJob printJob3 = new PrintJob();
    printJob3.setId(232325L);
    printJob3.setInstructionId(Long.valueOf(1236));
    receivedContainerPrintJobMap.put(receivedContainer3, printJob3);
    trackingIdQuantityMap.put(receivedContainer3.getLabelTrackingId(), 1);

    Map<String, Object> result =
        LabelGenerator.generateDAPalletLabels(
            deliveryDocumentLine,
            trackingIdQuantityMap,
            receivedContainerList,
            receivedContainerPrintJobMap,
            MockHttpHeaders.getHeaders(),
            null,
            LabelFormat.ATLAS_RDC_PALLET);
    assertTrue(result.containsKey("printRequests"));
    List<PrintLabelRequest> printLabelRequestList = (ArrayList) result.get("printRequests");
    assertTrue(
        printLabelRequestList
            .get(0)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.SSTK_TIMESTAMP.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(1)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
    assertTrue(
        printLabelRequestList
            .get(2)
            .getFormatName()
            .equalsIgnoreCase(LabelFormat.ATLAS_RDC_PALLET.getFormat()));
    assertEquals(
        printLabelRequestList.get(4).getLabelIdentifier(),
        printLabelRequestList.get(5).getLabelIdentifier());
    assertEquals(printLabelRequestList.get(0).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(1).getTtlInHours(), RdcConstants.LBL_TTL);
    assertEquals(printLabelRequestList.get(2).getTtlInHours(), RdcConstants.LBL_TTL);
  }

  private List<ReceivedContainer> getMockReceivedContainerList() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setDivision("01");
    destination.setSlot("A120");
    destination.setSlot_size(0);
    destination.setStore("01234");
    destinations.add(0, destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelTrackingId("0123456789012345678");
    receivedContainer.setReceiver(123456);
    receivedContainers.add(0, receivedContainer);

    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    List<Destination> destinations2 = new ArrayList<>();
    Destination destination2 = new Destination();
    destination2.setDivision("01");
    destination2.setSlot("A120");
    destination2.setSlot_size(0);
    destination2.setStore("01234");
    destinations2.add(0, destination);
    receivedContainer2.setDestinations(destinations2);
    receivedContainer2.setLabelTrackingId("0123456789012345679");
    receivedContainer2.setReceiver(123456);
    receivedContainers.add(1, receivedContainer2);

    ReceivedContainer receivedContainer3 = new ReceivedContainer();
    List<Destination> destinations3 = new ArrayList<>();
    Destination destination3 = new Destination();
    destination3.setDivision("01");
    destination3.setSlot("A120");
    destination3.setSlot_size(0);
    destination3.setStore("01234");
    destinations3.add(0, destination);
    receivedContainer3.setDestinations(destinations3);
    receivedContainer3.setLabelTrackingId("0123456789012345680");
    receivedContainer3.setReceiver(123456);
    receivedContainers.add(2, receivedContainer3);
    return receivedContainers;
  }
}
