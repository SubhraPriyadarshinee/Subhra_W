package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_CREATE_TS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DATE_FORMAT_ISO8601;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.CancelledInstructionResponse;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.model.MultipleTransferInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.model.ReceiveAllRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveAllResponse;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.TransferInstructionRequest;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.DefaultInstructionSearchRequestHandler;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.ReceiveInstructionHandler;
import com.walmart.move.nim.receiving.core.service.RefreshInstructionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
public class InstructionControllerTest extends ReceivingControllerTestBase {
  @Autowired private MockMvc mockMvc;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  @Autowired
  @Mock
  private InstructionService instructionService;

  @Resource(name = GdcConstants.WITRON_INSTRUCTION_SERVICE)
  @Autowired
  @Mock
  private InstructionService witronInstructionService;

  @Qualifier(ReceivingConstants.GDC_RECEIVE_INSTRUCTION_HANDLER)
  @Autowired
  @Mock
  private ReceiveInstructionHandler gdcReceiveInstructionHandler;

  @Qualifier(ReceivingConstants.GDC_REFRESH_INSTRUCTION_HANDLER)
  @Autowired
  @Mock
  private RefreshInstructionHandler gdcRefreshInstructionHandler;

  @Autowired
  @Mock
  @Qualifier(ReceivingConstants.CC_RECEIVE_INSTRUCTION_HANDLER)
  private RefreshInstructionHandler ccReceiveInstructionHandler;

  @Autowired @Mock private InstructionPersisterService instructionPersisterService;
  @Autowired @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired @Mock
  private DefaultInstructionSearchRequestHandler defaultInstructionSearchRequestHandler;

  @Resource @Mock RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;

  private List<InstructionSummary> instructionSummaryList;
  private List<Instruction> instructionsList = new ArrayList<>();

  public Gson gson;
  Instruction instruction1;
  Instruction instruction2;
  Map<String, Object> caseLabelsForDA;
  InstructionResponse instructionResponse;
  InstructionRequest instructionRequest;

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));

    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");

    Map<String, Object> item = new HashMap<String, Object>();
    item.put("value", "550129241");
    item.put("key", "ITEM");

    Map<String, Object> destination = new HashMap<String, Object>();
    item.put("value", "07026 US");
    item.put("key", "DESTINATION");

    Map<String, Object> upcbar = new HashMap<String, Object>();
    item.put("value", "00016017039630");
    item.put("key", "UPCBAR");

    Map<String, Object> lpn = new HashMap<String, Object>();
    item.put("value", "2803897407964380");
    item.put("key", "LPN");

    Map<String, Object> fullUserID = new HashMap<String, Object>();
    item.put("value", "rlp004v");
    item.put("key", "FULLUSERID");

    Map<String, Object> type = new HashMap<String, Object>();
    item.put("value", "DA");
    item.put("key", "TYPE");

    Map<String, Object> desc1 = new HashMap<String, Object>();
    item.put("value", "TR 12QT STCKPT SS   ");
    item.put("key", "DESC1");

    List<Map<String, Object>> dataArrayList =
        Arrays.asList(item, destination, upcbar, lpn, fullUserID, type, desc1);

    Map<String, Object> mapCtrLabel = new HashMap<>();
    mapCtrLabel.put("ttlInHours", 72);
    mapCtrLabel.put("labelIdentifier", "a328990000000000000106509");
    mapCtrLabel.put("clientId", "OF");
    mapCtrLabel.put("clientID", "OF");
    mapCtrLabel.put("formatId", "pallet_lpn_format");
    mapCtrLabel.put("formatID", "pallet_lpn_format");
    /*
     * Both Data and label data are same value returned by OF.
     */
    mapCtrLabel.put("data", dataArrayList);
    mapCtrLabel.put("labelData", dataArrayList);

    Map<String, Object> mapCtrLabelForDACase = new HashMap<>();
    mapCtrLabelForDACase.put("ttlInHours", 72);
    mapCtrLabelForDACase.put("labelIdentifier", "2803897406677828");
    mapCtrLabelForDACase.put("clientId", "OF");
    mapCtrLabelForDACase.put("clientID", "OF");
    mapCtrLabelForDACase.put("formatId", "case_lpn_format");
    mapCtrLabelForDACase.put("formatID", "case_lpn_format");
    /*
     * Both Data and label data are same value returned by OF.
     */
    mapCtrLabelForDACase.put("data", dataArrayList);
    mapCtrLabelForDACase.put("labelData", dataArrayList);

    caseLabelsForDA = mapCtrLabelForDACase;

    Map<String, String> mapCtrDestination = new HashMap<>();
    mapCtrDestination.put("countryCode", "US");
    mapCtrDestination.put("buNumber", "6012");

    Map<String, String> itemMap = new HashMap<>();
    itemMap.put("financialReportingGroup", "US");
    itemMap.put("baseDivisionCode", "WM");
    itemMap.put("itemNbr", "1084445");

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(5);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    distribution1.setItem(itemMap);

    Distribution distribution2 = new Distribution();
    distribution2.setAllocQty(5);
    distribution2.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c4");
    distribution2.setItem(itemMap);

    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);
    distributions.add(distribution2);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOrgUnitId(1);
    containerDetails.setProjectedWeightUom(ReceivingConstants.Uom.VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("PICKED");
    containerDetails.setCtrShippable(true);
    containerDetails.setCtrReusable(false);
    containerDetails.setQuantity(10);
    containerDetails.setCtrDestination(mapCtrDestination);
    containerDetails.setDistributions(distributions);
    containerDetails.setCtrLabel(mapCtrLabel);

    instruction1 = new Instruction();
    instruction1.setId(1l);
    instruction1.setContainer(containerDetails);
    instruction1.setChildContainers(null);
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setCompleteTs(new Date());
    instruction1.setCompleteUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setGtin("00000943037194");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE PACK (4)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction1.setMove(move);
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(true);
    instruction1.setPurchaseReferenceNumber("9763140004");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId("DA");
    instruction1.setDeliveryDocument(
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

    List<ContainerDetails> childContainerDetails = new ArrayList<>();
    childContainerDetails.add(Mockito.mock(ContainerDetails.class));

    instruction2 = new Instruction();
    instruction2.setId(2L);
    instruction2.setContainer(containerDetails);
    instruction2.setChildContainers(null);
    instruction2.setCreateTs(new Date());
    instruction2.setCreateUserId("sysadmin");
    instruction2.setLastChangeTs(new Date());
    instruction2.setLastChangeUserId("sysadmin");
    instruction2.setDeliveryNumber(Long.valueOf("21119003"));
    instruction2.setGtin("00000943037204");
    instruction2.setInstructionCode("Build Container");
    instruction2.setInstructionMsg("Build the Container");
    instruction2.setItemDescription("HEM VALUE PACK (4)");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction2.setMove(move);
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(true);
    instruction2.setPurchaseReferenceNumber("9763140004");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(2);
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setReceivedQuantity(1);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setProviderId("DA");
    instruction2.setDeliveryDocument(
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

    instructionsList.add(instruction1);
    instructionsList.add(instruction2);

    instructionSummaryList = new ArrayList<>();

    InstructionSummary instructionSummary1 = new InstructionSummary();
    instructionSummary1.setCreateTs(new Date());
    instructionSummary1.setCreateUserId("sysadmin");
    instructionSummary1.setCompleteTs(new Date());
    instructionSummary1.setCompleteUserId("sysadmin");
    instructionSummary1.setGtin("00000943037204");
    instructionSummary1.setId(1l);
    instructionSummary1.setInstructionData(null);
    instructionSummary1.setItemDescription("HEM VALUE PACK (4)");
    instructionSummary1.setPoDcNumber("32899");
    instructionSummary1.setProjectedReceiveQty(2);
    instructionSummary1.setProjectedReceiveQtyUOM("ZA");
    instructionSummary1.setPurchaseReferenceNumber("9763140104");
    instructionSummary1.setPurchaseReferenceLineNumber(1);
    instructionSummary1.setReceivedQuantity(2);
    instructionSummary1.setReceivedQuantityUOM("ZA");

    InstructionSummary instructionSummary2 = new InstructionSummary();
    instructionSummary2.setCreateTs(new Date());
    instructionSummary2.setCreateUserId("sysadmin");
    instructionSummary2.setGtin("00000943037204");
    instructionSummary2.setId(1l);
    instructionSummary2.setInstructionData(null);
    instructionSummary2.setItemDescription("HEM VALUE PACK (4)");
    instructionSummary2.setPoDcNumber("32899");
    instructionSummary2.setProjectedReceiveQty(4);
    instructionSummary2.setProjectedReceiveQtyUOM("ZA");
    instructionSummary2.setPurchaseReferenceNumber("9763140104");
    instructionSummary2.setPurchaseReferenceLineNumber(1);
    instructionSummary2.setReceivedQuantity(2);
    instructionSummary2.setReceivedQuantityUOM("ZA");

    instructionSummaryList.add(instructionSummary1);
    instructionSummaryList.add(instructionSummary2);
    // Instruction Request
    instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231313");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber(null);
    // Instruction Response
    InstructionResponseImplNew instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(instruction1);
    instructionResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionResponse.setPrintJob(null);

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06938");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoTypeCode(33);
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setOrderableQuantity(9);
    deliveryDocumentLine.setWarehousePackQuantity(9);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWeightFormatTypeCode("F");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    instructionResponse.setDeliveryDocuments(deliveryDocuments);
    this.instructionResponse = instructionResponse;
  }

  @AfterMethod
  public void tearDown() {
    reset(instructionService);
  }

  @Test
  public void testInstructions() throws Exception {

    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    doReturn(defaultInstructionSearchRequestHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doReturn(instructionSummaryList)
        .when(defaultInstructionSearchRequestHandler)
        .getInstructionSummary(any(InstructionSearchRequest.class), any(Map.class));
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(Long.valueOf("21119003"));
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/instructions/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(gson.toJson(instructionSearchRequest))
                    .headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Type listType =
        new TypeToken<List<InstructionSummary>>() {

          private static final long serialVersionUID = 1L;
        }.getType();
    List<InstructionSummary> instructionSummaryResponse =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                new JsonDeserializer<Date>() {
                  public Date deserialize(
                      JsonElement jsonElement, Type type, JsonDeserializationContext context)
                      throws JsonParseException {
                    return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                  }
                })
            .create()
            .fromJson(response, listType);
    assertEquals(instructionSummaryResponse.size(), instructionSummaryList.size());
  }

  @Test
  public void testGetInstruction() throws Exception {
    when(instructionService.getInstructionById(any(Long.class))).thenReturn(instruction1);
    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    String response =
        mockMvc
            .perform(MockMvcRequestBuilders.get("/instructions/1").headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Instruction instruction =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                new JsonDeserializer<Date>() {
                  public Date deserialize(
                      JsonElement jsonElement, Type type, JsonDeserializationContext context)
                      throws JsonParseException {
                    return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                  }
                })
            .create()
            .fromJson(response, Instruction.class);
    assertEquals(instruction.getId(), instruction1.getId());
  }

  /**
   * Test for create instruction request, pass instruction request the API should return instruction
   * response having instruction as one key and value
   *
   * @throws Exception
   */
  @Test
  public void testInstructionRequest() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    try {
      doReturn(instructionService)
          .when(tenantSpecificConfigReader)
          .getInstructionServiceByFacility(any());
      when(instructionService.serveInstructionRequest(any(), any(HttpHeaders.class)))
          .thenReturn(instructionResponse);
      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/instructions/request")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(instructionRequest))
                      .headers(httpHeaders))
              .andExpect(status().isCreated())
              .andExpect(content().contentType("application/json"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotEquals(response, null);
      InstructionResponseImplNew instructionResponse =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  new JsonDeserializer<Date>() {
                    public Date deserialize(
                        JsonElement jsonElement, Type type, JsonDeserializationContext context)
                        throws JsonParseException {
                      return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                    }
                  })
              .create()
              .fromJson(response, InstructionResponseImplNew.class);
      assertNotEquals(instructionResponse.getInstruction(), null);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  /**
   * Test for complete instruction. If valid instruction id is not provided the will throw HTTP 400.
   * If any validation error occurs then also will throw HTTP 400.
   *
   * @throws Exception
   */
  @Test
  public void testCompleteInstruction() throws Exception {
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPrinterId(101);

    InstructionResponseImplNew instructionResponseExpected =
        new InstructionResponseImplNew(
            null, null, instruction1, instruction1.getContainer().getCtrLabel());

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    when(tenantSpecificConfigReader.getInstructionServiceByFacility(anyString()))
        .thenReturn(instructionService);

    when(instructionService.completeInstruction(
            any(Long.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(instructionResponseExpected);

    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/instructions/1/complete")
                    .content(gson.toJson(completeInstructionRequest))
                    .headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    InstructionResponseImplNew instructionResponseActual =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(response, InstructionResponseImplNew.class);

    assertEquals(
        instructionResponseActual.getPrintJob().size(),
        instructionResponseExpected.getPrintJob().size());

    when(instructionService.completeInstruction(
            any(Long.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/instructions/1/complete")
                .content(gson.toJson(completeInstructionRequest))
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/instructions/null/complete")
                .content(gson.toJson(completeInstructionRequest))
                .headers(httpHeaders))
        .andExpect(status().isBadRequest());

    verify(instructionService, times(2))
        .completeInstruction(
            any(Long.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testCancelInstruction() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(tenantSpecificConfigReader.getInstructionServiceByFacility(anyString()))
        .thenReturn(instructionService);
    when(instructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(
            InstructionUtils.convertToInstructionSummaryResponseList(Arrays.asList(instruction1))
                .get(0));

    String response =
        mockMvc
            .perform(MockMvcRequestBuilders.put("/instructions/1/cancel").headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    CancelledInstructionResponse cancelledInstructionResponse =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(response, CancelledInstructionResponse.class);

    assertEquals(
        instruction1.getId(), cancelledInstructionResponse.getCancelledInstruction().get_id());
    assertEquals(
        instruction1.getId(), cancelledInstructionResponse.getCancelledInstruction().getId());
    assertEquals(
        instruction1.getCompleteTs(),
        cancelledInstructionResponse.getCancelledInstruction().getCompleteTs());
    assertEquals(
        instruction1.getCompleteUserId(),
        cancelledInstructionResponse.getCancelledInstruction().getCompleteUserId());
    assertEquals(
        instruction1.getReceivedQuantity(),
        cancelledInstructionResponse.getCancelledInstruction().getReceivedQuantity().intValue());
    assertNotNull(instruction1.getMove());

    when(instructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                "ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE));

    mockMvc
        .perform(MockMvcRequestBuilders.put("/instructions/1/cancel").headers(httpHeaders))
        .andExpect(status().isInternalServerError());

    mockMvc
        .perform(MockMvcRequestBuilders.put("/instructions/null/cancel").headers(httpHeaders))
        .andExpect(status().isBadRequest());

    verify(instructionService, times(2)).cancelInstruction(any(Long.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(2)).getInstructionServiceByFacility(anyString());
  }

  @Test
  public void testUpdateInstruction() {

    try {
      InstructionResponse instructionResponseExpected =
          new InstructionResponseImplNew(null, null, instruction2, caseLabelsForDA);
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
      httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

      DocumentLine documentLine = new DocumentLine();
      documentLine.setGtin("00016017039630");
      documentLine.setExpectedQty(10L);
      documentLine.setDeptNumber(14);
      documentLine.setPurchaseCompanyId(1);
      documentLine.setQuantityUOM("ZA");
      documentLine.setQuantity(1);
      documentLine.setPurchaseReferenceNumber("4763030211");
      documentLine.setPurchaseReferenceLineNumber(1);
      documentLine.setPurchaseRefType("CROSSU");
      documentLine.setPoDCNumber("6038");
      documentLine.setVnpkQty(1);
      documentLine.setWhpkQty(1);
      documentLine.setItemNumber(550129241L);
      documentLine.setWhpkSell(28.18);
      documentLine.setVendorPackCost(26.98);
      documentLine.setMaxOverageAcceptQty(15L);
      documentLine.setBaseDivisionCode("TEST");
      documentLine.setFinancialReportingGroupCode("TEST");
      documentLine.setVendorGS128("TEST");
      documentLine.setPoDeptNumber("TEST");
      documentLine.setTotalPurchaseReferenceQty(1);

      Map<String, String> facilityMap = new HashMap<>();
      facilityMap.put("buNumber", "6038");
      facilityMap.put("countryCode", "US");

      UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
      updateInstructionRequest.setDoorNumber("123");
      updateInstructionRequest.setDeliveryNumber(21231313L);
      updateInstructionRequest.setFacility(facilityMap);
      updateInstructionRequest.setDeliveryDocumentLines(Arrays.asList(documentLine));

      doReturn(instructionService)
          .when(tenantSpecificConfigReader)
          .getInstructionServiceByFacility(any());
      doReturn(instructionResponseExpected)
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));
      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.put("/instructions/2/update")
                      .content(gson.toJson(updateInstructionRequest))
                      .headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      InstructionResponseImplNew instructionResponseActual =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  (JsonDeserializer<Date>)
                      (jsonElement, type, context) ->
                          new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
              .create()
              .fromJson(response, InstructionResponseImplNew.class);

      assertEquals(
          instructionResponseActual.getInstruction().getId(),
          instructionResponseExpected.getInstruction().getId());
      doThrow(
              new ReceivingException(
                  ReceivingException.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE))
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update")
                  .content(gson.toJson(updateInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isInternalServerError());
      doThrow(
              new ReceivingException(
                  ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE))
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update")
                  .content(gson.toJson(updateInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isInternalServerError());

      /*
       * With out HTTP headers will produce bad request.
       */
      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update")
                  .content(gson.toJson(updateInstructionRequest)))
          .andExpect(status().isBadRequest());

      /*
       * With out HTTP request payload will produce bad request.
       */
      mockMvc
          .perform(MockMvcRequestBuilders.put("/instructions/null/update").headers(httpHeaders))
          .andExpect(status().isBadRequest());

      verify(instructionService, times(3))
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testReceiveInstruction_HappyPath() throws Exception {
    InstructionResponse instructionResponseExpected =
        new InstructionResponseImplNew(null, null, instruction2, caseLabelsForDA);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    ReceiveInstructionRequest receiveInstructionRequest = getReceiveInstructionRequest();

    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    doReturn(instructionResponseExpected)
        .when(instructionService)
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/instructions/2/receive")
                    .content(gson.toJson(receiveInstructionRequest))
                    .headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    InstructionResponseImplNew instructionResponseActual =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(response, InstructionResponseImplNew.class);

    assertNotNull(instructionResponseActual);
    assertEquals(
        instructionResponseActual.getInstruction().getId(),
        instructionResponseExpected.getInstruction().getId());

    verify(instructionService, times(1))
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_OverageExceedException() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    ReceiveInstructionRequest receiveInstructionRequest = getReceiveInstructionRequest();

    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    doThrow(
            new ReceivingException(
                RdcConstants.RDC_OVERAGE_EXCEED_ERROR_MESSAGE,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.OVERAGE_ERROR_CODE))
        .when(instructionService)
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/instructions/2/receive")
                .content(gson.toJson(receiveInstructionRequest))
                .headers(httpHeaders))
        .andExpect(status().isInternalServerError());

    verify(instructionService, times(1))
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_NearOverageException() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    ReceiveInstructionRequest receiveInstructionRequest = getReceiveInstructionRequest();

    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    doThrow(
            new ReceivingException(
                ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE))
        .when(instructionService)
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/instructions/2/receive")
                .content(gson.toJson(receiveInstructionRequest))
                .headers(httpHeaders))
        .andExpect(status().isInternalServerError());

    verify(instructionService, times(1))
        .receiveInstruction(any(Long.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_BadInstructionRequest() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    mockMvc
        .perform(MockMvcRequestBuilders.put("/instructions/null/receive").headers(httpHeaders))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testUpdateInstructionWithParentTrackingId() {

    try {
      InstructionResponse instructionResponseExpected =
          new InstructionResponseImplNew(null, null, instruction2, caseLabelsForDA);
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
      httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

      DocumentLine documentLine = new DocumentLine();
      documentLine.setGtin("00016017039630");
      documentLine.setExpectedQty(10L);
      documentLine.setDeptNumber(14);
      documentLine.setPurchaseCompanyId(1);
      documentLine.setQuantityUOM("ZA");
      documentLine.setQuantity(1);
      documentLine.setPurchaseReferenceNumber("4763030211");
      documentLine.setPurchaseReferenceLineNumber(1);
      documentLine.setPurchaseRefType("CROSSU");
      documentLine.setPoDCNumber("6038");
      documentLine.setVnpkQty(1);
      documentLine.setWhpkQty(1);
      documentLine.setItemNumber(550129241L);
      documentLine.setWhpkSell(28.18);
      documentLine.setVendorPackCost(26.98);
      documentLine.setMaxOverageAcceptQty(15L);
      documentLine.setBaseDivisionCode("TEST");
      documentLine.setFinancialReportingGroupCode("TEST");
      documentLine.setVendorGS128("TEST");
      documentLine.setPoDeptNumber("TEST");
      documentLine.setTotalPurchaseReferenceQty(1);

      Map<String, String> facilityMap = new HashMap<>();
      facilityMap.put("buNumber", "6038");
      facilityMap.put("countryCode", "US");

      UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
      updateInstructionRequest.setDoorNumber("123");
      updateInstructionRequest.setDeliveryNumber(21231313L);
      updateInstructionRequest.setFacility(facilityMap);
      updateInstructionRequest.setDeliveryDocumentLines(Arrays.asList(documentLine));

      doReturn(instructionService)
          .when(tenantSpecificConfigReader)
          .getInstructionServiceByFacility(any());
      doReturn(instructionResponseExpected)
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));
      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.put("/instructions/1/update/123")
                      .content(gson.toJson(updateInstructionRequest))
                      .headers(httpHeaders))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      InstructionResponseImplNew instructionResponseActual =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  (JsonDeserializer<Date>)
                      (jsonElement, type, context) ->
                          new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
              .create()
              .fromJson(response, InstructionResponseImplNew.class);

      assertEquals(
          instructionResponseActual.getInstruction().getId(),
          instructionResponseExpected.getInstruction().getId());
      doThrow(
              new ReceivingException(
                  ReceivingException.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE))
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update/123")
                  .content(gson.toJson(updateInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isInternalServerError());
      doThrow(
              new ReceivingException(
                  ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE))
          .when(instructionService)
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update/123")
                  .content(gson.toJson(updateInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isInternalServerError());

      /*
       * With out HTTP headers will produce bad request.
       */
      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/2/update/123")
                  .content(gson.toJson(updateInstructionRequest)))
          .andExpect(status().isBadRequest());

      /*
       * With out HTTP request payload will produce bad request.
       */
      mockMvc
          .perform(MockMvcRequestBuilders.put("/instructions/null/update/123").headers(httpHeaders))
          .andExpect(status().isBadRequest());

      verify(instructionService, times(3))
          .updateInstruction(
              any(Long.class), any(UpdateInstructionRequest.class), any(), any(HttpHeaders.class));

    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testTransferInstructions() {

    try {
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
      httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");

      TransferInstructionRequest transferInstructionRequest = new TransferInstructionRequest();

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/transfer")
                  .content(gson.toJson(transferInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isBadRequest());

      transferInstructionRequest.setDeliveryNumber(Long.valueOf("21119003"));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/transfer")
                  .content(gson.toJson(transferInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isBadRequest());

      transferInstructionRequest.setUserIds(Arrays.asList("sysadmin"));

      when(instructionService.transferInstructions(transferInstructionRequest, httpHeaders))
          .thenReturn(instructionSummaryList);

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/transfer")
                  .content(gson.toJson(transferInstructionRequest))
                  .headers(httpHeaders))
          .andExpect(status().isOk());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testInstructionRequest_witron() throws Exception {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    try {
      doReturn(witronInstructionService)
          .when(tenantSpecificConfigReader)
          .getInstructionServiceByFacility(any());
      when(witronInstructionService.serveInstructionRequest(any(), any(HttpHeaders.class)))
          .thenReturn(instructionResponse);
      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/instructions/request")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(instructionRequest))
                      .headers(httpHeaders))
              .andExpect(status().isCreated())
              .andExpect(content().contentType("application/json"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotEquals(response, null);
      InstructionResponseImplNew instructionResponse =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  new JsonDeserializer<Date>() {
                    public Date deserialize(
                        JsonElement jsonElement, Type type, JsonDeserializationContext context)
                        throws JsonParseException {
                      return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                    }
                  })
              .create()
              .fromJson(response, InstructionResponseImplNew.class);
      assertNotEquals(instructionResponse.getInstruction(), null);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  private ReceiveInstructionRequest getReceiveInstructionRequest() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("123");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");
    receiveInstructionRequest.setUserRole("RCVR");
    return receiveInstructionRequest;
  }

  @Test
  public void test_transferInstructionsMultipleSuccess() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    MultipleTransferInstructionsRequestBody multipleTransferInstructionsRequestBody =
        new MultipleTransferInstructionsRequestBody();
    multipleTransferInstructionsRequestBody.setInstructionId(Arrays.asList(12345l));

    try {

      doNothing()
          .when(instructionService)
          .transferInstructionsMultiple(
              any(MultipleTransferInstructionsRequestBody.class), any(HttpHeaders.class));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/transferMultiple")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(multipleTransferInstructionsRequestBody))
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      assertNull(e);
    }
  }

  @Test
  public void test_getInstructionSummaryByDeliveryAndInstructionSetId() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {

      ArgumentCaptor<Long> deliveryNumberCaptor = ArgumentCaptor.forClass(Long.class);
      ArgumentCaptor<Long> instructionSetIdCaptor = ArgumentCaptor.forClass(Long.class);

      List<InstructionSummary> mockResponse = new ArrayList<>();
      mockResponse.add(new InstructionSummary());

      doReturn(mockResponse)
          .when(instructionService)
          .getInstructionSummaryByDeliveryAndInstructionSetId(
              deliveryNumberCaptor.capture(), instructionSetIdCaptor.capture());

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/instructions?deliveryNumber=12345&instructionSetId=1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());

      assertTrue(deliveryNumberCaptor.getValue() == 12345l);
      assertTrue(instructionSetIdCaptor.getValue() == 1);

    } catch (Exception e) {
      e.printStackTrace();
      assertNull(e);
    }
  }

  @Test
  public void test_cancelInstructionsMultiple() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      doNothing()
          .when(instructionService)
          .cancelInstructionsMultiple(
              any(MultipleCancelInstructionsRequestBody.class), any(HttpHeaders.class));

      MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
          new MultipleCancelInstructionsRequestBody();
      mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(12345l));

      mockMvc
          .perform(
              MockMvcRequestBuilders.put("/instructions/cancelMultiple")
                  .content(new Gson().toJson(mockMultipleCancelInstructionsRequestBody))
                  .contentType(MediaType.APPLICATION_JSON)
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());

      verify(instructionService, times(1))
          .cancelInstructionsMultiple(
              any(MultipleCancelInstructionsRequestBody.class), any(HttpHeaders.class));

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testRefreshInstruction_HappyPath() throws Exception {
    InstructionResponse instructionResponseExpected =
        new InstructionResponseImplNew(null, null, instruction2, null);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    doReturn(instructionResponseExpected)
        .when(instructionService)
        .refreshInstruction(any(Long.class), any(HttpHeaders.class));
    String response =
        mockMvc
            .perform(MockMvcRequestBuilders.put("/instructions/1/refresh").headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    InstructionResponseImplNew instructionResponseActual =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(response, InstructionResponseImplNew.class);

    assertNotNull(instructionResponseActual);
    assertEquals(
        instructionResponseActual.getInstruction().getId(),
        instructionResponseExpected.getInstruction().getId());

    verify(instructionService, times(1))
        .refreshInstruction(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveAll_HappyPath() throws Exception {
    ReceiveAllResponse receiveAllResponseExpected =
        new ReceiveAllResponse(12345L, "12341234", 1, 12341234L, new HashMap<>());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "test1234");

    ReceiveAllRequest receiveAllRequest = getReceiveAllRequest();

    doReturn(instructionService)
        .when(tenantSpecificConfigReader)
        .getInstructionServiceByFacility(any());
    doReturn(receiveAllResponseExpected)
        .when(instructionService)
        .receiveAll(any(String.class), any(HttpHeaders.class));
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/instructions/line/receive")
                    .content(gson.toJson(receiveAllRequest))
                    .headers(httpHeaders))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ReceiveAllResponse receiveAllResponseActual =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(response, ReceiveAllResponse.class);

    assertNotNull(receiveAllResponseActual);
    assertEquals(
        receiveAllResponseActual.getPurchaseReferenceLineNumber(),
        receiveAllResponseExpected.getPurchaseReferenceLineNumber());

    verify(instructionService, times(1)).receiveAll(any(String.class), any(HttpHeaders.class));
  }

  private ReceiveAllRequest getReceiveAllRequest() {
    ReceiveAllRequest receiveAllRequest = new ReceiveAllRequest();
    receiveAllRequest.setDoorNumber("123");
    receiveAllRequest.setQuantity(1);
    receiveAllRequest.setQuantityUOM("ZA");
    receiveAllRequest.setDeliveryDocuments(new ArrayList<>());
    return receiveAllRequest;
  }

  @Test
  public void test_pendingContainers() throws Exception {
    // given
    doNothing()
        .when(rxCompleteInstructionOutboxHandler)
        .pendingContainers(anyString(), any(HttpHeaders.class));
    // when/then
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/instructions/pendingContainers/1234567890")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
  }

  @Test
  public void test_eachesDetail() throws Exception {
    // given
    doNothing()
        .when(rxCompleteInstructionOutboxHandler)
        .eachesDetail(any(Container.class), any(HttpHeaders.class));
    // when/then
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/instructions/eachesDetail")
                .content(gson.toJson(new Container()))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
  }
}
