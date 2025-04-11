package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE;
import static com.walmart.move.nim.receiving.core.service.ContainerService.ABORT_CALL_FOR_KAFKA_ERR;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;
import static java.util.Objects.nonNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.inventory.model.AdjustmentData;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryAdjustRequest;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient;
import com.walmart.move.nim.receiving.core.client.printlabel.PrintLabelRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSReceiptPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaHawkshawPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.model.inventory.OrgUnitIdInfo;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelServiceResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.testng.annotations.*;

public class ContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private ContainerService containerService;
  @Mock private ContainerService containerServ;
  @Mock private CancelContainerProcessor cancelContainerProcessor;
  @Mock private MoveRestApiClient moveRestApiClient;
  @InjectMocks private ContainerPersisterService containerPersisterService;
  @Spy private DefaultUpdateContainerQuantityRequestHandler updateContainerQuantityRequestHandler;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ContainerRepository containerRepository;
  @Spy private ContainerItemRepository containerItemRepository;
  @Spy private ReceiptRepository receiptRepository;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private JmsExceptionContainerPublisher jmsExceptionContainerPublisher;
  @Mock private MessagePublisher msgPublisher;
  @Mock private JMSReceiptPublisher JMSReceiptPublisher;
  @Mock private InstructionRepository instructionRepository;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private PrintLabelRestApiClient printLabelRestApiClient;
  @Mock private OSDRCalculator osdrCalculator;
  @Mock private RestUtils restUtils;
  @Mock private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @Mock private DockTagService dockTagService;
  @InjectMocks private DockTagServiceImpl dockTagServ;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private DeliveryServiceRetryableImpl deliveryServiceRetryableImpl;
  @Mock private InventoryService inventoryService;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private KafkaTemplate kafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock private DefaultLabelIdProcessor defaultLabelIdProcessor;
  @Mock private AsyncPersister asyncPersister;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Spy private ItemConfigApiClient itemConfigApiClient;
  @Spy private InventoryRestApiClient inventoryRestApiClient;
  @Mock private KafkaHawkshawPublisher kafkaHawkshawPublisher;
  @Mock private PrintLabelHelper printLabelHelper;

  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @InjectMocks private ContainerAdjustmentValidator containerAdjustmentValidator;
  @InjectMocks private PurchaseReferenceValidator purchaseReferenceValidator;

  @Mock private MaasTopics maasTopics;

  private Instruction instruction, instruction2;

  private Instruction reprintInstruction;
  private List<DocumentLine> contentsList, contentsList1, contentsList2;
  private List<ContainerItem> containerItemList;
  private Container container;
  private Container container1;
  private Set<Container> ContainerChildContainers;
  private ContainerItem containerItem, containerItem1;
  private UpdateInstructionRequest instructionRequest, instructionRequest2;
  private Container backoutContainer;
  private Container childContainer;
  private ContainerItem content;
  private final String trackingId = "c32987000000000000000001";
  private Labels labels1, lebels2;
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private ContainerResponseData gdmContainerResponse;
  private final String userId = "sysadmin";
  private final Long deliveryNumber = 21231313L;
  private final String poNumber = "82332773";
  private final Gson gson = new Gson();
  private Transformer<Container, ContainerDTO> transformer;
  Integer printerId = 10;
  private String upc = "2345678912";
  private Long itemNumber = 678945L;
  private ObjectMapper objectMapper = new ObjectMapper();
  private List<Container> containers;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(
        updateContainerQuantityRequestHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        updateContainerQuantityRequestHandler, "receiptService", receiptService);
    ReflectionTestUtils.setField(
        updateContainerQuantityRequestHandler,
        "finalizePORequestBodyBuilder",
        finalizePORequestBodyBuilder);
    ReflectionTestUtils.setField(updateContainerQuantityRequestHandler, "configUtils", configUtils);
    ReflectionTestUtils.setField(
        updateContainerQuantityRequestHandler, "receiptPublisher", receiptPublisher);
    ReflectionTestUtils.setField(
        containerService, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        containerService, "containerAdjustmentValidator", containerAdjustmentValidator);
    ReflectionTestUtils.setField(
        containerService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(
        containerService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(
        containerAdjustmentValidator, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(containerService, "createDockTagTopic", "test");
    ReflectionTestUtils.setField(containerService, "inventMultiCtnrTopic", "test");
    ReflectionTestUtils.setField(inventoryRestApiClient, "gson", gson);
    ReflectionTestUtils.setField(containerService, "gson", gson);
    ReflectionTestUtils.setField(
        updateContainerQuantityRequestHandler, "itemConfig", itemConfigApiClient);

    this.transformer = new ContainerTransformer();
    /* Asn flow */
    InstructionRequest asnInstructionRequest = new InstructionRequest();
    asnInstructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    asnInstructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    asnInstructionRequest.setDeliveryNumber("21231313");
    asnInstructionRequest.setDoorNumber("123");
    asnInstructionRequest.setAsnBarcode(null);
    asnInstructionRequest.setProblemTagId(null);
    asnInstructionRequest.setUpcNumber(null);

    FdeCreateContainerResponse asnFdeCreateContainerResponse = new FdeCreateContainerResponse();
    asnFdeCreateContainerResponse.setInstructionCode("Label");
    asnFdeCreateContainerResponse.setInstructionMsg("Label the Container");
    asnFdeCreateContainerResponse.setMessageId("62668e40-2df0-11e9-a3a4-df7d6879ad88");
    asnFdeCreateContainerResponse.setProviderId("S2S");
    LinkedTreeMap<String, Object> asnMove = new LinkedTreeMap();
    asnMove.put("lastChangedBy", "OF-SYS");
    asnMove.put("lastChangedOn", new Date());
    asnMove.put("sequenceNbr", 1618623190);
    asnMove.put("containerTag", "00100077672010660414");
    asnMove.put("toLocation", "STAGE");
    asnFdeCreateContainerResponse.setMove(asnMove);
    ContainerDetails asnContainer = new ContainerDetails();
    List<ContainerDetails> asnChildContainers = new ArrayList<>();
    ContainerDetails asnChildContainer = new ContainerDetails();
    asnChildContainer.setCtrShippable(Boolean.TRUE);
    asnChildContainer.setCtrReusable(Boolean.TRUE);
    asnChildContainer.setOutboundChannelMethod("S2S");
    Map<String, String> asnCtrDestination = new HashMap<>();
    asnCtrDestination.put("countryCode", "US");
    asnCtrDestination.put("buNumber", "5091");
    asnContainer.setCtrShippable(Boolean.TRUE);
    asnContainer.setCtrReusable(Boolean.FALSE);
    asnContainer.setOutboundChannelMethod("S2S");
    asnContainer.setInventoryStatus("PICKED");
    asnContainer.setCtrType("CARTON");
    asnContainer.setTrackingId("00000077670099006775");
    List<Content> asnContents = new ArrayList<>();
    Content asnChildContent = new Content();
    asnChildContent.setFinancialReportingGroup("US");
    asnChildContent.setBaseDivisionCode("WM");
    asnChildContent.setItemNbr(-1l);
    List<Distribution> asnDistributions = new ArrayList<>();
    Distribution asnDistribution = new Distribution();
    asnDistribution.setAllocQty(1);
    asnDistribution.setDestCC("US");
    asnDistribution.setDestNbr(5091);
    asnDistribution.setOrderId("0618437030");
    Map<String, String> distItem = new HashMap<>();
    distItem.put("itemNbr", "-1");
    distItem.put("baseDivisionCode", "WM");
    distItem.put("financialReportingGroup", "US");
    asnDistribution.setItem(distItem);
    asnDistributions.add(asnDistribution);
    asnChildContent.setDistributions(asnDistributions);
    asnContents.add(asnChildContent);
    asnChildContainer.setContents(asnContents);
    asnChildContainers.add(asnChildContainer);
    asnContainer.setChildContainers(asnChildContainers);

    asnFdeCreateContainerResponse.setContainer(asnContainer);

    /* gdm container response */
    gdmContainerResponse = new ContainerResponseData();
    gdmContainerResponse.setLabel("00100077672010660414");
    /*gdmContainerResponse.setAsnNumber("736795");
    gdmContainerResponse.setDeliveryNumber("20310001");
    gdmContainerResponse.setIsPallet(Boolean.TRUE);
    gdmContainerResponse.setDestBuNbr(7036);*/
    gdmContainerResponse.setDestinationNumber(07036);
    gdmContainerResponse.setDestinationCountryCode("US");
    gdmContainerResponse.setDestinationType("DC");
    gdmContainerResponse.setWeight(222f);
    gdmContainerResponse.setWeightUOM("lb");
    // gdmContainerResponse.setContainerCreatedTimeStamp(new Date());
    ContainerResponseData childContainerResponse = new ContainerResponseData();
    childContainerResponse.setLabel("00000077670099006775");
    // childContainerResponse.setAsnNumber("736795");
    // childContainerResponse.setIsPallet(Boolean.FALSE);
    childContainerResponse.setChannel("S2S");
    childContainerResponse.setInvoiceNumber(618437030);
    childContainerResponse.setDestinationNumber(5091);
    childContainerResponse.setDestinationCountryCode("US");
    childContainerResponse.setDestinationType("STORE");
    childContainerResponse.setSourceType("FC");
    ContainerItemResponseData gdmContainerItemResponse = new ContainerItemResponseData();
    ContainerPOResponseData containerPOResponseData = new ContainerPOResponseData();
    containerPOResponseData.setPurchaseReferenceNumber("0618437030");
    containerPOResponseData.setPurchaseReferenceLineNumber(0);
    // gdmContainerItemResponse.setPurchaseReferenceType("S2S");
    gdmContainerItemResponse.setItemNumber(0);
    gdmContainerItemResponse.setItemUpc("0752113654952");
    gdmContainerItemResponse.setItemQuantity(1);
    gdmContainerItemResponse.setQuantityUOM("EA");
    gdmContainerItemResponse.setPurchaseOrder(containerPOResponseData);
    List<ContainerResponseData> childContainers3 = new ArrayList<>();
    List<ContainerItemResponseData> containerItems = new ArrayList<>();
    containerItems.add(gdmContainerItemResponse);
    childContainerResponse.setItems(containerItems);
    childContainers3.add(childContainerResponse);
    gdmContainerResponse.setContainers(childContainers3);

    instruction2 = new Instruction();
    instruction2.setId(Long.valueOf(2348458));
    instruction2.setDeliveryNumber(Long.parseLong(asnInstructionRequest.getDeliveryNumber()));
    instruction2.setSsccNumber(asnInstructionRequest.getAsnBarcode());
    instruction2.setMessageId(asnInstructionRequest.getMessageId());
    instruction2.setReceivedQuantity(0);
    instruction2.setReceivedQuantityUOM(VNPK);
    instruction2.setProjectedReceiveQty(1);
    instruction2.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    instruction2.setChildContainers(
        asnFdeCreateContainerResponse.getContainer().getChildContainers());
    instruction2.setPrintChildContainerLabels(
        asnFdeCreateContainerResponse.isPrintChildContainerLabels());
    instruction2.setInstructionMsg(asnFdeCreateContainerResponse.getInstructionMsg());
    instruction2.setInstructionCode(asnFdeCreateContainerResponse.getInstructionCode());
    instruction2.setProjectedReceiveQtyUOM(VNPK);
    instruction2.setContainer(asnFdeCreateContainerResponse.getContainer());
    instruction2.setProviderId(asnFdeCreateContainerResponse.getProviderId());
    instruction2.setMove(asnFdeCreateContainerResponse.getMove());
    instruction2.setFacilityCountryCode("US");
    instruction2.setFacilityNum(32899);
    /* end */

    Map<String, String> ctrDestination = new HashMap<String, String>();
    ctrDestination.put("countryCode", "US");
    ctrDestination.put("buNumber", "6012");

    // move Data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");

    List<String> availableLabels = new ArrayList<String>();
    availableLabels.add("a");
    availableLabels.add("b");

    List<String> usedLabels = new ArrayList<String>();
    usedLabels.add("h");

    Labels labels = new Labels();
    labels.setAvailableLabels(availableLabels);
    labels.setUsedLabels(usedLabels);

    // labels data(Second set)
    labels1 = new Labels();
    List<String> availableLabels1 = new ArrayList<String>();
    availableLabels1.add("d");
    labels1.setAvailableLabels(availableLabels1);

    List<String> usedLabels1 = new ArrayList<String>();
    usedLabels1.add("a");
    labels1.setUsedLabels(usedLabels1);

    lebels2 = new Labels();
    lebels2.setAvailableLabels(availableLabels);

    List<String> usedLabels2 = new ArrayList<String>();
    lebels2.setUsedLabels(usedLabels2);

    // Instruction data
    instruction = new Instruction();
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysAdmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("000009430371976");
    instruction.setInstructionCode("Build ContainerModel");
    instruction.setInstructionMsg("Build the ContainerModel");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setLastChangeTs(new Date());
    instruction.setMessageId("12345534");
    instruction.setCompleteTs(new Date());
    instruction.setCreateTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setPoDcNumber("32899");
    instruction.setMove(move);
    instruction.setPrintChildContainerLabels(true);
    instruction.setProviderId("DA");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setReceivedQuantity(1);
    instruction.setReceivedQuantityUOM("ZA");
    instruction.setLabels(labels);
    instruction.setOriginalChannel("XXX");
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"poTypeCode\": 33,\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"CROSSU\",\n"
            + "                \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "                \"purchaseReferenceLineNumber\": 9,\n"
            + "                \"purchaseReferenceLineStatus\": \"PARTIALLY_RECEIVED\",\n "
            + "                \"itemNbr\": 574322171,\n"
            + "                \"itemUPC\": \"00673419302784\",\n"
            + "                \"caseUPC\": \"10673419302781\",\n"
            + "                \"expectedQty\": 600,\n"
            + "                \"expectedQtyUOM\": \"ZA\",\n"
            + "                \"vnpkQty\": 3,\n"
            + "                \"whpkQty\": 3,\n"
            + "                \"orderableQuantity\": 3,\n"
            + "                \"warehousePackQuantity\": 3,\n"
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
            + "                \"freightBillQty\": 10,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\",\n"
            + "                \"additionalInfo\" : {"
            + "                                      \"warehouseGroupCode\": \"P\","
            + "                                      \"isNewItem\": false, "
            + "                                      \"warehouseAreaCode\": \"8\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"weightFormatTypeCode\": \"F\","
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\""
            + "                                     }"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\",\n"
            + "\"totalPurchaseReferenceQty\" : 20 \n"
            + "    }");
    Map<String, Object> ctrLabel = new HashMap<String, Object>();
    Map<String, Object> data = new HashMap<String, Object>();

    data.put("value", "5213389");
    data.put("key", "H");
    data.put("value", "5216389");
    data.put("key", "M");
    List<Map<String, Object>> dataArray = new ArrayList<Map<String, Object>>();

    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("value", "526389");
    labelData.put("key", "Y");
    labelData.put("value", "526789");
    labelData.put("key", "A");
    List<Map<String, Object>> labelDataArray = new ArrayList<Map<String, Object>>();

    Map<String, String> destination = new HashMap<String, String>();
    destination.put("countryCode", "US");
    destination.put("buNumber", "6012");
    dataArray.add(data);
    ctrLabel.put("ttlInHours", "72.0");
    ctrLabel.put("data", dataArray);
    ctrLabel.put("labelData", labelDataArray);

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(1);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item = new HashMap<String, String>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "1084445");
    distribution1.setItem(item);

    Distribution distribution2 = new Distribution();
    distribution2.setAllocQty(2);
    distribution2.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item1 = new HashMap<String, String>();
    item1.put("financialReportingGroup", "US");
    item1.put("baseDivisionCode", "WM");
    item1.put("itemNbr", "1084445");
    distribution2.setItem(item1);
    List<Distribution> distributions = new ArrayList<Distribution>();

    distributions.add(distribution1);
    distributions.add(distribution2);

    ContainerDetails parentContainer = new ContainerDetails();
    parentContainer.setCtrLabel(ctrLabel);
    parentContainer.setCtrShippable(Boolean.TRUE);
    parentContainer.setCtrReusable(Boolean.FALSE);
    parentContainer.setCtrDestination(destination);
    parentContainer.setCtrType("PALLET");
    parentContainer.setQuantity(3);
    parentContainer.setOutboundChannelMethod("CROSSU");
    parentContainer.setInventoryStatus("PICKED");
    parentContainer.setTrackingId("a32L8990000000000000106509");
    parentContainer.setDistributions(distributions);
    parentContainer.setProjectedWeight(20F);
    parentContainer.setProjectedWeightUom("EA");
    parentContainer.setOrgUnitId(1);

    instruction.setContainer(parentContainer);
    List<ContainerDetails> childContainers = new ArrayList<ContainerDetails>();

    ContainerDetails childContainer1 = new ContainerDetails();
    childContainer1.setCtrLabel(ctrLabel);
    childContainer1.setCtrShippable(Boolean.TRUE);
    childContainer1.setCtrReusable(Boolean.FALSE);
    childContainer1.setCtrDestination(destination);
    childContainer1.setCtrType("Vendor Pack");
    childContainer1.setQuantity(3);
    childContainer1.setOutboundChannelMethod("CROSSU");
    childContainer1.setInventoryStatus("PICKED");
    childContainer1.setDistributions(distributions);
    childContainer1.setProjectedWeight(20F);
    childContainer1.setProjectedWeightUom("EA");
    childContainer1.setOrgUnitId(1);
    childContainer1.setTrackingId("a32L8990000000000000106519");
    childContainers.add(childContainer1);

    ContainerDetails childContainer2 = new ContainerDetails();
    childContainer2.setCtrLabel(ctrLabel);
    childContainer2.setCtrShippable(Boolean.TRUE);
    childContainer2.setCtrReusable(Boolean.FALSE);
    childContainer2.setCtrDestination(destination);
    childContainer2.setCtrType("Vendor Pack");
    childContainer2.setQuantity(3);
    childContainer2.setOutboundChannelMethod("CROSSU");
    childContainer2.setInventoryStatus("PICKED");
    childContainer2.setDistributions(distributions);
    childContainer2.setProjectedWeight(20F);
    childContainer2.setProjectedWeightUom("EA");
    childContainer2.setOrgUnitId(1);
    childContainer2.setTrackingId("a32L8990000000000000106567");
    childContainers.add(childContainer2);
    instruction.setChildContainers(childContainers);

    instructionRequest = new UpdateInstructionRequest();
    instructionRequest.setDeliveryNumber(Long.valueOf(20107001));
    instructionRequest.setDoorNumber("123");
    instructionRequest.setFacility(destination);

    instructionRequest2 = new UpdateInstructionRequest();
    instructionRequest2.setDeliveryNumber(Long.valueOf(20107001));
    instructionRequest2.setDoorNumber("123");
    instructionRequest2.setFacility(destination);

    // Delivery document lines
    contentsList = new ArrayList<DocumentLine>();
    DocumentLine contentItems = new DocumentLine();
    contentItems.setPurchaseReferenceNumber("32");
    contentItems.setPurchaseReferenceLineNumber(5);
    contentItems.setTotalPurchaseReferenceQty(100);
    contentItems.setPurchaseRefType("CROSSU");
    contentItems.setPurchaseCompanyId(1);
    contentItems.setPoDCNumber("32899");
    contentItems.setPoDeptNumber("92");
    contentItems.setDeptNumber(1);
    contentItems.setItemNumber(10844432L);
    contentItems.setVendorGS128("");
    contentItems.setGtin("00049807100025");
    contentItems.setVnpkQty(1);
    contentItems.setWhpkQty(1);
    contentItems.setQuantity(1);
    contentItems.setQuantityUOM("EA"); // by default EA
    contentItems.setVendorPackCost(25.0);
    contentItems.setWhpkSell(25.0);
    contentItems.setBaseDivisionCode(null);
    contentItems.setFinancialReportingGroupCode("US");
    contentItems.setRotateDate(null);
    contentItems.setWarehouseMinLifeRemainingToReceive(null);
    contentItems.setProfiledWarehouseArea(null);
    contentItems.setPromoBuyInd(null);
    contentItems.setVendorNumber(null);
    contentItems.setPalletTi(5);
    contentItems.setPalletHi(4);
    contentsList.add(contentItems);
    instructionRequest.setDeliveryDocumentLines(contentsList);

    DocumentLine contentItems2 = new DocumentLine();
    contentItems2.setPurchaseReferenceLineNumber(5);
    contentItems2.setPurchaseReferenceNumber("32");
    contentItems2.setTotalPurchaseReferenceQty(100);
    contentItems2.setPurchaseRefType("POCON");
    contentItems2.setPurchaseCompanyId(1);
    contentItems2.setPoDCNumber("32899");
    contentItems2.setPoDeptNumber("92");
    contentItems2.setDeptNumber(1);
    contentItems2.setItemNumber(10844432L);
    contentItems2.setVendorGS128("");
    contentItems2.setGtin("00049807100025");
    contentItems2.setVnpkQty(1);
    contentItems2.setWhpkQty(1);
    contentItems2.setQuantity(1);
    contentItems2.setQuantityUOM("EA"); // by default EA
    contentItems2.setVendorPackCost(25.0);
    contentItems2.setWhpkSell(25.0);
    contentItems2.setBaseDivisionCode(null);
    contentItems2.setFinancialReportingGroupCode("US");
    contentItems2.setRotateDate(null);
    contentItems2.setWarehouseMinLifeRemainingToReceive(null);
    contentItems2.setProfiledWarehouseArea(null);
    contentItems2.setPromoBuyInd(null);
    contentItems2.setVendorNumber(null);
    contentItems2.setPalletTi(5);
    contentItems2.setPalletHi(4);
    contentsList1 = new ArrayList<DocumentLine>();
    contentsList1.add(contentItems2);
    instructionRequest2.setDeliveryDocumentLines(contentsList1);

    // ContainerModel data
    container = new Container();
    container.setTrackingId("a32L8990000000000000106519");
    container.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
    container.setInventoryStatus("PICKED");
    container.setLocation("171");
    container.setDeliveryNumber(21119003L);
    container.setFacility(ctrDestination);
    container.setDestination(ctrDestination);
    container.setContainerType("Vendor Pack");
    container.setContainerStatus("");
    container.setWeight(5F);
    container.setWeightUOM("");
    container.setCube(1F);
    container.setCubeUOM(null);
    container.setCtrShippable(Boolean.TRUE);
    container.setCtrShippable(Boolean.TRUE);
    container.setCompleteTs(null);
    container.setOrgUnitId("1");
    container.setPublishTs(null);
    container.setCreateTs(new Date(0));
    container.setCreateUser("sysAdmin");
    container.setLastChangedTs(null);
    container.setLastChangedUser("sysAdmin");
    container.setContainerItems(null);

    container1 = new Container();
    container1.setTrackingId("a32L8990000000000000106519");
    container1.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
    container1.setInventoryStatus("PICKED");
    container1.setLocation("171");
    container1.setDeliveryNumber(21119003L);
    container1.setFacility(ctrDestination);
    container1.setDestination(ctrDestination);
    container1.setContainerType("Vendor Pack");
    container1.setContainerStatus("");
    container1.setWeight(5F);
    container1.setWeightUOM("");
    container1.setCube(1F);
    container1.setCubeUOM(null);
    container1.setCtrShippable(Boolean.TRUE);
    container1.setCtrShippable(Boolean.TRUE);
    container1.setCompleteTs(null);
    container1.setOrgUnitId("1");
    container1.setPublishTs(null);
    container1.setCreateTs(new Date(0));
    container1.setCreateUser("sysAdmin");
    container1.setLastChangedTs(null);
    container1.setLastChangedUser("sysAdmin");

    // container contents

    containerItemList = new ArrayList<ContainerItem>();
    containerItem = new ContainerItem();
    containerItem1 = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("7");
    containerItem.setPurchaseReferenceLineNumber(5);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setOutboundChannelMethod("CROSSU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setItemNumber(12345L);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setPoDeptNumber("0092");
    containerItem.setDeptNumber(1);
    containerItem.setItemNumber(10844432L);
    containerItem.setVendorGS128("");
    containerItem.setGtin("00049807100025");
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setActualHi(9);
    containerItem.setActualTi(9);
    containerItem.setQuantityUOM("EA"); // by default EA
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(null);
    containerItem.setDistributions(distributions);
    containerItemList.add(containerItem);

    List<ContainerItem> contentsLists1 = new ArrayList<ContainerItem>();
    containerItem1.setPurchaseReferenceNumber("7");
    containerItem1.setPurchaseReferenceLineNumber(5);
    containerItem1.setInboundChannelMethod("CROSSU");
    containerItem1.setOutboundChannelMethod("CROSSU");
    containerItem1.setTotalPurchaseReferenceQty(100);
    containerItem1.setPurchaseCompanyId(1);
    containerItem1.setPoDeptNumber("0092");
    containerItem1.setDeptNumber(1);
    containerItem1.setItemNumber(10844432L);
    containerItem1.setVendorGS128("");
    containerItem1.setGtin("00049807100025");
    containerItem1.setVnpkQty(1);
    containerItem1.setWhpkQty(1);
    containerItem1.setQuantity(1);
    containerItem1.setQuantityUOM("EA"); // by default EA
    containerItem1.setVendorPackCost(25.0);
    containerItem1.setWhpkSell(25.0);
    containerItem1.setBaseDivisionCode("VM");
    containerItem1.setFinancialReportingGroupCode("US");
    containerItem1.setRotateDate(null);
    containerItem1.setDistributions(null);
    containerItem1.setVnpkQty(2);
    containerItem1.setWhpkQty(2);
    containerItem1.setQuantity(2);
    contentsLists1.add(containerItem1);

    container1.setContainerItems(containerItemList);

    container.setContainerItems(contentsLists1);

    // childContainers
    Container containerchildContainer1 = new Container();
    containerchildContainer1.setTrackingId("a32L8990000000000000106520");
    containerchildContainer1.setCompleteTs(new Date());
    containerchildContainer1.setLocation("123");
    containerchildContainer1.setDeliveryNumber(Long.valueOf(12342));
    containerchildContainer1.setFacility(ctrDestination);
    containerchildContainer1.setDestination(ctrDestination);
    containerchildContainer1.setContainerType("Vendor Pack");
    containerchildContainer1.setContainerStatus("");
    containerchildContainer1.setWeight(5F);
    containerchildContainer1.setWeightUOM("EA");
    containerchildContainer1.setCube(2F);
    containerchildContainer1.setCubeUOM("EA");
    containerchildContainer1.setCtrShippable(true);
    containerchildContainer1.setCtrShippable(false);
    containerchildContainer1.setInventoryStatus("Picked");
    containerchildContainer1.setCompleteTs(new Date());
    containerchildContainer1.setPublishTs(new Date());
    containerchildContainer1.setCreateTs(new Date());
    containerchildContainer1.setCreateUser("sysAdmin");
    containerchildContainer1.setLastChangedTs(new Date());
    containerchildContainer1.setLastChangedUser("sysAdmin");
    containerchildContainer1.setContainerMiscInfo(Collections.emptyMap());
    containerchildContainer1.setContainerItems(containerItemList);

    Container containerchildContainer2 = new Container();
    containerchildContainer2.setTrackingId("a32L8990000000000000106521");
    containerchildContainer2.setCompleteTs(new Date());
    containerchildContainer2.setLocation("123L");
    containerchildContainer2.setDeliveryNumber(Long.valueOf(12342));
    containerchildContainer2.setFacility(ctrDestination);
    containerchildContainer2.setDestination(ctrDestination);
    containerchildContainer2.setContainerType("Vendor Pack");
    containerchildContainer2.setContainerStatus("");
    containerchildContainer2.setWeight(5F);
    containerchildContainer2.setWeightUOM("EA");
    containerchildContainer2.setCube(2F);
    containerchildContainer2.setCubeUOM("EA");
    containerchildContainer2.setCtrShippable(true);
    containerchildContainer2.setCtrShippable(false);
    containerchildContainer2.setInventoryStatus("Picked");
    containerchildContainer2.setCompleteTs(new Date());
    containerchildContainer2.setPublishTs(new Date());
    containerchildContainer2.setCreateTs(new Date());
    containerchildContainer2.setCreateUser("sysAdmin");
    containerchildContainer2.setLastChangedTs(new Date());
    containerchildContainer2.setLastChangedUser("sysAdmin");
    containerchildContainer2.setContainerItems(containerItemList);

    ContainerChildContainers = new HashSet<Container>();
    ContainerChildContainers.add(containerchildContainer1);
    ContainerChildContainers.add(containerchildContainer2);

    // Mock data for backout container

    backoutContainer = new Container();
    backoutContainer.setDeliveryNumber(1234L);
    backoutContainer.setTrackingId(trackingId);
    backoutContainer.setContainerStatus("");

    List<ContainerItem> backoutContainerItems = new ArrayList<ContainerItem>();
    content = new ContainerItem();
    content.setTrackingId(trackingId);
    content.setPurchaseReferenceNumber("34734743");
    content.setPurchaseReferenceLineNumber(1);
    content.setVnpkQty(24);
    content.setWhpkQty(6);
    content.setQuantity(24);
    content.setQuantityUOM("EA");
    backoutContainerItems.add(content);

    Set<Container> childs = new HashSet<Container>();
    childContainer = new Container();
    childContainer.setDeliveryNumber(1234L);
    childContainer.setParentTrackingId(trackingId);
    childContainer.setContainerStatus("");
    childs.add(childContainer);

    ContainerUpdateResponse containerUpdateResponse = new ContainerUpdateResponse();

    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/containerByDeliveryNumber.json")
              .getCanonicalPath();
      containers =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), new TypeReference<List<Container>>() {});
    } catch (IOException e) {
      e.printStackTrace();
    }

    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {

    reset(containerRepository);
    reset(containerItemRepository);
    reset(receiptRepository);
    reset(receiptService);
    reset(deliveryStatusPublisher);
    reset(configUtils);
    reset(gdcPutawayPublisher);
    reset(osdrRecordCountAggregator);
    reset(deliveryItemOverrideService);
    reset(deliveryService);
    reset(deliveryServiceRetryableImpl);
    reset(receiptPublisher);
    reset(gdmRestApiClient);
    reset(finalizePORequestBodyBuilder);
    reset(kafkaTemplate);
    reset(secureKafkaTemplate);
    reset(kafkaConfig);
    reset(restUtils);
    reset(inventoryService);
    reset(JMSReceiptPublisher);
    reset(asyncPersister);
    reset(moveRestApiClient);
    reset(kafkaHawkshawPublisher);
    reset(dcFinRestApiClient);
    reset(instructionRepository);
  }

  @BeforeMethod
  public void beforeMethod() {
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    when(maasTopics.getPubExceptionContainerTopic()).thenReturn(PUB_RECEIPTS_EXCEPTION_TOPIC);
    when(maasTopics.getPubExceptionContainerTopic()).thenReturn(PUB_RECEIPTS_EXCEPTION_TOPIC);
  }

  /**
   * This method will test processCreateContainer() method when ParentContainer created DB.
   *
   * @throws Exception
   */
  @Test
  public void testProcessCreateContainersWhenParentContainerisPresent() throws Exception {

    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
  }

  @Test
  public void testSwapCancelContainers() {
    List<SwapContainerRequest> swapList = new ArrayList<>();
    SwapContainerRequest swapContainerRequest = new SwapContainerRequest();
    swapList.add(swapContainerRequest);
    when(cancelContainerProcessor.swapContainers(any(), any(HttpHeaders.class))).thenReturn(null);
    when(configUtils.getConfiguredInstance(any(), anyString(), any()))
        .thenReturn(cancelContainerProcessor);
    containerService.swapContainers(swapList, httpHeaders);

    verify(configUtils, times(1)).getConfiguredInstance(any(), anyString(), any());
  }

  /**
   * This method will test processCreateContainer method when parent container is not created in DB
   *
   * @throws Exception
   */
  @Test
  public void testProcessCreateContainersWhenParentisNotPresent() throws Exception {

    when(containerRepository.existsByTrackingId(anyString())).thenReturn(true);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  @Test
  public void testProcessCreateContainers_vendorNbrDeptSeq() throws Exception {

    String deliveryDocumentJsonString =
        "{\"purchaseReferenceNumber\":\"8222248770\",\"financialGroupCode\":\"US\",\"baseDivCode\":\"WM\",\"vendorNumber\":\"480889\",\"vendorNbrDeptSeq\":\"480889940\",\"deptNumber\":\"94\",\"purchaseCompanyId\":\"1\",\"purchaseReferenceLegacyType\":\"28\",\"poDCNumber\":\"32612\",\"purchaseReferenceStatus\":\"ACTV\",\"deliveryDocumentLines\":[{\"gtin\":\"01123840356119\",\"itemUPC\":\"01123840356119\",\"caseUPC\":\"11188122713797\",\"purchaseReferenceNumber\":\"8222248770\",\"purchaseReferenceLineNumber\":1,\"event\":\"POS REPLEN\",\"purchaseReferenceLineStatus\":\"ACTIVE\",\"whpkSell\":23.89,\"vendorPackCost\":23.89,\"vnpkQty\":6,\"whpkQty\":6,\"orderableQuantity\":11,\"warehousePackQuantity\":11,\"expectedQtyUOM\":\"ZA\",\"openQty\":0,\"expectedQty\":81,\"overageQtyLimit\":20,\"itemNbr\":9773149,\"purchaseRefType\":\"SSTKU\",\"palletTi\":9,\"palletHi\":9,\"vnpkWgtQty\":10.0,\"vnpkWgtUom\":\"LB\",\"vnpkcbqty\":0.852,\"vnpkcbuomcd\":\"CF\",\"color\":\"8DAYS\",\"size\":\"EA\",\"isHazmat\":false,\"itemDescription1\":\"4\\\" MKT BAN CRM N\",\"itemDescription2\":\"\\u003cT\\u0026S\\u003e\",\"isConveyable\":false,\"warehouseRotationTypeCode\":\"3\",\"firstExpiryFirstOut\":true,\"warehouseMinLifeRemainingToReceive\":30,\"profiledWarehouseArea\":\"CPS\",\"promoBuyInd\":\"N\",\"additionalInfo\":{\"warehouseAreaCode\":\"4\",\"warehouseGroupCode\":\"DD\",\"isNewItem\":false,\"profiledWarehouseArea\":\"CPS\",\"warehouseRotationTypeCode\":\"3\",\"recall\":false,\"weight\":3.325,\"weightFormatTypeCode\":\"V\",\"weightUOM\":\"LB\",\"warehouseMinLifeRemainingToReceive\":30},\"operationalInfo\":{\"state\":\"ACTIVE\"},\"freightBillQty\":243,\"bolWeight\":0.4115,\"activeChannelMethods\":[]}],\"totalPurchaseReferenceQty\":243,\"weight\":0.0,\"cubeQty\":0.0,\"freightTermCode\":\"PRP\",\"deliveryStatus\":\"WRK\",\"poTypeCode\":28,\"totalBolFbq\":0,\"deliveryLegacyStatus\":\"WRK\"}";
    instruction.setDeliveryDocument(deliveryDocumentJsonString);

    final DeliveryDocument deliveryDocument_pojo =
        InstructionUtils.getDeliveryDocument(instruction);
    final Integer vendorNbrDeptSeq = deliveryDocument_pojo.getVendorNbrDeptSeq();
    assertTrue(vendorNbrDeptSeq == 480889940);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);
    verify(containerItemRepository, Mockito.times(1)).saveAll(anyList());
  }

  /** This method will test a success for getting container printJob and label details. */
  @Test
  public void testGetCreatedChildContainerLabelsWhenSuccess() {

    List<ContainerDetails> containerDetails = new ArrayList<>();
    ContainerDetails details = new ContainerDetails();
    Map<String, Object> map = new HashMap<String, Object>();
    map.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, "OF");
    map.put(ReceivingConstants.PRINT_HEADERS_KEY, "headers");
    map.put(
        ReceivingConstants.PRINT_REQUEST_KEY,
        Arrays.asList(new HashMap<String, Object>(), new HashMap<String, Object>()));
    details.setCtrLabel(map);
    containerDetails.add(details);
    containerDetails.add(details);
    containerDetails.add(details);
    containerDetails.add(details);
    Map<String, Object> createdChildContainerLabels =
        containerService.getCreatedChildContainerLabels(containerDetails, 2, 2);
    assertTrue(createdChildContainerLabels.containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
  }

  /** This method will test a success for getting container tracking id details. */
  @Test
  public void testGetCreatedChildContainerTrackingIdWhenSuccess() {

    List<ContainerDetails> containerDetails = new ArrayList<>();
    ContainerDetails details = new ContainerDetails();
    details.setTrackingId("1234r543");
    containerDetails.add(details);
    containerDetails.add(details);
    containerDetails.add(details);
    containerDetails.add(details);
    List<String> createdChildContainerLabels =
        containerService.getCreatedChildContainerTrackingIds(containerDetails, 1, 1);
    assertEquals(createdChildContainerLabels.get(0), "1234r543");
  }

  /**
   * This method will test processCreateContainer method when parent container is exists and
   * updating quantity
   *
   * @throws Exception
   */
  @Test
  public void testProcessCreateContainersWhenParentisPresentandSSTK() throws Exception {

    instruction.setChildContainers(null);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(true);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /**
   * This method will test processCreateContainer method when parent container doesn't exists and
   * creating new container for SSTK case
   *
   * @throws Exception
   */
  @Test
  public void testProcessCreateContainersWhenParentisNotPresentforSSTKCase() throws Exception {

    instruction.setChildContainers(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  @Test
  public void testProcessCreateContainersWhenParentisNotPresentforPCON() throws Exception {

    instruction.setChildContainers(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest2, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /**
   * This method will test the constructParentContainer method for SSTK container
   *
   * @throws Exception
   */
  @Test
  public void testConstructParentContainerWhenChildContainerisNotPresent() throws Exception {
    // setting child container null value for test
    instruction.setChildContainers(null);
    instruction.getLabels().setAvailableLabels(null);

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /**
   * This method will test the check for pending ChildContainer if parent is available.
   *
   * @throws ReceivingException
   */
  @Test
  public void checkForPendingchildContainerCreationforDACaseAndParentisAvailable()
      throws ReceivingException {
    instruction.setLabels(labels1);

    when(containerRepository.existsByTrackingId(anyString())).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /**
   * This method will test the when Available labels is empty or null
   *
   * @throws ReceivingException
   */
  @Test
  public void
      checkForPendingchildContainerCreationforDACaseAndParentisAvailableAndAvailableLabelisEmpty()
          throws ReceivingException {
    instruction.getLabels().setAvailableLabels(new ArrayList<>());
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    try {
      containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.CONTAINER_EXCEEDS_QUANTITY);
    }
  }

  /**
   * This method will test the check for pending ChildContainer if parent is not available.
   *
   * @throws ReceivingException
   */
  @Test
  public void checkForPendingchildContainerCreationforDACaseAndParentisNotAvailable()
      throws ReceivingException {

    instruction.setLabels(lebels2);
    instructionRequest.getDeliveryDocumentLines().get(0).setFinancialReportingGroupCode(null);

    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /**
   * This method will test the constructParentContainer method for DA container
   *
   * @throws Exception
   */
  @Test
  public void testConstructParentContainerWhenChildContainersIsPresent() throws Exception {

    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  /** This method will test the getContainerDetails() */
  @Test
  public void testGetContainerDetails() {
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container);

    containerPersisterService.getContainerDetails("a32L8990000000000000106519");

    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /** This method will test getContainerIncludingChild method. */
  @Test
  public void testGetContainerIncludingChild() throws ReceivingException {

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(ContainerChildContainers);
    when(containerItemRepository.findByTrackingIdIn(anyList())).thenReturn(containerItemList);

    containerService.getContainerIncludingChild(container);

    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(anyString());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingIdIn(anyList());
  }

  /** This method will test the update container Quantity */
  @Test
  public void testUpdateContainerItem() {
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseReferenceNumber("2324341341231");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("CROSSU");
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setQuantity(2);
    documentLine.setQuantityUOM("ZA");
    documentLine.setRotateDate(new Date());

    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);

    containerService.updateContainerItem(instruction2, documentLine, "sysadmin");

    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(anyString());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the containerComplete method().
   *
   * @throws ReceivingException
   */
  @Test
  public void testContainerComplete() throws ReceivingException {
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.containerComplete("a32L8990000000000000106519", "sysAdmin");

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the containerComplete method().
   *
   * @throws ReceivingException
   */
  @Test
  public void testContainerCompleteWhenLessQtyReceived() throws ReceivingException {
    containerItemList.get(0).setQuantity(2);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.containerComplete("a32L8990000000000000106519", "sysAdmin");

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the containerComplete method() when Distribution is not available.
   *
   * @throws ReceivingException
   */
  @Test
  public void testContainerCompleteWhenDistributionIsNotAvailable() throws ReceivingException {
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.containerComplete("a32L8990000000000000106520", "sysAdmin");

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the containerComplete method().
   *
   * @throws ReceivingException
   */
  @Test
  public void testContainerCompleteForDACase() throws ReceivingException {
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.containerComplete("a32L8990000000000000106520", "sysAdmin");

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the containerComplete method() when container not found.
   *
   * @throws ReceivingException
   */
  @Test
  public void testContainerCompleteWhenContainerNotFound() throws ReceivingException {
    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    try {
      containerService.containerComplete("a32L8990000000000000106520", "sysAdmin");
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.MATCHING_CONTAINER_NOT_FOUND);
    }
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  /**
   * This method will test the updateContainerStatus method().
   *
   * @throws ReceivingException
   */
  @Test
  public void testupdateContainerStatus() {
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(receiptRepository.saveAll(anyList())).thenReturn(anyList());

    containerPersisterService.updateContainerStatusAndSaveReceipts(
        "a32L8990000000000000106520", "backout", "sysAdmin", new ArrayList<Receipt>());

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
    verify(receiptRepository, Mockito.times(1)).saveAll(anyList());
  }

  /** This method will test the publishContainer method(); */
  @Test
  public void testPublishContainer() {
    container.setChildContainers(ContainerChildContainers);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    containerService.publishContainer(container, new HashMap<>());
  }

  /**
   * Test for sending delete putaway request message on VTR
   *
   * @throws ReceivingException
   */
  @Test
  public void testBackoutContainer_PublishPutaway() throws ReceivingException {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("us");

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  /**
   * This method will test the backoutContainer
   *
   * @throws ReceivingException
   */
  @Test
  public void testBackoutContainerHapÎpyPath() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerHappyPath_1() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDPT\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerHappyPath_2() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"REO\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  /** */
  @Test
  public void testBackoutContainerDoesNotExists() {
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(null);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      containerService.backoutContainer(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  /** */
  @Test
  public void testBackoutContainerAlreadyBackout() {
    container = MockContainer.getContainerInfo();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      containerService.backoutContainer(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  /** */
  @Test
  public void testBackoutContainerHasChilds() {
    Set<Container> childContainers = new HashSet<Container>();
    childContainer = new Container();
    childContainer.setDeliveryNumber(1234L);
    childContainer.setParentTrackingId(trackingId);
    childContainer.setContainerStatus("");
    childContainers.add(childContainer);

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(childContainers);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      containerService.backoutContainer(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_WITH_CHILD_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  /** */
  @Test
  @SneakyThrows
  public void testBackoutContainerDoesNotHaveContents() {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(new ArrayList<ContainerItem>());

    try {
      containerService.backoutContainer(trackingId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void testCreateAndPublishContainersForValidS2S() throws ReceivingException {
    reset(containerRepository);
    List<Container> containerList = new ArrayList<Container>();
    when(containerRepository.saveAll(any())).thenReturn(containerList);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());

    containerService.createAndPublishContainersForS2S(
        instruction2, gdmContainerResponse, ReceivingUtils.getForwardablHeader(httpHeaders));

    verify(JMSReceiptPublisher, times(1)).publish(any(), any());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).saveAll(any(List.class));
  }

  @Test
  public void testCreateAndPublishContainersForValidS2SWhenParentHasContainerItem()
      throws ReceivingException {
    reset(JMSReceiptPublisher);
    ContainerResponseData containerResponseData = new ContainerResponseData();
    ContainerItemResponseData gdmContainerItemResponse = new ContainerItemResponseData();
    ContainerPOResponseData containerPOResponseData = new ContainerPOResponseData();
    containerPOResponseData.setPurchaseReferenceNumber("0618437030");
    containerPOResponseData.setPurchaseReferenceLineNumber(0);
    // gdmContainerItemResponse.setPurchaseReferenceType("S2S");
    gdmContainerItemResponse.setItemNumber(0);
    gdmContainerItemResponse.setItemUpc("0752113654952");
    gdmContainerItemResponse.setItemQuantity(1);
    gdmContainerItemResponse.setQuantityUOM("EA");
    gdmContainerItemResponse.setPurchaseOrder(containerPOResponseData);
    List<ContainerItemResponseData> containerItems = new ArrayList<>();
    containerItems.add(gdmContainerItemResponse);
    containerResponseData.setItems(containerItems);

    List<Container> containerList = new ArrayList<Container>();
    when(containerRepository.saveAll(any())).thenReturn(containerList);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());

    containerService.createAndPublishContainersForS2S(
        instruction2, containerResponseData, ReceivingUtils.getForwardablHeader(httpHeaders));

    verify(JMSReceiptPublisher, times(1)).publish(any(), any());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
  }

  @Test
  public void testDeleteContainer() throws ReceivingException {
    when(appConfig.getInSqlBatchSize()).thenReturn(999);
    List<Container> containerList = new ArrayList<Container>();
    containerList.add(container);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containerList);

    doNothing().when(containerRepository).deleteAll(containerList);

    containerService.deleteContainers(Long.valueOf(1234));

    verify(containerRepository, Mockito.times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void testDeleteContainerWhenContainerListIsEmpty() throws ReceivingException {
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(null);
    try {
      containerService.deleteContainers(Long.valueOf(1234));
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          "no record's found for this delivery number in container table");
    }

    verify(containerRepository, Mockito.times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void testGetContainerByInstruction() throws ReceivingException {
    List<Container> containerList = new ArrayList<Container>();
    containerList.add(container);

    when(containerRepository.findByInstructionId(anyLong())).thenReturn(containerList);

    containerService.getContainerByInstruction(Long.valueOf(1234));

    verify(containerRepository, Mockito.times(1)).findByInstructionId(anyLong());
  }

  @Test
  public void testGetContainersByInstructionisEmpty() throws ReceivingException {
    when(containerRepository.findByInstructionId(anyLong())).thenReturn(null);
    try {
      containerService.getContainerByInstruction(Long.valueOf(1234));
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          "no record's found for this instruction id in container table");
    }

    verify(containerRepository, Mockito.times(1)).findByInstructionId(any());
  }

  @Test
  public void testGetContainerByDeliveryNumber() throws ReceivingException {
    List<Container> containers = new ArrayList<Container>();
    containers.add(container);
    when(containerRepository.findByDeliveryNumber(anyLong())).thenReturn(containers);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
    when(appConfig.getInSqlBatchSize()).thenReturn(999);
    containerService.getContainerByDeliveryNumber(Long.valueOf(1234));

    verify(containerRepository, Mockito.times(1)).findByDeliveryNumber(any());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingIdIn(any());
  }

  @Test
  public void testProcessCreateContainers_1() throws Exception {

    instruction.setLabels(null);
    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
  }

  @Test(dataProvider = "OrgUnitTest")
  public void testPrepareContainer(ContainerRequest containerRequest) {
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    Container container =
        containerService.prepareContainer(
            deliveryNumber,
            containerRequest != null ? containerRequest : MockContainer.getContainerRequest(),
            userId);

    assertEquals(
        container.getContainerItems().size(),
        MockContainer.getContainerRequest().getContents().size());
    assertEquals(container.getDeliveryNumber(), deliveryNumber);
    assertEquals(container.getTrackingId(), MockContainer.getContainerRequest().getTrackingId());
    assertEquals(container.getMessageId(), MockContainer.getContainerRequest().getMessageId());
    assertEquals(
        container.getContainerItems().get(0).getItemNumber(),
        MockContainer.getContainerRequest().getContents().get(0).getItemNumber());
    assertEquals(
        container.getContainerItems().get(1).getItemNumber(),
        MockContainer.getContainerRequest().getContents().get(1).getItemNumber());
  }

  @DataProvider(name = "OrgUnitTest")
  public static Object[][] restExceptionStatus() {
    ContainerRequest containerOne = MockContainer.getContainerRequest();
    containerOne.setOrgUnitId("1");
    return new Object[][] {{null}, {containerOne}};
  }

  /**
   * The source for containerType is pallet instruction from OF and UpdateInstructionRequest
   * payload. This test is for using the containerType received from client.
   *
   * @throws Exception
   */
  @Test
  public void testCreateContainerWithContainerTypeFromClient() throws Exception {
    Container expectedContainer = new Container();
    expectedContainer.setTrackingId("a32612000000000001");
    expectedContainer.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    expectedContainer.setInstructionId(Long.parseLong("1901"));
    expectedContainer.setLocation("101");
    expectedContainer.setDeliveryNumber(Long.parseLong("300001"));
    expectedContainer.setContainerType("Chep Pallet");
    expectedContainer.setCtrShippable(Boolean.TRUE);
    expectedContainer.setCtrReusable(Boolean.TRUE);
    expectedContainer.setInventoryStatus("AVAILABLE");
    expectedContainer.setFacilityNum(32612);
    expectedContainer.setFacilityCountryCode("US");

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a32612000000000001");
    containerItem.setPurchaseReferenceNumber("4166030001");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(14);
    containerItem.setPoDeptNumber("14");
    containerItem.setItemNumber(Long.parseLong("573170821"));
    containerItem.setGtin("00028000114602");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(15.88);
    containerItem.setWhpkSell(16.98);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setFacilityNum(32612);
    containerItem.setFacilityCountryCode("US");
    containerItems.add(containerItem);
    containerItem.setWarehouseGroupCode("P");
    containerItem.setWarehouseAreaCode("8");
    containerItem.setProfiledWarehouseArea("CPS");
    containerItem.setWarehouseRotationTypeCode("3");
    containerItem.setRecall(Boolean.FALSE);
    containerItem.setIsVariableWeight(Boolean.TRUE);
    expectedContainer.setContainerItems(containerItems);

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(
        MockInstruction.getInstruction(),
        MockUpdateInstructionRequest.getUpdateInstructionRequestWithContainerType(),
        GdcHttpHeaders.getHeaders());

    verify(containerRepository, times(1)).save(any());
  }

  @Test
  public void testCreateContainer_receiveIntoOss() throws Exception {

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    final UpdateInstructionRequest updateInstructionRequest =
        MockUpdateInstructionRequest.getUpdateInstructionRequestWithContainerType();
    updateInstructionRequest.setFlowDescriptor(FLOW_RECEIVE_INTO_OSS);

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    Instruction instruction1 = MockInstruction.getInstruction();
    containerService.processCreateContainers(
        instruction1, updateInstructionRequest, GdcHttpHeaders.getHeaders());

    verify(containerRepository, times(1)).save(any());
    verify(containerRepository, times(1)).save(containerArgumentCaptor.capture());
    final Container containerArgumentCaptorValue = containerArgumentCaptor.getValue();
    assertNotNull(containerArgumentCaptorValue);
    final Map<String, Object> containerMiscInfo =
        containerArgumentCaptorValue.getContainerMiscInfo();
    assertNotNull(containerMiscInfo);
  }

  /**
   * The source for containerType is pallet instruction from OF and UpdateInstructionRequest
   * payload. This test is for using the containerType received from OF.
   *
   * @throws Exception
   */
  @Test
  public void testCreateContainerWithContainerTypeFromOF() throws Exception {
    Container expectedContainer = new Container();
    expectedContainer.setTrackingId("a32612000000000001");
    expectedContainer.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    expectedContainer.setInstructionId(Long.parseLong("1901"));
    expectedContainer.setLocation("101");
    expectedContainer.setDeliveryNumber(Long.parseLong("300001"));
    expectedContainer.setContainerType("PALLET");
    expectedContainer.setCtrShippable(Boolean.TRUE);
    expectedContainer.setCtrReusable(Boolean.TRUE);
    expectedContainer.setInventoryStatus("AVAILABLE");
    expectedContainer.setFacilityNum(32612);
    expectedContainer.setFacilityCountryCode("US");

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a32612000000000001");
    containerItem.setPurchaseReferenceNumber("4166030001");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(14);
    containerItem.setPoDeptNumber("14");
    containerItem.setItemNumber(Long.parseLong("573170821"));
    containerItem.setGtin("00028000114602");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(15.88);
    containerItem.setWhpkSell(16.98);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setFacilityNum(32612);
    containerItem.setFacilityCountryCode("US");
    containerItems.add(containerItem);
    expectedContainer.setContainerItems(containerItems);

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(
        MockInstruction.getInstruction(),
        MockUpdateInstructionRequest.getUpdateInstructionRequestWithoutContainerType(),
        GdcHttpHeaders.getHeaders());

    verify(containerRepository, times(1)).save(any());
  }

  @Test
  public void testProcessCreateContainersWithPromoBuyInd() throws Exception {
    when(appConfig.getPackagedAsUom()).thenReturn(VNPK);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(
        MockContainer.getInstruction(), MockContainer.getUpdateInstructionRequest(), httpHeaders);

    verify(containerRepository, times(1)).save(any(Container.class));
  }

  @Test
  public void testContainerCompleteWithActualTiHi_fullPallet() throws ReceivingException {
    Container mockContainer = MockContainer.getContainer();
    List<ContainerItem> mockContainerItems = MockContainer.getContainer().getContainerItems();

    when(containerRepository.save(any(Container.class))).thenReturn(mockContainer);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(mockContainerItems);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ADJUST_HI_ENABLED, true);

    Container response =
        containerService.containerComplete(
            MockContainer.getContainer().getTrackingId(), "sysadmin");

    assertEquals(
        response.getContainerItems().get(0).getActualTi(),
        MockContainer.getContainer().getContainerItems().get(0).getActualTi());
    assertEquals(response.getContainerItems().get(0).getActualHi().intValue(), 4);
  }

  @Test
  public void testContainerCompleteWithActualTiHi_partialPallet() throws ReceivingException {
    Container mockContainer = MockContainer.getContainer();
    List<ContainerItem> mockContainerItems = MockContainer.getContainer().getContainerItems();
    mockContainerItems.get(0).setQuantity(24);

    when(containerRepository.save(any(Container.class))).thenReturn(mockContainer);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(mockContainerItems);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ADJUST_HI_ENABLED, true);

    Container response =
        containerService.containerComplete(
            MockContainer.getContainer().getTrackingId(), "sysadmin");

    assertEquals(
        response.getContainerItems().get(0).getActualTi(),
        MockContainer.getContainer().getContainerItems().get(0).getActualTi());
    assertEquals(response.getContainerItems().get(0).getActualHi().intValue(), 1);
  }

  @Test
  public void testProcessFullPalletDamageAdjustment() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    Integer damageQty = -24;

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(
            container, ReceivingConstants.PUTAWAY_DELETE_ACTION, GdcHttpHeaders.getHeaders());

    containerService.processDamageAdjustment(trackingId, damageQty, GdcHttpHeaders.getHeaders());

    verify(containerRepository, Mockito.times(1)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(trackingId);
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void testContainerCompleteWithActualTiHi_fullPallet_ADJUST_HI_ENABLED_false()
      throws ReceivingException {
    Container mockContainer = MockContainer.getContainer();
    List<ContainerItem> mockContainerItems = MockContainer.getContainer().getContainerItems();

    when(containerRepository.save(any(Container.class))).thenReturn(mockContainer);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(mockContainerItems);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ADJUST_HI_ENABLED, true);

    Container response =
        containerService.containerComplete(
            MockContainer.getContainer().getTrackingId(), "sysadmin");

    assertEquals(
        response.getContainerItems().get(0).getActualTi(),
        MockContainer.getContainer().getContainerItems().get(0).getActualTi());
    final int actualHi_resulted = response.getContainerItems().get(0).getActualHi().intValue();
    // qty=24, TiXHi=6x2
    assertEquals(actualHi_resulted, 2);
  }

  @Test
  public void testContainerCompleteWithActualTiHi_partialPallet_ADJUST_HI_ENABLED_false()
      throws ReceivingException {
    Container mockContainer = MockContainer.getContainer();
    List<ContainerItem> mockContainerItems = MockContainer.getContainer().getContainerItems();
    mockContainerItems.get(0).setQuantity(24);

    when(containerRepository.save(any(Container.class))).thenReturn(mockContainer);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(mockContainerItems);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ADJUST_HI_ENABLED, true);

    Container response =
        containerService.containerComplete(
            MockContainer.getContainer().getTrackingId(), "sysadmin");

    final Integer actualHi_mock =
        MockContainer.getContainer().getContainerItems().get(0).getActualHi();
    assertEquals(
        response.getContainerItems().get(0).getActualTi(),
        MockContainer.getContainer().getContainerItems().get(0).getActualTi());
    final int actualHi_resulted = response.getContainerItems().get(0).getActualHi().intValue();
    // qty=24, TiXHi=6x2
    assertEquals(actualHi_resulted, actualHi_mock.intValue());
  }

  @Test
  public void testProcessPartialPalletDamageAdjustment() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    Integer damageQty = -60;

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(
            container, ReceivingConstants.PUTAWAY_DELETE_ACTION, GdcHttpHeaders.getHeaders());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(
            container, ReceivingConstants.PUTAWAY_ADD_ACTION, GdcHttpHeaders.getHeaders());

    containerService.processDamageAdjustment(trackingId, damageQty, GdcHttpHeaders.getHeaders());

    verify(containerRepository, Mockito.times(1)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(trackingId);
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), anyString(), any());
  }

  @Test
  public void testAdjustContainerByQty() {

    Container adjustedContainer =
        ContainerUtils.adjustContainerByQty(true, MockContainer.getContainerInfo(), 30);

    assertEquals(adjustedContainer.getContainerItems().get(0).getQuantity().intValue(), 30);
    assertEquals(adjustedContainer.getContainerItems().get(0).getActualHi().intValue(), 1);
    assertEquals(adjustedContainer.getWeight(), 10f);
    assertEquals(adjustedContainer.getWeightUOM(), "LB");
  }

  @Test
  public void testCreateContainerPoCon()
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(container1);
    when(containerRepository.save(any(Container.class))).thenReturn(container1);
    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(containerItem);

    Instruction instruction = new Instruction();
    instruction.setId(3L);
    instruction.setDeliveryNumber(42229032L);
    instruction.setActivityName("POCON");
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setGtin(null);
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setPoDcNumber("32988");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(null);
    instruction.setProjectedReceiveQty(3);
    instruction.setProviderId("DA-SSTK");

    String deliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"2963320149\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"12344\",\n"
            + "    \"deptNumber\": \"14\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"33\",\n"
            + "    \"poDCNumber\": \"32988\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"itemUPC\": \"00076501380104\",\n"
            + "            \"caseUPC\": \"00076501380104\",\n"
            + "            \"purchaseReferenceNumber\": \"2963320149\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 8.22,\n"
            + "            \"vendorPackCost\": 6.6,\n"
            + "            \"vnpkQty\": 2,\n"
            + "            \"whpkQty\": 2,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"expectedQty\": 400,\n"
            + "            \"overageQtyLimit\": 11,\n"
            + "            \"itemNbr\": 444444441,\n"
            + "            \"purchaseRefType\": \"POCON\",\n"
            + "            \"palletTi\": 7,\n"
            + "            \"palletHi\": 8,\n"
            + "            \"vnpkWgtQty\": 14.84,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.432,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"\",\n"
            + "            \"size\": \"\",\n"
            + "            \"itemDescription1\": \"70QT XTREME BLUE\",\n"
            + "            \"itemDescription2\": \"WH TO ASM\",\n"
            + "            \"isConveyable\": true\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 106,\n"
            + "    \"weight\": 12.0,\n"
            + "    \"weightUOM\": \"LB\",\n"
            + "    \"cubeQty\": 23.5,\n"
            + "    \"cubeUOM\": \"CF\",\n"
            + "    \"freightTermCode\": \"COLL\"\n"
            + "}";

    instruction.setDeliveryDocument(deliveryDocument);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOrgUnitId(1);
    containerDetails.setProjectedWeightUom(VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("PICKED");
    containerDetails.setCtrShippable(true);
    containerDetails.setCtrReusable(false);
    containerDetails.setQuantity(10);

    instruction.setContainer(containerDetails);

    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    List<DocumentLine> documentLines = new ArrayList<>();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseRefType("POCON");
    documentLine.setQuantity(10);
    documentLine.setQuantityUOM("EA");
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryNumber(42229032L);
    updateInstructionRequest.setDoorNumber("3");
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    Class[] paramArguments = new Class[5];
    paramArguments[0] = Instruction.class;
    paramArguments[1] = ContainerDetails.class;
    paramArguments[2] = Boolean.class;
    paramArguments[3] = Boolean.class;
    paramArguments[4] = UpdateInstructionRequest.class;
    // this is when container not exist
    Method privateMethodParentContainer =
        ContainerService.class.getDeclaredMethod("constructContainer", paramArguments);
    privateMethodParentContainer.setAccessible(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getOrgUnitId()).thenReturn("1");
    Container container =
        (Container)
            privateMethodParentContainer.invoke(
                containerService,
                instruction,
                containerDetails,
                true,
                false,
                updateInstructionRequest);

    ContainerItem containerItem = container.getContainerItems().get(0);

    assertNotNull(container.getWeight());
    assertNotNull(container.getWeightUOM());
    assertNotNull(container.getCube());
    assertNotNull(container.getCubeUOM());
    if (!(containerItem.getItemNumber() == -1L
        && containerItem.getVnpkQty() == 1
        && containerItem.getWhpkQty() == 1)) {
      assertTrue(false);
    }

    // when container exist

    String userId = "sysadmin";
    Class[] updateArg = new Class[3];
    updateArg[0] = Instruction.class;
    updateArg[1] = DocumentLine.class;
    updateArg[2] = String.class;

    Method privateMethodUpdateContainer =
        ContainerService.class.getDeclaredMethod("updateContainerItem", updateArg);
    privateMethodUpdateContainer.setAccessible(true);
    privateMethodUpdateContainer.invoke(containerService, instruction, documentLine, userId);

    verify(containerRepository, times(1)).save(any());
    verify(containerItemRepository, times(1)).save(any());
  }

  @Test
  public void testProcessVendorDamageAdjustment() throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test
  public void testProcessVendorDamageAdjustment_withOpenDeliveries() throws ReceivingException {
    List<Long> openDeliveries = new ArrayList<>();
    openDeliveries.add(1234L);
    openDeliveries.add(74378358L);

    Container cntr = MockContainer.getSSTKContainer();
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test
  public void testProcessVendorDamageAdjustment_masterRecordAlreadyPresent()
      throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.of(MockReceipt.getOSDRMasterReceipt()));

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for tracking id c32987000000000000000001")
  public void testProcessVendorDamageAdjustment_ContainerNotAvaiable() throws ReceivingException {

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(trackingId, adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerWithNoContents for tracking id a329870000000000000000001")
  public void testProcessVendorDamageAdjustment_ContainerItemsNotAvaiableForContainer()
      throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    cntr.getContainerItems().clear();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerWithChild for tracking id a329870000000000000000001")
  public void testProcessVendorDamageAdjustment_ContainerIsAPallet() throws ReceivingException {

    Container cntr = MockContainer.getDAContainer();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getChildContainers());
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerAlreadyCanceled for tracking id a329870000000000000000001")
  public void testProcessVendorDamageAdjustment_ContainerAlreadyBackedOut()
      throws ReceivingException {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("us");

    Container cntr = MockContainer.getSSTKContainer();
    cntr.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", ReceivingConstants.VDM_REASON_CODE);
    adjustment.addProperty("reasonDesc", OSDRCode.D53.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processVendorDamageAdjustment(cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test
  public void testProcessConcealedShortageOrOverageAdjustment_Shortage() throws ReceivingException {
    Container cntr = MockContainer.getSSTKContainer();
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    assertEquals((int) cntr.getContainerItems().get(0).getQuantity(), 19);
    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test
  public void testProcessConcealedShortageOrOverageAdjustment_withOpenDeliveries()
      throws ReceivingException {

    List<Long> openDeliveriesOnDb = new ArrayList<>();
    openDeliveriesOnDb.add(1234L);
    openDeliveriesOnDb.add(748378348L);

    Container cntr = MockContainer.getSSTKContainer();
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    assertEquals((int) cntr.getContainerItems().get(0).getQuantity(), 19);
    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test
  public void testProcessConcealedShortageOrOverageAdjustment_masterRecordAlreadyPresent()
      throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    Receipt optionalReceipt = MockReceipt.getOSDRMasterReceipt();
    optionalReceipt.setId(76834L);

    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.of(optionalReceipt));

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    assertEquals((int) cntr.getContainerItems().get(0).getQuantity(), 19);
    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test
  public void testProcessConcealedShortageOrOverageAdjustment_Overage() throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(receiptRepository.saveAll(anyList())).thenReturn(new ArrayList<Receipt>());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCO_CONCEALED_OVERAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.O55.getDescription());
    adjustment.addProperty("value", 5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    assertEquals((int) cntr.getContainerItems().get(0).getQuantity(), 29);
    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for tracking id c32987000000000000000001")
  public void testProcessConcealedShortageOrOverageAdjustment_ContainerNotAvaiable()
      throws ReceivingException {

    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        trackingId, adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerWithNoContents for tracking id a329870000000000000000001")
  public void
      testProcessConcealedShortageOrOverageAdjustment_ContainerItemsNotAvaiableForContainer()
          throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    cntr.getContainerItems().clear();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerWithChild for tracking id a329870000000000000000001")
  public void testProcessConcealedShortageOrOverageAdjustment_ContainerIsAPallet()
      throws ReceivingException {

    Container cntr = MockContainer.getDAContainer();
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getChildContainers());
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(null);

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid inventory adjustment containerAlreadyCanceled for tracking id a329870000000000000000001")
  public void testProcessConcealedShortageOrOverageAdjustment_ContainerAlreadyBackedOut()
      throws ReceivingException {

    Container cntr = MockContainer.getSSTKContainer();
    cntr.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    when(containerRepository.findByTrackingId(cntr.getTrackingId())).thenReturn(cntr);
    when(containerRepository.findAllByParentTrackingId(cntr.getTrackingId())).thenReturn(null);
    when(containerItemRepository.findByTrackingId(cntr.getTrackingId()))
        .thenReturn(cntr.getContainerItems());

    JsonObject adjustment = new JsonObject();
    adjustment.addProperty("reasonCode", RCS_CONCEALED_SHORTAGE_REASON_CODE);
    adjustment.addProperty("reasonDesc", ConcealedShortageCode.S54.getDescription());
    adjustment.addProperty("value", -5);
    adjustment.addProperty("uom", "EACHES");

    containerService.processConcealedShortageOrOverageAdjustment(
        cntr.getTrackingId(), adjustment, httpHeaders);

    assertEquals((int) cntr.getContainerItems().get(0).getQuantity(), 24);
    verify(containerRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(cntr.getTrackingId());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(cntr.getTrackingId());
  }

  @Test
  public void testGetContainerWithChildsByTrackingIdReturnsUomAsEaches() throws ReceivingException {

    when(containerRepository.findByTrackingId(anyString()))
        .thenReturn(mockResponseForGetParentContainer(null, "12345", 36));
    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(mockResponseForGetContainerIncludesChildren("12345"));
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(containerItemRepository.findByTrackingIdIn(anyList()))
        .thenReturn(mockResponseForGetContainerItems(Arrays.asList("123", "456"), 36));

    Container containerWithChildsByTrackingId =
        containerService.getContainerWithChildsByTrackingId("12345", true);

    assertTrue(containerWithChildsByTrackingId.isHasChildContainers());
    assertNotNull(containerWithChildsByTrackingId.getChildContainers());

    Set<Container> childContainers = containerWithChildsByTrackingId.getChildContainers();
    childContainers.forEach(
        (childContainer) -> {
          List<ContainerItem> containerItems = childContainer.getContainerItems();
          ContainerItem containerItem = containerItems.get(0);
          assertTrue(containerItem.getQuantityUOM().equalsIgnoreCase(EACHES));
          assertTrue(containerItem.getQuantity() == 36);
        });

    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(anyString());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingIdIn(anyList());
  }

  @Test
  public void testGetContainerWithChildsByTrackingIdReturnsUomAsWHPK() throws ReceivingException {
    when(containerRepository.findByTrackingId(anyString()))
        .thenReturn(mockResponseForGetParentContainer(null, "12345", 36));
    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(mockResponseForGetContainerIncludesChildren("12345"));
    when(containerItemRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerItemRepository.findByTrackingIdIn(anyList()))
        .thenReturn(mockResponseForGetContainerItems(Arrays.asList("123", "456"), 36));

    Container containerWithChildsByTrackingId =
        containerService.getContainerWithChildsByTrackingId(
            "12345", true, ReceivingConstants.Uom.WHPK);

    assertTrue(containerWithChildsByTrackingId.isHasChildContainers());
    assertNotNull(containerWithChildsByTrackingId.getChildContainers());

    Set<Container> childContainers = containerWithChildsByTrackingId.getChildContainers();
    childContainers.forEach(
        (childContainer) -> {
          List<ContainerItem> containerItems = childContainer.getContainerItems();
          ContainerItem containerItem = containerItems.get(0);
          assertTrue(containerItem.getQuantityUOM().equalsIgnoreCase(ReceivingConstants.Uom.WHPK));
          assertTrue(containerItem.getQuantity() == 6);
        });

    verify(containerRepository, Mockito.times(1)).findByTrackingId(anyString());
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(anyString());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(anyString());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingIdIn(anyList());
  }

  @Test
  public void getContainerByTrackingId_NullContainer() {
    try {
      when(containerRepository.findByTrackingId(trackingId)).thenReturn(null);

      containerService.getContainerByTrackingId(trackingId);

    } catch (ReceivingException re) {
      assertEquals(HttpStatus.NOT_FOUND, re.getHttpStatus(), "error");
      assertEquals(
          ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
          (String) re.getErrorResponse().getErrorMessage(),
          "no record found error message should be displayed");
    }
  }

  @Test
  public void getParentContainerByTrackingIdAndItReturnsQtyAndUomInEaches() {
    try {
      when(containerRepository.findByTrackingId(anyString()))
          .thenReturn(mockParentContainerWithoutChildrenResponse(null, "12345", 60));
      when(containerItemRepository.findByTrackingId(anyString()))
          .thenReturn(Arrays.asList(mockGetContainerItem("12345", 60)));

      Container containerByTrackingId = containerService.getContainerByTrackingId(anyString());

      assertNotNull(containerByTrackingId);

      ContainerItem containerItem = containerByTrackingId.getContainerItems().get(0);
      assertTrue(containerItem.getQuantityUOM().equalsIgnoreCase(EACHES));
      assertTrue(containerItem.getQuantity() == 60);

    } catch (ReceivingException re) {
      assertTrue(false, "expected a valid containerByTrackingId");
    }
  }

  @Test
  public void getParentContainerByTrackingIdAndItReturnsQtyAndUomInWhpk() {
    try {
      when(containerRepository.findByTrackingId(anyString()))
          .thenReturn(mockParentContainerWithoutChildrenResponse(null, "12345", 60));
      when(containerItemRepository.findByTrackingId(anyString()))
          .thenReturn(Arrays.asList(mockGetContainerItem("12345", 60)));

      Container containerByTrackingId =
          containerService.getContainerByTrackingId(anyString(), WHPK);

      assertNotNull(containerByTrackingId);

      ContainerItem containerItem = containerByTrackingId.getContainerItems().get(0);
      assertTrue(containerItem.getQuantityUOM().equalsIgnoreCase(WHPK));
      assertTrue(containerItem.getQuantity() == 10);

    } catch (ReceivingException re) {
      assertTrue(false, "expected a valid containerByTrackingId");
    }
  }

  @Test
  public void getParentContainerByTrackingIdAndItReturnsQtyAndUomInVnpk() {
    try {
      when(containerRepository.findByTrackingId(anyString()))
          .thenReturn(mockParentContainerWithoutChildrenResponse(null, "12345", 60));
      when(containerItemRepository.findByTrackingId(anyString()))
          .thenReturn(Arrays.asList(mockGetContainerItem("12345", 60)));

      Container containerByTrackingId =
          containerService.getContainerByTrackingId(anyString(), VNPK);

      assertNotNull(containerByTrackingId);

      ContainerItem containerItem = containerByTrackingId.getContainerItems().get(0);
      assertTrue(containerItem.getQuantityUOM().equalsIgnoreCase(VNPK));
      assertTrue(containerItem.getQuantity() == 10);

    } catch (ReceivingException re) {
      assertTrue(false, "expected a valid containerByTrackingId");
    }
  }

  /** Adjust Quantity for LPN */
  @Test
  public void updateQuantityByTrackingId() {
    try {
      // setup data
      container.setInstructionId(10l);
      container.setContainerStatus(AVAILABLE);
      final ContainerItem containerItem = containerItemList.get(0);
      containerItem.setQuantity(1);
      containerItem.setVnpkWgtQty(1f);
      containerItem.setActualHi(11);

      Receipt receipt = new Receipt();
      receipt.setOsdrMaster(1);
      receipt.setPurchaseReferenceLineNumber(5);
      receipt.setFbProblemQty(10);
      receipt.setFbDamagedQty(10);
      receipt.setFbRejectedQty(10);
      receipt.setFinalizedUserId("sysadmin");
      receipt.setFinalizeTs(new Date());
      FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
      ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
      containerUpdateRequest.setAdjustQuantity(10);
      containerUpdateRequest.setInventoryQuantity(containerItem.getQuantity());
      containerUpdateRequest.setPrinterId(10);
      PrintLabelServiceResponse printLabelSericeResponse = getSetupPrintJobTestDataTestData();

      doReturn(updateContainerQuantityRequestHandler)
          .when(configUtils)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      // setup mock data
      when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
      when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(containerItem);
      when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
      when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(instruction));
      doNothing()
          .when(gdcPutawayPublisher)
          .publishMessage(container, PUTAWAY_DELETE_ACTION, httpHeaders);
      doReturn(receipt)
          .when(receiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              any(), any(), any());
      doReturn(mockFinalizePORequestBody)
          .when(finalizePORequestBodyBuilder)
          .buildFrom(anyLong(), anyString(), any(Map.class));
      doNothing().when(gdmRestApiClient).finalizePurchaseOrder(any(), any(), any(), any());
      doNothing().when(osdrCalculator).calculate(any());
      doReturn(printLabelSericeResponse).when(printLabelRestApiClient).getPrintLabel(any(), any());
      doReturn(new ResponseEntity<String>("{}", OK))
          .when(restUtils)
          .put(any(), any(), any(), any());

      // execution
      ContainerUpdateResponse containerUpdateResponse =
          containerService.updateQuantityByTrackingId(
              trackingId, containerUpdateRequest, httpHeaders);

      // verify
      final Container containerInResponse = containerUpdateResponse.getContainer();
      assertNotNull(containerInResponse);
      final Map<String, Object> printJob = containerUpdateResponse.getPrintJob();
      assertNull(printJob);

      verify(configUtils, times(1))
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      // verify only 1 putaway is called
      verify(gdcPutawayPublisher, times(1)).publishMessage(any(), anyString(), any());
      verify(printLabelRestApiClient, times(0)).getPrintLabel(anyString(), anyMap());

      // verify container save
      verify(containerRepository, times(1)).save(any(Container.class));
    } catch (Exception e) {
      assertTrue(false, "expected a valid response and not exception");
    }
  }

  /** PalletCorrection With 0 Quantity to backout/cancel LPN */
  @Test
  public void test_PalletCorrection_With_0_Quantity_to_cancel_LPN() {
    try {
      // setup data
      container.setInstructionId(10l);
      container.setContainerStatus(AVAILABLE);
      final ContainerItem containerItem = containerItemList.get(0);
      containerItem.setVnpkWgtQty(1f);
      Receipt receipt = new Receipt();
      receipt.setOsdrMaster(1);
      receipt.setPurchaseReferenceLineNumber(5);
      receipt.setFbProblemQty(10);
      receipt.setFbDamagedQty(10);
      receipt.setFbRejectedQty(10);
      receipt.setFinalizedUserId("sysadmin");
      receipt.setFinalizeTs(new Date());
      FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
      ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
      // Zero new Quantity means delete/cancel lpn
      containerUpdateRequest.setAdjustQuantity(0);
      containerUpdateRequest.setInventoryQuantity(containerItem.getQuantity());
      containerUpdateRequest.setPrinterId(10);
      PrintLabelServiceResponse printLabelSericeResponse = getSetupPrintJobTestDataTestData();

      doReturn(updateContainerQuantityRequestHandler)
          .when(configUtils)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      // setup mock data
      when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
      when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(containerItem);
      when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
      when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(instruction));
      doNothing()
          .when(gdcPutawayPublisher)
          .publishMessage(container, PUTAWAY_DELETE_ACTION, httpHeaders);
      doReturn(receipt)
          .when(receiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              any(), any(), any());
      doReturn(mockFinalizePORequestBody)
          .when(finalizePORequestBodyBuilder)
          .buildFrom(anyLong(), anyString(), any(Map.class));
      doNothing().when(gdmRestApiClient).finalizePurchaseOrder(any(), any(), any(), any());
      doNothing().when(osdrCalculator).calculate(any());
      doReturn(printLabelSericeResponse).when(printLabelRestApiClient).getPrintLabel(any(), any());
      doReturn(new ResponseEntity<String>("{}", OK))
          .when(restUtils)
          .put(any(), any(), any(), any());
      doNothing()
          .when(receiptPublisher)
          .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
      doReturn(receipt)
          .when(receiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              any(), any(), any());

      offGlsFlags("32987");

      // execution
      ContainerUpdateResponse containerUpdateResponse =
          containerService.updateQuantityByTrackingId(
              trackingId, containerUpdateRequest, httpHeaders);

      // verify
      final Container containerInResponse = containerUpdateResponse.getContainer();
      assertNotNull(containerInResponse);
      // backout and save to repo
      assertEquals(containerInResponse.getContainerStatus(), ReceivingConstants.STATUS_BACKOUT);
      verify(containerRepository, Mockito.times(1)).save(any(Container.class));

      verify(printLabelRestApiClient, times(0)).getPrintLabel(anyString(), anyMap());

    } catch (Exception e) {
      assertTrue(false, "expected a valid response and not exception");
    }
  }

  @Test
  public void test_ReceivingCorrection_with_possible_damages() {
    try {
      // setup data
      Integer currentQuantity_RCV = 12; // initially received Quantity in RCV

      final int currentQuantity_INV = 9; // actual quantity from inventory
      Integer newQuantity_UI = 7; // new quantity UI is adjusting to
      // current actual Quantity in INV is 9, user sending 7 as new Quantity
      // currentQuantity_RCV-actualQuantity=possibleDamages=12-9=3, RCV ignores

      // new quantity actual qty=7 from -9(7-9=-2), quantity is reduced by 2
      Integer actualDiffQuantity_per_INV = newQuantity_UI - currentQuantity_INV;

      // new quantity in RCV is 10, 12 + (diff=-2)
      Integer newQuantity_RCV = currentQuantity_RCV + actualDiffQuantity_per_INV;

      ContainerUpdateRequest containerUpdateRequest_UI = new ContainerUpdateRequest();
      containerUpdateRequest_UI.setAdjustQuantity(newQuantity_UI);
      containerUpdateRequest_UI.setInventoryQuantity(currentQuantity_INV);

      container.setInstructionId(10l);
      container.setContainerStatus(AVAILABLE);
      final ContainerItem containerItem = containerItemList.get(0);
      containerItem.setVnpkWgtQty(1f);
      containerItem.setQuantity(currentQuantity_RCV);

      Receipt receipt = new Receipt();
      receipt.setOsdrMaster(1);
      receipt.setPurchaseReferenceLineNumber(5);
      receipt.setFbProblemQty(10);
      receipt.setFbDamagedQty(10);
      receipt.setFbRejectedQty(10);
      receipt.setFinalizedUserId("sysadmin");
      receipt.setFinalizeTs(new Date());
      FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();

      PrintLabelServiceResponse printLabelSericeResponse = getSetupPrintJobTestDataTestData();

      doReturn(updateContainerQuantityRequestHandler)
          .when(configUtils)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      // setup mock data
      when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
      when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(containerItem);
      when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
      when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(instruction));
      doNothing()
          .when(gdcPutawayPublisher)
          .publishMessage(container, PUTAWAY_DELETE_ACTION, httpHeaders);
      doReturn(receipt)
          .when(receiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              any(), any(), any());
      doReturn(mockFinalizePORequestBody)
          .when(finalizePORequestBodyBuilder)
          .buildFrom(anyLong(), anyString(), any(Map.class));
      doNothing().when(gdmRestApiClient).finalizePurchaseOrder(any(), any(), any(), any());
      doNothing().when(osdrCalculator).calculate(any());
      doReturn(printLabelSericeResponse).when(printLabelRestApiClient).getPrintLabel(any(), any());
      doReturn(new ResponseEntity<String>("{}", OK))
          .when(restUtils)
          .put(any(), any(), any(), any());
      doNothing()
          .when(receiptPublisher)
          .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());

      offGlsFlags("32987");

      // execution
      ContainerUpdateResponse containerUpdateResponse =
          containerService.updateQuantityByTrackingId(
              trackingId, containerUpdateRequest_UI, httpHeaders);

      // verify
      final Container containerInResponse = containerUpdateResponse.getContainer();
      assertNotNull(containerInResponse);

      // RCV container quantity=10=12-2=(rcv.old.qty=12 - actual diff=-2)
      ArgumentCaptor<ContainerItem> containerItemArgumentCaptor_RCV =
          ArgumentCaptor.forClass(ContainerItem.class);
      verify(containerItemRepository, times(1)).save(containerItemArgumentCaptor_RCV.capture());
      final ContainerItem containerItem_RCV = containerItemArgumentCaptor_RCV.getValue();
      assertNotNull(containerItem_RCV);
      final Integer actualQuantity_RCV = containerItem_RCV.getQuantity();
      assertEquals(actualQuantity_RCV, newQuantity_RCV);

      // RCV mew receipt for actual diff = -2
      ArgumentCaptor<Receipt> receiptArgumentCaptor_RCV = ArgumentCaptor.forClass(Receipt.class);
      verify(receiptService, times(2)).saveReceipt(receiptArgumentCaptor_RCV.capture());
      final Receipt receipt_RCV = receiptArgumentCaptor_RCV.getAllValues().get(0);
      assertNotNull(receipt_RCV);
      final Integer receiptQuantityVnpk_RCV = receipt_RCV.getQuantity();
      Integer differenceOfQuantityInEaches_RCV =
          ReceivingUtils.conversionToEaches(
              receiptQuantityVnpk_RCV, VNPK, receipt_RCV.getVnpkQty(), receipt_RCV.getWhpkQty());
      assertEquals(differenceOfQuantityInEaches_RCV, actualDiffQuantity_per_INV);

      // RTU Putaway: update to 7 (i.e newQuantity from user=7)
      ArgumentCaptor<Container> containerArgumentCaptor_RTU =
          ArgumentCaptor.forClass(Container.class);
      verify(gdcPutawayPublisher, times(1))
          .publishMessage(containerArgumentCaptor_RTU.capture(), any(), any());
      final Container container_RTU = containerArgumentCaptor_RTU.getValue();
      assertNotNull(container_RTU);
      final Float weight_RTU = container_RTU.getWeight();
      assertEquals(weight_RTU, Float.valueOf(7.0f));

      final ContainerItem containerItem_RTU = container_RTU.getContainerItems().get(0);
      final Integer actualQuantity_RTU = containerItem_RTU.getQuantity();
      assertEquals(actualQuantity_RTU, newQuantity_UI);

      final Integer actualTi = containerItem_RTU.getActualTi();
      assertEquals(actualTi, Integer.valueOf(9));
      final Integer actualHi = containerItem_RTU.getActualHi();
      assertEquals(actualHi, Integer.valueOf(1));

      // INV should get -2 actual diff
      ArgumentCaptor<String> argumentCaptorString = ArgumentCaptor.forClass(String.class);
      verify(restUtils, atLeastOnce()).put(any(), any(), any(), argumentCaptorString.capture());
      final String requestJson_INV = argumentCaptorString.getValue();
      final String expectedRequestJson_INV =
          "{\"trackingId\":\"c32987000000000000000001\",\"itemNumber\":10844432,\"baseDivisionCode\":\"VM\",\"uom\":\"EA\",\"financialReportingGroup\":\"US\",\"adjustBy\":"
              + actualDiffQuantity_per_INV
              + ",\"reasonCode\":52}";
      assertNotNull(requestJson_INV);
      assertEquals(requestJson_INV, expectedRequestJson_INV);
      // lpass
      verify(printLabelRestApiClient, times(0)).getPrintLabel(anyString(), anyMap());

    } catch (Exception e) {
      assertTrue(false, "expected a valid response and not exception");
    }
  }

  private void offGlsFlags(String facilityNum) {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_INVENTORY_API_DISABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
  }

  @Test
  public void test_ReceivingCorrection_with_possible_damages_0_newQuantity_send_Delete_RTU() {
    try {
      // setup data
      Integer currentQuantity_RCV = 12; // initially received Quantity in RCV

      final int currentQuantity_INV = 9; // actual quantity from inventory
      Integer newQuantity_UI = 0; // new quantity UI is adjusting to
      // current actual Quantity in INV is 9, user sending 0 as new Quantity

      // new actual quantity will be 0 from 9(9-9=0), reduced by 9
      Integer actualDiffQuantity_per_INV = newQuantity_UI - currentQuantity_INV;

      // new quantity in RCV is 3 (12+(diff=-9)=3)
      Integer newQuantity_RCV = currentQuantity_RCV + actualDiffQuantity_per_INV;

      ContainerUpdateRequest containerUpdateRequest_UI = new ContainerUpdateRequest();
      containerUpdateRequest_UI.setAdjustQuantity(newQuantity_UI);
      containerUpdateRequest_UI.setInventoryQuantity(currentQuantity_INV);

      container.setInstructionId(10l);
      container.setContainerStatus(AVAILABLE);
      final ContainerItem containerItem = containerItemList.get(0);
      containerItem.setVnpkWgtQty(1f);
      containerItem.setQuantity(currentQuantity_RCV);

      Receipt receipt = new Receipt();
      receipt.setOsdrMaster(1);
      receipt.setPurchaseReferenceLineNumber(5);
      receipt.setFbProblemQty(10);
      receipt.setFbDamagedQty(10);
      receipt.setFbRejectedQty(10);
      receipt.setFinalizedUserId("sysadmin");
      receipt.setFinalizeTs(new Date());
      FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();

      PrintLabelServiceResponse printLabelSericeResponse = getSetupPrintJobTestDataTestData();

      doReturn(updateContainerQuantityRequestHandler)
          .when(configUtils)
          .getConfiguredInstance(anyString(), anyString(), any(Class.class));

      // setup mock data
      when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
      when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(containerItem);
      when(containerItemRepository.findByTrackingId(anyString())).thenReturn(containerItemList);
      when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(instruction));
      doNothing()
          .when(gdcPutawayPublisher)
          .publishMessage(container, PUTAWAY_DELETE_ACTION, httpHeaders);
      doReturn(receipt)
          .when(receiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              any(), any(), any());
      doReturn(mockFinalizePORequestBody)
          .when(finalizePORequestBodyBuilder)
          .buildFrom(anyLong(), anyString(), any(Map.class));
      doNothing().when(gdmRestApiClient).finalizePurchaseOrder(any(), any(), any(), any());
      doNothing().when(osdrCalculator).calculate(any());
      doReturn(printLabelSericeResponse).when(printLabelRestApiClient).getPrintLabel(any(), any());
      doReturn(new ResponseEntity<String>("{}", OK))
          .when(restUtils)
          .put(any(), any(), any(), any());
      doNothing()
          .when(receiptPublisher)
          .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());

      offGlsFlags("32987");

      // execution
      ContainerUpdateResponse containerUpdateResponse =
          containerService.updateQuantityByTrackingId(
              trackingId, containerUpdateRequest_UI, httpHeaders);

      // verify
      final Container containerInResponse = containerUpdateResponse.getContainer();
      assertNotNull(containerInResponse);

      // RCV container quantity=10=12-2=(rcv.old.qty=12 - actual diff=-2)
      ArgumentCaptor<ContainerItem> containerItemArgumentCaptor_RCV =
          ArgumentCaptor.forClass(ContainerItem.class);
      verify(containerItemRepository, times(1)).save(containerItemArgumentCaptor_RCV.capture());
      final ContainerItem containerItem_RCV = containerItemArgumentCaptor_RCV.getValue();
      assertNotNull(containerItem_RCV);
      final Integer actualQuantity_RCV = containerItem_RCV.getQuantity();
      assertEquals(actualQuantity_RCV, newQuantity_RCV);

      // RCV mew receipt for actual diff = -9
      ArgumentCaptor<Receipt> receiptArgumentCaptor_RCV = ArgumentCaptor.forClass(Receipt.class);
      verify(receiptService, times(2)).saveReceipt(receiptArgumentCaptor_RCV.capture());
      final Receipt receipt_RCV = receiptArgumentCaptor_RCV.getAllValues().get(0);
      assertNotNull(receipt_RCV);
      final Integer receiptQuantityVnpk_RCV = receipt_RCV.getQuantity();
      Integer differenceOfQuantityInEaches_RCV =
          ReceivingUtils.conversionToEaches(
              receiptQuantityVnpk_RCV, VNPK, receipt_RCV.getVnpkQty(), receipt_RCV.getWhpkQty());
      assertEquals(differenceOfQuantityInEaches_RCV, actualDiffQuantity_per_INV);

      // RTU Putaway: update to 7 (i.e newQuantity from user=7)
      // TODO verify action if delete sent
      ArgumentCaptor<Container> containerArgumentCaptor_RTU =
          ArgumentCaptor.forClass(Container.class);
      verify(gdcPutawayPublisher, times(1))
          .publishMessage(containerArgumentCaptor_RTU.capture(), any(), any());
      final Container container_RTU = containerArgumentCaptor_RTU.getValue();
      assertNotNull(container_RTU);
      final Float weight_RTU = container_RTU.getWeight();
      assertEquals(weight_RTU, Float.valueOf(9.0f));

      final ContainerItem containerItem_RTU = container_RTU.getContainerItems().get(0);
      final Integer actualQuantity_RTU = containerItem_RTU.getQuantity();
      assertEquals(actualQuantity_RTU, Integer.valueOf(9));

      final Integer actualTi = containerItem_RTU.getActualTi();
      assertEquals(actualTi, Integer.valueOf(9));
      final Integer actualHi = containerItem_RTU.getActualHi();
      assertEquals(actualHi, Integer.valueOf(1));

      // INV should get -2 actual diff
      ArgumentCaptor<String> argumentCaptorString = ArgumentCaptor.forClass(String.class);
      verify(restUtils, atLeastOnce()).put(any(), any(), any(), argumentCaptorString.capture());
      final String requestJson_INV = argumentCaptorString.getValue();
      final String expectedRequestJson_INV =
          "{\"trackingId\":\"c32987000000000000000001\",\"itemNumber\":10844432,\"baseDivisionCode\":\"VM\",\"uom\":\"EA\",\"financialReportingGroup\":\"US\",\"adjustBy\":"
              + actualDiffQuantity_per_INV
              + ",\"reasonCode\":52}";
      assertNotNull(requestJson_INV);
      assertEquals(requestJson_INV, expectedRequestJson_INV);
      // lpass
      verify(printLabelRestApiClient, times(0)).getPrintLabel(anyString(), anyMap());

    } catch (Exception e) {
      assertTrue(false, "expected a valid response and not exception");
    }
  }

  private PrintLabelServiceResponse getSetupPrintJobTestDataTestData() {
    PrintLabelServiceResponse printLabelSericeResponse = new PrintLabelServiceResponse();
    List<LabelData> labelDataList = new LinkedList<>();
    LabelData labelData = new LabelData();
    labelData.setKey(PRINT_PRINTER_ID);
    labelData.setValue(String.valueOf(printerId));
    labelDataList.add(labelData);
    printLabelSericeResponse.setLabelData(labelDataList);
    return printLabelSericeResponse;
  }

  /**
   * This method will test the constructParentContainer method for DA container
   *
   * @throws Exception
   */
  @Test
  public void testConstructParentContainer_invalid_variable_weight_items() throws Exception {

    Gson gson = new Gson();

    String deliveryDocumentStr = instruction.getDeliveryDocument();
    DeliveryDocument deliveryDocument = gson.fromJson(deliveryDocumentStr, DeliveryDocument.class);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setWeightFormatTypeCode("V");
    deliveryDocument.getDeliveryDocumentLines().get(0).setBolWeight(null);

    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    try {
      containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);
    } catch (ReceivingException re) {
      assertEquals(re.getErrorResponse().getErrorCode(), "InvalidBolWeightForItem");
      assertEquals(re.getErrorResponse().getErrorHeader(), "Invalid BOL Weight");
      assertEquals(
          re.getErrorResponse().getErrorMessage(),
          "Unable to receive item due to Invalid BOL weight for item in GDM.");
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  /**
   * This method will test the constructParentContainer method for DA container
   *
   * @throws Exception
   */
  @Test
  public void testConstructParentContainer_variable_weight_items() throws Exception {

    Gson gson = new Gson();

    String deliveryDocumentStr = instruction.getDeliveryDocument();
    DeliveryDocument deliveryDocument = gson.fromJson(deliveryDocumentStr, DeliveryDocument.class);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setWeightFormatTypeCode("V");
    deliveryDocument.getDeliveryDocumentLines().get(0).setBolWeight(0.45f);

    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).existsByTrackingId(anyString());
  }

  @Test
  public void testCreateAndCompleteParentContainer() {

    // setting child container null , so that only parent container is created.
    instruction.setChildContainers(null);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    try {
      Container completeParentContainer =
          containerService.createAndCompleteParentContainer(
              instruction, instructionRequest, Boolean.FALSE);
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }

    ArgumentCaptor<Container> captor = ArgumentCaptor.forClass(Container.class);
    verify(containerRepository, times(1)).save(captor.capture());
    verify(containerItemRepository, times(1)).saveAll(any());

    assertEquals(captor.getValue().getOnConveyor(), Boolean.FALSE);
  }

  @Test
  public void testCreateAndCompleteParentContainer_Imports() {
    // setting child container null , so that only parent container is created.
    instruction.setChildContainers(null);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6561\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"poTypeCode\": 33,\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"importInd\": true,\n"
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
            + "                \"orderableQuantity\": 3,\n"
            + "                \"warehousePackQuantity\": 3,\n"
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
            + "                \"freightBillQty\": 10,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\",\n"
            + "                \"additionalInfo\" : {"
            + "                                      \"warehouseGroupCode\": \"P\","
            + "                                      \"isNewItem\": false, "
            + "                                      \"warehouseAreaCode\": \"8\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"weightFormatTypeCode\": \"F\","
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\""
            + "                                     }"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"poDcCountry\": \"US\",\n"
            + "        \"financialGroupCode\": \"US\",\n"
            + "\"totalPurchaseReferenceQty\" : 20 \n"
            + "    }");
    try {
      Container completeParentContainer =
          containerService.createAndCompleteParentContainer(
              instruction, instructionRequest, Boolean.FALSE);
      when(configUtils.getConfiguredInstance(
              any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
          .thenReturn(defaultLabelIdProcessor);

    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }

    ArgumentCaptor<Container> captor = ArgumentCaptor.forClass(Container.class);
    verify(containerRepository, times(1)).save(captor.capture());
    verify(containerItemRepository, times(1)).saveAll(any());
    assertTrue(
        captor
            .getValue()
            .getContainerItems()
            .stream()
            .allMatch(
                o ->
                    o.getImportInd()
                        && !StringUtils.isEmpty(o.getPoDCNumber())
                        && !StringUtils.isEmpty(o.getPoDcCountry())));
  }

  @Test
  public void testCreateAndCompleteParentContainer_invalid_bol_weight() {

    Gson gson = new Gson();

    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setBolWeight(null);

    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    try {

      doReturn(true).when(configUtils).isBOLWeightCheckEnabled(any(Integer.class));
      when(configUtils.getConfiguredInstance(
              any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
          .thenReturn(defaultLabelIdProcessor);

      Container completeParentContainer =
          containerService.createAndCompleteParentContainer(
              instruction, instructionRequest, Boolean.FALSE);
      when(configUtils.getConfiguredInstance(
              any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
          .thenReturn(defaultLabelIdProcessor);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "InvalidBolWeightForItem");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Invalid BOL Weight");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Unable to receive item due to Invalid BOL weight for item in GDM.");
    }
  }

  @Test
  public void testCreateAndCompleteParentContainer_invalid_zero_bol_weight() {

    Gson gson = new Gson();

    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setBolWeight(0f);

    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    try {

      doReturn(true).when(configUtils).isBOLWeightCheckEnabled(any(Integer.class));
      when(configUtils.getConfiguredInstance(
              any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
          .thenReturn(defaultLabelIdProcessor);

      Container completeParentContainer =
          containerService.createAndCompleteParentContainer(
              instruction, instructionRequest, Boolean.FALSE);
      when(configUtils.getConfiguredInstance(
              any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
          .thenReturn(defaultLabelIdProcessor);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "InvalidBolWeightForItem");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Invalid BOL Weight");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Unable to receive item due to Invalid BOL weight for item in GDM.");
    }
  }

  @Test
  public void testprocessCreateChildContainersWhenParentContainerisNotPresent()
      throws ReceivingException {

    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setParentTrackingId(instruction.getSsccNumber());
    containerDetails1.setTrackingId("CASE_CONTAINER_TRACKINGID");

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setParentTrackingId(containerDetails1.getTrackingId());
    containerDetails2.setTrackingId("CONTAINER_TRACKINGID");
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    containerService.processCreateChildContainers(
        instruction, instructionRequest, containerDetails1, containerDetails2);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(2)).saveAll(any(List.class));
    verify(containerItemRepository, Mockito.times(2)).saveAll(any(List.class));
  }

  @Test
  public void testprocessCreateChildContainersWhenParentContainerisPresent()
      throws ReceivingException {

    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setParentTrackingId(instruction.getSsccNumber());
    containerDetails1.setTrackingId("CASE_CONTAINER_TRACKINGID");

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setParentTrackingId(containerDetails1.getTrackingId());
    containerDetails2.setTrackingId("CONTAINER_TRACKINGID");
    when(containerPersisterService.checkIfContainerExist(anyString())).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    containerService.processCreateChildContainers(
        instruction, instructionRequest, containerDetails1, containerDetails2);

    // verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).saveAll(any(List.class));
    verify(containerItemRepository, Mockito.times(1)).saveAll(any(List.class));
  }

  @Test
  public void testprocessCreateChildContainersWhenParentContainersPresent()
      throws ReceivingException {

    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setParentTrackingId(instruction.getSsccNumber());
    containerDetails2.setTrackingId("CONTAINER_TRACKINGID");
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(containerPersisterService.checkIfContainerExist(anyString())).thenReturn(true);
    containerService.processCreateChildContainers(
        instruction, instructionRequest, new ContainerDetails(), containerDetails2);

    // verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(1)).saveAll(any(List.class));
    verify(containerItemRepository, Mockito.times(1)).saveAll(any(List.class));
  }

  private Container mockResponseForGetParentContainer(
      String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackingId);

    container.setContainerItems(Arrays.asList(new ContainerItem()));

    return container;
  }

  private Container mockParentContainerWithoutChildrenResponse(
      String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackingId);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("987654321");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);

    container.setContainerItems(Arrays.asList(new ContainerItem()));

    return container;
  }

  private Container createChildContainer(String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackingId);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("987654321");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);

    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }

  private Set<Container> mockResponseForGetContainerIncludesChildren(String trackingId) {
    Container childContainer1 = createChildContainer("12345", "123", 6);
    Container childContainer2 = createChildContainer("12345", "456", 6);
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(childContainer1);
    childContainers.add(childContainer2);
    return childContainers;
  }

  private List<ContainerItem> mockResponseForGetContainerItems(
      List<String> trackingIdList, int quantity) {
    ContainerItem containerItem1 = mockGetContainerItem(trackingIdList.get(0), quantity);
    ContainerItem containerItem2 = mockGetContainerItem(trackingIdList.get(1), quantity);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    return containerItems;
  }

  private ContainerItem mockGetContainerItem(String trackingId, int quantity) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("987654321");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    return containerItem;
  }

  /**
   * Test for pocon palletQty negation
   *
   * @throws ReceivingException
   */
  @Test
  public void testBackoutContainer_PoCon() throws ReceivingException {
    TenantContext.setFacilityNum(32835);
    TenantContext.setFacilityCountryCode("us");

    Container containerInfo = MockContainer.getContainerInfo();
    containerInfo.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    containerInfo.getContainerItems().get(0).setInboundChannelMethod("POCON");
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(containerInfo);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(new HashSet<>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(containerInfo.getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            containerInfo.getDeliveryNumber(), httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void testCreateContainerWithItemMdmWeight()
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Instruction instruction = new Instruction();
    instruction.setId(4L);
    instruction.setDeliveryNumber(20782785L);
    instruction.setActivityName("SSTK");
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setGtin(null);
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("BQ STRIPY");
    instruction.setMessageId("18e1df00-ebf6-11e8-9c25-dd4bfc2a06f1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("1004795674");
    instruction.setPurchaseReferenceLineNumber(null);
    instruction.setProjectedReceiveQty(48);
    instruction.setProviderId("Witron");

    String deliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"1004795674\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"870753\",\n"
            + "    \"vendorNbrDeptSeq\": 870753942,\n"
            + "    \"deptNumber\": \"94\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"20\",\n"
            + "    \"poDCNumber\": \"32612\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"gtin\": \"00729571284458\",\n"
            + "            \"itemUPC\": \"00729571284458\",\n"
            + "            \"caseUPC\": \"10729571284455\",\n"
            + "            \"purchaseReferenceNumber\": \"1004795674\",\n"
            + "            \"purchaseReferenceLineNumber\": 6,\n"
            + "            \"event\": \"\",\n"
            + "            \"purchaseReferenceLineStatus\": \"APPROVED\",\n"
            + "            \"whpkSell\": 30.97,\n"
            + "            \"vendorPackCost\": 30.97,\n"
            + "            \"vnpkQty\": 10,\n"
            + "            \"whpkQty\": 10,\n"
            + "            \"orderableQuantity\": 10,\n"
            + "            \"warehousePackQuantity\": 10,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"openQty\": 45,\n"
            + "            \"expectedQty\": 45,\n"
            + "            \"overageQtyLimit\": 22500,\n"
            + "            \"itemNbr\": 579516308,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 16,\n"
            + "            \"palletHi\": 3,\n"
            + "            \"vnpkWgtQty\": 18,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 3.666,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"BQ\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"BQ STRIPY/CARN FF\",\n"
            + "            \"itemDescription2\": \"\",\n"
            + "            \"isConveyable\": true,\n"
            + "            \"warehouseRotationTypeCode\": \"3\",\n"
            + "            \"firstExpiryFirstOut\": true,\n"
            + "            \"warehouseMinLifeRemainingToReceive\": 7,\n"
            + "            \"profiledWarehouseArea\": \"MAN\",\n"
            + "            \"promoBuyInd\": \"N\",\n"
            + "            \"additionalInfo\": {\n"
            + "                \"warehouseAreaCode\": \"7\",\n"
            + "                \"warehouseGroupCode\": \"P\",\n"
            + "                \"isNewItem\": false,\n"
            + "                \"profiledWarehouseArea\": \"MAN\",\n"
            + "                \"warehouseRotationTypeCode\": \"3\",\n"
            + "                \"weight\": 9.75,\n"
            + "                \"weightFormatTypeCode\": \"F\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 7\n"
            + "            },\n"
            + "            \"operationalInfo\": {\n"
            + "                \"state\": \"ACTIVE\"\n"
            + "            },\n"
            + "            \"freightBillQty\": 45,\n"
            + "            \"activeChannelMethods\": [],\n"
            + "            \"originalChannel\": \"STAPLESTOCK\",\n"
            + "            \"vendorStockNumber\": \"72957128445\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 1201,\n"
            + "    \"weight\": 29578,\n"
            + "    \"weightUOM\": \"LB\",\n"
            + "    \"cubeQty\": 12,\n"
            + "    \"cubeUOM\": \"CF\",\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"WRK\",\n"
            + "    \"poTypeCode\": 20,\n"
            + "    \"totalBolFbq\": 1201,\n"
            + "    \"poDcCountry\": \"US\",\n"
            + "    \"deliveryLegacyStatus\": \"WRK\",\n"
            + "    \"purchaseReferenceMustArriveByDate\": \"May 2, 2020 12:00:00 AM\"\n"
            + "}";
    instruction.setDeliveryDocument(deliveryDocument);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setCtrShippable(false);
    containerDetails.setCtrReusable(false);
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrType("PALLET");
    containerDetails.setTrackingId("A08852000020029222");
    instruction.setContainer(containerDetails);
    instruction.setOrgUnitId(2);

    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    List<DocumentLine> documentLines = new ArrayList<>();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setQuantity(24);
    documentLine.setQuantityUOM("ZA");
    documentLine.setVnpkQty(10);
    documentLine.setWhpkQty(10);
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryNumber(20782785L);
    updateInstructionRequest.setDoorNumber("414");
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);
    updateInstructionRequest.setFlowDescriptor(FLOW_RECEIVE_INTO_OSS);

    Class[] paramArguments = new Class[5];
    paramArguments[0] = Instruction.class;
    paramArguments[1] = ContainerDetails.class;
    paramArguments[2] = Boolean.class;
    paramArguments[3] = Boolean.class;
    paramArguments[4] = UpdateInstructionRequest.class;
    Method privateMethodParentContainer =
        ContainerService.class.getDeclaredMethod("constructContainer", paramArguments);
    privateMethodParentContainer.setAccessible(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    Container container =
        (Container)
            privateMethodParentContainer.invoke(
                containerService,
                instruction,
                containerDetails,
                true,
                false,
                updateInstructionRequest);

    ContainerItem containerItem = container.getContainerItems().get(0);

    assertEquals(container.getWeight(), null);
    assertEquals(containerItem.getVnpkWgtQty(), new Float(9.75));
  }

  @Test
  public void testCreateNonConDockTagContainer() {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber("101");
    instructionRequest.setDeliveryNumber("1234567");
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(containerRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

    Instruction dockTagInstruction = MockInstruction.getDockTagInstruction();
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    Container dockTagContainer =
        containerService.createDockTagContainer(
            dockTagInstruction, instructionRequest, headers, Boolean.TRUE);
    assertNotNull(dockTagContainer);
    assertNotNull(dockTagContainer.getCompleteTs());
    assertNotNull(dockTagContainer.getPublishTs());
    assertEquals(dockTagContainer.getContainerException(), ContainerException.DOCK_TAG.getText());
    assertEquals(dockTagContainer.getLocation(), instructionRequest.getDoorNumber());
    assertFalse(dockTagContainer.getIsConveyable());
    assertFalse(dockTagContainer.getOnConveyor());
  }

  @Test
  public void testCreateDockTagContainer() {

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(containerRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    Instruction dockTagInstruction = MockInstruction.getDockTagInstruction();
    Container dockTagContainer =
        containerService.createDockTagContainer(
            dockTagInstruction, instructionRequest, headers, Boolean.TRUE);
    assertNotNull(dockTagContainer);
    assertNotNull(dockTagContainer.getCompleteTs());
    assertNotNull(dockTagContainer.getPublishTs());
    assertEquals(dockTagContainer.getContainerException(), ContainerException.DOCK_TAG.getText());
    assertEquals(dockTagContainer.getLocation(), instructionRequest.getDoorNumber());
    assertTrue(dockTagContainer.getIsConveyable());
    assertFalse(dockTagContainer.getOnConveyor());
  }

  @Test
  public void testProcessCreateContainersForNonConDockTagPbylLocation() throws Exception {

    UpdateInstructionRequest instructionRequest =
        MockUpdateInstructionRequest.getUpdateInstructionRequest();
    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    instructionRequest.setPbylDockTagId("a328180000000000000123");
    instructionRequest.setPbylLocation("PTR003");
    when(containerRepository.save(any(Container.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    ArgumentCaptor<Container> captor = ArgumentCaptor.forClass(Container.class);
    verify(containerRepository, Mockito.times(1)).save(captor.capture());
    assertEquals(captor.getValue().getLocation(), "PTR003");
  }

  @Test
  public void testProcessCreateContainersForNonConDockTag() throws Exception {

    UpdateInstructionRequest instructionRequest =
        MockUpdateInstructionRequest.getUpdateInstructionRequest();
    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    instructionRequest.setPbylDockTagId("a328180000000000000123");
    DockTag dockTag = new DockTag();
    dockTag.setScannedLocation("PTR001");
    dockTag.setDeliveryNumber(1234567L);
    dockTag.setDockTagId("a328180000000000000123");
    when(configUtils.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);
    when(dockTagService.getDockTagById("a328180000000000000123")).thenReturn(dockTag);
    when(containerRepository.save(any(Container.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    ArgumentCaptor<Container> captor = ArgumentCaptor.forClass(Container.class);
    verify(containerRepository, Mockito.times(1)).save(captor.capture());
    assertEquals(captor.getValue().getLocation(), "PTR001");
  }

  @Test
  public void testUpdatePalletTiHiWithItemOverride() throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(6);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.FALSE, httpHeaders);

    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 6);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 4);
  }

  @Test
  public void testUpdatePalletTiHiWithItemOverride_includeHiChange() throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletHi(6);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.FALSE, httpHeaders);

    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 6);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 6);
  }

  @Test
  public void testUpdatePalletTiHiWithItemOverride_adjustQuantityValidation_invalidQty() {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletHi(6);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    try {
      containerService.adjustQuantityValidation(
          deliveryNumber,
          "234-ABC",
          37,
          MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    } catch (Exception ex) {
      assert ex.getMessage()
          .contains(
              "The new LPN quantity entered exceeds the item's Ti x Hi. Please verify the quantity entered.");
    }
  }

  @Test
  public void testUpdateContainerData() throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenReturn(new InventoryContainerDetails(12, null, null, 0));

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.TRUE, httpHeaders);

    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getInventoryQuantity().intValue(), 12);
    assertEquals(
        container.getContainerItems().get(0).getInventoryQuantityUOM(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void testUpdateContainerData_INV_NotFoundExceptionThrown() throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.INVENTORY_NOT_FOUND,
                String.format(INVENTORY_NOT_FOUND_MESSAGE, "lpn")));

    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, httpHeaders);
      fail("ReceivingDataNotFoundException should be thrown");
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(
          exc.getErrorCode(), ExceptionCodes.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND);
    }
  }

  @Test
  public void
      testUpdateContainerData_WFSCorrection_ContainerStatusValidationEnabled_InvalidContainerStatus_MobileUI()
          throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), null, 0));
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    mockHeaders.set(IS_KOTLIN_CLIENT, "true");
    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      fail("ReceivingBadDataException should be thrown");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_FOR_CORRECTION_INV_STATUS_NOT_PICKED);
    }
  }

  @Test
  public void
      testUpdateContainerData_WFSCorrection_ContainerStatusValidationEnabled_InvalidContainerStatus_WebUI()
          throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), null, 0));
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      fail("ReceivingBadDataException should be thrown");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes
              .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_INV_STATUS_NOT_PICKED_OR_AVAILABLE);
    }
  }

  @Test
  public void testUpdateContainerData_validateInventoryStatusIfEnabled() throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("SOFT_DELETE");
    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.SOFT_DELETE.name(), null, 0));
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      containerService.updateContainerData(
          MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      fail("ReceivingBadDataException should be thrown");
    } catch (ReceivingBadDataException exc) {
      assertEquals(
          exc.getErrorCode(),
          ExceptionCodes
              .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_INV_STATUS_NOT_PICKED_OR_AVAILABLE);
    }
  }

  @Test
  public void testUpdateContainerData_validateInventoryStatusIfNotEnabled()
      throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn(EMPTY_STRING);
    when(inventoryService.getInventoryContainerDetails(anyString(), any()))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.SOFT_DELETE.name(), null, 0));
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
    assertNotNull(container);
  }

  @Test
  public void testUpdatePalletTiHiWithGdm() throws ReceivingException {
    doReturn(Optional.empty())
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPalletTie(3);
    deliveryDocumentLine.setPalletHigh(2);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPurchaseReferenceNumber(poNumber);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);

    when(deliveryServiceRetryableImpl.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any()))
        .thenReturn(gdmPOLineResponse);

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.FALSE, httpHeaders);

    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 3);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 2);
  }

  @Test
  public void testCreateContainerWithVariableWeightItem()
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException,
          ReceivingException {
    doReturn(true).when(configUtils).isBOLWeightCheckEnabled(any(Integer.class));
    Instruction instruction = new Instruction();
    instruction.setId(21659L);
    instruction.setDeliveryNumber(40089354L);
    instruction.setActivityName("SSTK");
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setGtin(null);
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("BQ STRIPY");
    instruction.setMessageId("18e1df00-ebf6-11e8-9c25-dd4bfc2a06f1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("1004795674");
    instruction.setPurchaseReferenceLineNumber(null);
    instruction.setProjectedReceiveQty(48);
    instruction.setProviderId("Witron");

    String deliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"1004795674\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"870753\",\n"
            + "    \"vendorNbrDeptSeq\": 870753942,\n"
            + "    \"deptNumber\": \"94\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"20\",\n"
            + "    \"poDCNumber\": \"32612\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"gtin\": \"00729571284458\",\n"
            + "            \"itemUPC\": \"00729571284458\",\n"
            + "            \"caseUPC\": \"10729571284455\",\n"
            + "            \"purchaseReferenceNumber\": \"1004795674\",\n"
            + "            \"purchaseReferenceLineNumber\": 6,\n"
            + "            \"event\": \"\",\n"
            + "            \"purchaseReferenceLineStatus\": \"APPROVED\",\n"
            + "            \"whpkSell\": 30.97,\n"
            + "            \"vendorPackCost\": 30.97,\n"
            + "            \"vnpkQty\": 10,\n"
            + "            \"whpkQty\": 10,\n"
            + "            \"orderableQuantity\": 10,\n"
            + "            \"warehousePackQuantity\": 10,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"openQty\": 45,\n"
            + "            \"expectedQty\": 45,\n"
            + "            \"overageQtyLimit\": 22500,\n"
            + "            \"itemNbr\": 579516308,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 16,\n"
            + "            \"palletHi\": 3,\n"
            + "            \"vnpkWgtQty\": 18,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 3.666,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"BQ\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"BQ STRIPY/CARN FF\",\n"
            + "            \"itemDescription2\": \"\",\n"
            + "            \"isConveyable\": true,\n"
            + "            \"warehouseRotationTypeCode\": \"3\",\n"
            + "            \"firstExpiryFirstOut\": true,\n"
            + "            \"warehouseMinLifeRemainingToReceive\": 7,\n"
            + "            \"profiledWarehouseArea\": \"MAN\",\n"
            + "            \"promoBuyInd\": \"N\",\n"
            + "            \"additionalInfo\": {\n"
            + "                \"warehouseAreaCode\": \"7\",\n"
            + "                \"warehouseGroupCode\": \"P\",\n"
            + "                \"isNewItem\": false,\n"
            + "                \"profiledWarehouseArea\": \"MAN\",\n"
            + "                \"warehouseRotationTypeCode\": \"3\",\n"
            + "                \"weight\": 9.75,\n"
            + "                \"weightFormatTypeCode\": \"V\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 7\n"
            + "            },\n"
            + "            \"operationalInfo\": {\n"
            + "                \"state\": \"ACTIVE\"\n"
            + "            },\n"
            + "            \"freightBillQty\": 45,\n"
            + "            \"bolWeight\": 24,\n"
            + "            \"activeChannelMethods\": [],\n"
            + "            \"originalChannel\": \"STAPLESTOCK\",\n"
            + "            \"vendorStockNumber\": \"72957128445\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 1201,\n"
            + "    \"weight\": 29578,\n"
            + "    \"weightUOM\": \"LB\",\n"
            + "    \"cubeQty\": 12,\n"
            + "    \"cubeUOM\": \"CF\",\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"WRK\",\n"
            + "    \"poTypeCode\": 20,\n"
            + "    \"totalBolFbq\": 1201,\n"
            + "    \"poDcCountry\": \"US\",\n"
            + "    \"deliveryLegacyStatus\": \"WRK\",\n"
            + "    \"purchaseReferenceMustArriveByDate\": \"May 2, 2020 12:00:00 AM\"\n"
            + "}";
    instruction.setDeliveryDocument(deliveryDocument);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setCtrShippable(false);
    containerDetails.setCtrReusable(false);
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrType("PALLET");
    containerDetails.setTrackingId("A08852000020029222");
    instruction.setContainer(containerDetails);

    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    List<DocumentLine> documentLines = new ArrayList<>();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setQuantity(36);
    documentLine.setQuantityUOM("ZA");
    documentLine.setVnpkQty(8);
    documentLine.setWhpkQty(8);
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryNumber(40089354L);
    updateInstructionRequest.setDoorNumber("414");
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    Class[] paramArguments = new Class[5];
    paramArguments[0] = Instruction.class;
    paramArguments[1] = ContainerDetails.class;
    paramArguments[2] = Boolean.class;
    paramArguments[3] = Boolean.class;
    paramArguments[4] = UpdateInstructionRequest.class;
    Method privateMethodParentContainer =
        ContainerService.class.getDeclaredMethod("constructContainer", paramArguments);
    privateMethodParentContainer.setAccessible(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    Container container =
        (Container)
            privateMethodParentContainer.invoke(
                containerService,
                instruction,
                containerDetails,
                true,
                false,
                updateInstructionRequest);

    ContainerItem containerItem = container.getContainerItems().get(0);

    assertEquals(containerItem.getQuantity().intValue(), 360);
    assertEquals(containerItem.getVnpkWgtQty(), new Float(24.00));
    assertEquals(containerItem.getVnpkWgtUom(), ReceivingConstants.Uom.LB);
    assertEquals(
        containerItem.getWeightFormatTypeCode(),
        ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE);
    assertEquals(container.getWeight(), new Float(864.00));
    assertEquals(container.getWeightUOM(), ReceivingConstants.Uom.LB);
  }

  @Test
  public void testBackoutContainer_NotPublish_ReceiptsSummary() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn(
            "{\"deliveryNumber\":95350003,\"deliveryStatus\":\"WRK\",\"stateReasonCodes\":[\"WORKING\"]}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainer_Publish_ReceiptsSummary() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getContainerInfo().getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.getContainerInfo().getDeliveryNumber(), httpHeaders))
        .thenReturn(
            "{\"deliveryNumber\":95350003,\"deliveryStatus\":\"OPN\",\"stateReasonCodes\":[\"PENDING_PROBLEM\"]}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    containerService.backoutContainer(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testPublishMultipleContainersToInventory1() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    when(configUtils.getConfiguredFeatureFlag(any(), eq(ENABLE_ATLAS_INVENTORY_TEST), anyBoolean()))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    MULTIPLE_PALLET_RECEIVING_FLOW)));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ABORT_CALL_FOR_KAFKA_ERR, true);

    containerService.publishMultipleContainersToInventory(transformer.transformList(containers));
    verify(kafkaHawkshawPublisher, times(0))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
  }

  @Test
  public void testPublishMultipleContainersToInventory() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    containerService.publishMultipleContainersToInventory(transformer.transformList(containers));

    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
  }

  @Test
  public void testPublishContainerOnSecureKafkaCluster() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    containerService.publishMultipleContainersToInventory(transformer.transformList(containers));
    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
  }

  @Test
  public void testPublishContainerOnSecureKafkaCluster_PopulateFreightTypeHeaderAsSSTK() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    when(configUtils.getConfiguredFeatureFlag(
            any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean()))
        .thenReturn(true);
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    containerService.publishMultipleContainersToInventory(transformer.transformList(containers));
    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean());
  }

  @Test
  public void testPublishContainerOnSecureKafkaCluster_PopulateFreightTypeHeaderAsDA() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    containers.get(0).getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    when(configUtils.getConfiguredFeatureFlag(
            any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean()))
        .thenReturn(true);
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    containerService.publishMultipleContainersToInventory(transformer.transformList(containers));
    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean());
  }

  @Test
  public void
      testPublishContainerOnSecureKafkaCluster_PopulateFreightTypeHeaderAsDAChildContainers() {
    List<ContainerDTO> containers =
        Collections.singletonList(MockContainer.getContainerWithChildContainers());
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    when(configUtils.getConfiguredFeatureFlag(
            any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean()))
        .thenReturn(true);
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    containerService.publishMultipleContainersToInventory(containers);
    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), any(HashMap.class), anyString());
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(any(), eq(IS_EVENT_TYPE_HEADER_ENABLED), anyBoolean());
  }

  @Test
  public void testPublishContainerOnSecureKafkaClusterWithHeader() {
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    String payload = gson.toJson(containers);
    settableListenableFuture.set(new Object());
    KafkaHelper.buildKafkaMessage(1234, payload, "test");
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    httpHeaders.set(IGNORE_INVENTORY, "fasle");
    containerService.publishMultipleContainersToInventory(
        transformer.transformList(containers), httpHeaders);
    ArgumentCaptor<Map> httpHeadersCaptor = ArgumentCaptor.forClass(Map.class);

    verify(kafkaHawkshawPublisher, times(1))
        .publishKafkaWithHawkshaw(
            any(Object.class), anyString(), anyString(), httpHeadersCaptor.capture(), anyString());

    assertEquals(
        containers.get(0).getContainerItems().get(0).getTrackingId(),
        httpHeadersCaptor.getValue().get(ReceivingConstants.IDEM_POTENCY_KEY));
  }

  @SneakyThrows
  @Test
  public void test_finalizePoOsdrToGdm() {

    final FinalizePORequestBody finalizePORequestBody = new FinalizePORequestBody();
    containerService.postFinalizePoOsdrToGdm(
        httpHeaders,
        container.getDeliveryNumber(),
        containerItem.getPurchaseReferenceNumber(),
        finalizePORequestBody);

    verify(gdmRestApiClient, times(1))
        .persistFinalizePoOsdrToGdm(anyLong(), anyString(), any(), any());
  }

  @Test
  public void testGetContainerLabelsByTrackingIdReturnsSuccessResponse()
      throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(MockInstruction.getInstructionResponse()));

    Map<String, Object> printJob =
        containerService.getContainerLabelsByTrackingIds(getTrackingIds(), httpHeaders);
    assertNotNull(printJob);
    assertNotNull(printJob.get(PRINT_HEADERS_KEY));
    assertNotNull(printJob.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(printJob.get(PRINT_REQUEST_KEY));

    verify(containerRepository, times(1))
        .getInstructionIdsByTrackingIds(anyList(), any(Integer.class), any(String.class));
    verify(instructionRepository, times(1)).findByIdIn(anyList());
  }

  @Test
  public void reprint_Only_RequestedLabels_with_2() throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(MockInstruction.getInstructionResponseWithMultipleLabels()));

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");

    Map<String, Object> response =
        containerService.getContainerLabelsByTrackingIds(getTrackingIds_CC(), httpHeaders);

    assertNotNull(response);
    assertNotNull(response.get(PRINT_HEADERS_KEY));
    assertNotNull(response.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(response.get(PRINT_REQUEST_KEY));
    Assert.assertEquals(
        ((List<Object>) response.get(ReceivingConstants.PRINT_REQUEST_KEY)).size(), 2);
  }

  @Test
  public void reprint_Only_RequestedLabels_with_MultipleInstructions()
      throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    List<Instruction> instruction =
        MockInstruction.getMultipleInstructionResponseWithMultipleLabels();
    when(instructionRepository.findByIdIn(anyList())).thenReturn(instruction);

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");

    Map<String, Object> response =
        containerService.getContainerLabelsByTrackingIds(
            getTrackingIds_CC_MultipleInstruction(), httpHeaders);

    assertNotNull(response);
    assertNotNull(response.get(PRINT_HEADERS_KEY));
    assertNotNull(response.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(response.get(PRINT_REQUEST_KEY));
    Assert.assertEquals(
        ((List<Object>) response.get(ReceivingConstants.PRINT_REQUEST_KEY)).size(), 2);
  }

  @Test
  public void reprint_Only_RequestedLabels_with_pallet_Label()
      throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(MockInstruction.getInstructionResponseWithMultipleLabels()));
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");
    try {
      Map<String, Object> response =
          containerService.getContainerLabelsByTrackingIds(
              getTrackingIds_CC_PalletLabel(), httpHeaders);
      fail("ReceivingBadDataException should be thrown");
    } catch (ReceivingBadDataException error) {
      assertEquals(error.getErrorCode(), ExceptionCodes.PALLET_LABEL_CAN_NOT_BE_PRINTED);
    }
  }

  @Test
  public void reprint_Only_RequestedLabels_with_1() throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(MockInstruction.getInstructionResponseWithMultipleLabels()));

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");
    Map<String, Object> response =
        containerService.getContainerLabelsByTrackingIds(getTrackingIds_CC_1(), httpHeaders);

    assertNotNull(response);
    assertNotNull(response.get(PRINT_HEADERS_KEY));
    assertNotNull(response.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(response.get(PRINT_REQUEST_KEY));
    Assert.assertEquals(
        ((List<Object>) response.get(ReceivingConstants.PRINT_REQUEST_KEY)).size(), 1);
  }

  @Test
  public void reprint_Only_RequestedLabels_1_with_DISABLE_PRINTING_MASTER_PALLET_LPN_False()
      throws ReceivingException, IOException {
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(3221l);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    when(instructionRepository.findByIdIn(anyList()))
        .thenReturn(Arrays.asList(MockInstruction.getInstructionResponseWithMultipleLabels()));

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(false);
    Map<String, Object> response =
        containerService.getContainerLabelsByTrackingIds(
            getTrackingIds_CC_PalletLabel(), httpHeaders);

    assertNotNull(response);
    assertNotNull(response.get(PRINT_HEADERS_KEY));
    assertNotNull(response.get(PRINT_CLIENT_ID_KEY));
    assertNotNull(response.get(PRINT_REQUEST_KEY));
    Assert.assertEquals(
        ((List<Object>) response.get(ReceivingConstants.PRINT_REQUEST_KEY)).size(), 1);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerLabelByTrackingIdThrowsExceptionWhenContainerIsNotFound()
      throws ReceivingException {
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(null);
    containerService.getContainerLabelsByTrackingIds(getTrackingIds(), httpHeaders);
    verify(containerRepository, times(1))
        .getInstructionIdsByTrackingIds(anyList(), any(Integer.class), any(String.class));
    verify(instructionRepository, times(1)).findByIdIn(anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetContainerLabelByTrackingIdThrowsExceptionForInvalidData()
      throws ReceivingException {
    containerService.getContainerLabelsByTrackingIds(Arrays.asList(), httpHeaders);
    verify(containerRepository, times(0))
        .getInstructionIdsByTrackingIds(anyList(), any(Integer.class), any(String.class));
    verify(instructionRepository, times(0)).findByIdIn(anyList());
  }

  @Test
  public void publishContainerListWithStatusTestWithException() throws ReceivingException {
    Set<Container> containerList = new HashSet<>();
    containerList.add(container);
    containerService.publishContainerListWithStatus(getTrackingIds(), httpHeaders, STATUS_ACTIVE);
  }

  private List<String> getTrackingIds() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("9784587526");
    trackingIds.add("9434343");
    return trackingIds;
  }

  private List<String> getTrackingIds_CC() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("c015820000200000000108791");
    trackingIds.add("c015820000200000000108790");
    return trackingIds;
  }

  private List<String> getTrackingIds_CC_MultipleInstruction() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("c015820000200000000108791");
    trackingIds.add("c015820000200000000108690");
    return trackingIds;
  }

  private List<String> getTrackingIds_CC_1() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("c015820000200000000108791");
    return trackingIds;
  }

  private List<String> getTrackingIds_CC_PalletLabel() {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("E01582000020007018");
    return trackingIds;
  }

  @Test
  public void testGetContainerByItemNumber() {
    when(containerItemRepository.findFirstByItemNumberOrderByIdDesc(anyLong()))
        .thenReturn(Optional.of(new ContainerItem()));

    ContainerItem containerItem =
        containerService.getContainerByItemNumber(Long.parseLong("9398504"));

    verify(containerItemRepository, times(1)).findFirstByItemNumberOrderByIdDesc(anyLong());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Container not found by itemNumber=9398504")
  public void testGetContainerByItemNumber_NotFound() {
    when(containerItemRepository.findFirstByItemNumberOrderByIdDesc(anyLong()))
        .thenReturn(Optional.empty());

    ContainerItem containerItem =
        containerService.getContainerByItemNumber(Long.parseLong("9398504"));

    verify(containerItemRepository, times(1)).findFirstByItemNumberOrderByIdDesc(anyLong());
  }

  @Test
  public void testPostReceiptsReceiveAsCorrection() {
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());

    containerService.postReceiptsReceiveAsCorrection(container, httpHeaders);

    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(asyncPersister, times(1))
        .persistAsyncHttp(any(), any(), any(), headerCaptor.capture(), any());
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));
  }

  @Test
  public void testPostReceiptsReceiveAsCorrection_Inventory2Url_backwardCompatible() {
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32987", INV_V2_ENABLED, false);
    doReturn("https://gls-atlas-inventory-core-gdc-qa.walmart.com")
        .when(appConfig)
        .getInventoryCoreBaseUrl();

    // call
    containerService.postReceiptsReceiveAsCorrection(container, httpHeaders);

    // verify
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> argumentCaptorUrl = ArgumentCaptor.forClass(String.class);
    verify(asyncPersister, times(1))
        .persistAsyncHttp(any(), argumentCaptorUrl.capture(), any(), headerCaptor.capture(), any());
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    final String argumentCaptorUrlValue = argumentCaptorUrl.getValue();
    assertEquals(
        argumentCaptorUrlValue,
        "https://gls-atlas-inventory-core-gdc-qa.walmart.com/inventory/inventories/receipt?flow=rcvCorrection");
  }

  @Test(expectedExceptions = Exception.class)
  public void testPublishDockTagContainerWithException() throws Exception {
    when(containerServ.getContainerByTrackingId(anyString()))
        .thenThrow(new Exception("Unable to publish"));
    dockTagServ.publishDockTagContainer(
        MockHttpHeaders.getHeaders(), trackingId, ReceivingConstants.STATUS_ACTIVE);
    verify(containerServ, times(0))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
  }

  /** test publishDockTagContainer method with exception */
  @Test
  public void testPublishDockTagContainerCoverException() {
    try {
      when(containerServ.getContainerByTrackingId(anyString())).thenReturn(container1);
      when(containerPersisterService.saveAndFlushContainer(container1)).thenReturn(container1);
      doNothing()
          .when(containerServ)
          .publishExceptionContainer(container1, MockHttpHeaders.getHeaders(), Boolean.TRUE);
      containerServ.publishContainerWithStatus(
          trackingId, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
      verify(containerServ, times(0))
          .publishExceptionContainer(container1, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    } catch (Exception e) {
      logger.info(e.getMessage());
    }
  }

  /** test publishDockTagContainer method */
  @Test
  public void testPublishDockTagContainer() {
    try {
      containerService.publishContainerWithStatus(
          trackingId, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
      verify(containerService, times(1))
          .publishExceptionContainer(container1, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  /**
   * test publishContainerListWithStatus checking for exception
   *
   * @throws ReceivingException
   */
  @Test(expectedExceptions = Exception.class)
  public void testPublishContainerListWithStatusTestWithException() throws ReceivingException {
    Set<Container> containerList = new HashSet<>();
    containerList.add(container);
    when(containerService.getContainerListByTrackingIdList(anyList()))
        .thenThrow(ReceivingException.class);
    containerService.publishContainerListWithStatus(getTrackingIds(), httpHeaders, STATUS_ACTIVE);
  }

  /** This method will test the publishExceptionContainer method(); */
  @Test
  public void testPublishExceptionContainer() {
    container.setChildContainers(ContainerChildContainers);
    when(configUtils.getConfiguredInstance(
            any(),
            eq(ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER),
            eq(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER),
            any()))
        .thenReturn(jmsExceptionContainerPublisher);
    containerService.publishExceptionContainer(
        container, MockHttpHeaders.getHeaders(), Boolean.TRUE);
  }

  /** This method will test the testPublishContainerWithStatus method(); */
  @Test
  public void testPublishContainerWithStatus() {
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    containerServ.publishContainerWithStatus(
        trackingId, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
  }

  /** This method will test the testPublishContainerWithStatus method(); */
  @Test
  public void testPublishContainerWithStatusForCompleteRun() {
    when(containerService.findByTrackingId(trackingId)).thenReturn(container);
    when(containerPersisterService.saveAndFlushContainer(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getConfiguredInstance(
            any(),
            eq(ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER),
            eq(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER),
            any()))
        .thenReturn(jmsExceptionContainerPublisher);
    doNothing().when(jmsExceptionContainerPublisher).publish(any(), any());
    containerService.publishContainerWithStatus(
        trackingId, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
  }

  /** This method will test the testPublishContainerWithStatus method(); */
  @Test
  public void testPublishContainerListWithStatusForCompleteRun() throws ReceivingException {
    List<String> trackingIdList = new ArrayList<>();
    trackingIdList.add(trackingId);
    Set<Container> containers = new HashSet<>();
    containers.add(container);
    when(containerService.getContainerListByTrackingIdList(trackingIdList)).thenReturn(containers);
    when(containerPersisterService.saveAndFlushContainer(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getConfiguredInstance(
            any(),
            eq(ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER),
            eq(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER),
            any()))
        .thenReturn(jmsExceptionContainerPublisher);
    doNothing().when(jmsExceptionContainerPublisher).publish(any(), any());
    containerService.publishContainerListWithStatus(
        trackingIdList, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
  }

  /** This method will test the testPublishContainerWithStatus method(); */
  @Test
  public void testPublishContainerListWithStatusForCompleteRunExeption() throws ReceivingException {
    List<String> trackingIdList = new ArrayList<>();
    trackingIdList.add(trackingId);
    Set<Container> containers = new HashSet<>();
    containers.add(container);
    // when(containerService.getContainerListByTrackingIdList(trackingIdList)).thenThrow(Exception.class);
    when(containerPersisterService.saveAndFlushContainer(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    containerServ.publishContainerListWithStatus(
        null, MockHttpHeaders.getHeaders(), ReceivingConstants.STATUS_ACTIVE);
  }

  /** This method will test the testPublishContainerWithStatus method(); */
  @Test
  public void testPublishContainerWithStatusWithException() {
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(null);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    containerServ.publishContainerWithStatus(
        trackingId, httpHeaders, ReceivingConstants.STATUS_ACTIVE);
  }

  @Test
  public void testGetLastTouchedContainerByUpcAndItemNumber() {
    when(containerItemRepository.findTopByItemNumberAndItemUPCOrderByIdDesc(anyLong(), anyString()))
        .thenReturn(Optional.ofNullable(containerItem));
    ContainerItem containerItem =
        containerService.getContainerItemByUpcAndItemNumber(upc, itemNumber);
    assertNotNull(containerItem.getItemNumber());
  }

  @Test
  public void testGetLastTouchedContainerByUpcAndItemNumberNoContainerItemFound() {
    when(containerItemRepository.findTopByItemNumberAndItemUPCOrderByIdDesc(anyLong(), anyString()))
        .thenReturn(Optional.ofNullable(null));
    try {
      ContainerItem containerItem =
          containerService.getContainerItemByUpcAndItemNumber(upc, itemNumber);
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getDescription(),
          "Container item data not found for itemNumber= 678945 and upc= 2345678912");
      assertEquals(e.getErrorCode(), ExceptionCodes.CONTAINER_ITEM_DATA_NOT_FOUND);
    }
  }

  @Test
  public void testPublishReceiptsInventoryNotEnabled() {
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(false);
    doNothing()
        .when(kafkaHawkshawPublisher)
        .publishKafkaWithHawkshaw(any(), anyString(), anyString(), any(), anyString());
    containerService.publishDockTagContainer(MockContainer.getContainerInfo());
    verify(secureKafkaTemplate, times(0)).send(any(Message.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testPublishReceiptsThrowsAnException() {
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", secureKafkaTemplate);
    when(secureKafkaTemplate.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    ReceivingConstants.CONTAINERS_PUBLISH)));
    containerService.publishDockTagContainer(MockContainer.getContainerInfo());
  }

  @Test
  public void testPublishReceiptsOnSecureKafkaIsSuccess() {
    ReflectionTestUtils.setField(containerService, "secureKafkaTemplate", secureKafkaTemplate);
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    settableListenableFuture.set(new Object());
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(true);
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(settableListenableFuture);
    containerService.publishDockTagContainer(MockContainer.getContainerInfo());
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testGetContainerByDeliveryNumberWithProcessor() throws ReceivingException {
    ContainerService containerServiceTemp = Mockito.spy(containerService);
    doReturn(containers).when(containerServiceTemp).getContainerByDeliveryNumber(anyLong());
    Consumer<Container> processContainer =
        (container) -> {
          if (nonNull(container.getSsccNumber())
              && !org.apache.commons.lang3.StringUtils.equalsIgnoreCase(
                  container.getTrackingId(), container.getSsccNumber())) {
            container.setTrackingId(container.getSsccNumber());
            if (!CollectionUtils.isEmpty(container.getContainerItems()))
              container
                  .getContainerItems()
                  .forEach(containerItem -> containerItem.setTrackingId(container.getSsccNumber()));
          }
        };
    List<Container> containerList =
        containerServiceTemp.getContainerByDeliveryNumber(deliveryNumber, processContainer);
    Assert.assertNotNull(containerList);
    for (Container container : containerList)
      Assert.assertEquals(container.getTrackingId(), container.getSsccNumber());
    verify(containerServiceTemp, times(1)).getContainerByDeliveryNumber(anyLong());
  }

  @Test
  public void testGetContainerByDeliveryNumberWithoutProcessor() throws ReceivingException {
    ContainerService containerServiceTemp = Mockito.spy(containerService);
    doReturn(containers).when(containerServiceTemp).getContainerByDeliveryNumber(anyLong());
    List<Container> containerList =
        containerServiceTemp.getContainerByDeliveryNumber(deliveryNumber, null);
    Assert.assertNotNull(containerList);
    Assert.assertEquals(containers.size(), containerList.size());
    verify(containerServiceTemp, times(1)).getContainerByDeliveryNumber(anyLong());
  }

  @Test
  public void testProcessCreateContainersMapWeightFromGLS() throws Exception {

    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_MANUAL_GDC_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getCcmValue(
            32987, ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);

    instruction.getContainer().setGlsWeightUOM("LB");
    instruction.getContainer().setGlsWeight(386.7192);
    instruction.getContainer().setGlsTimestamp("2022-05-25T00:00:00.000Z");

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
  }

  @Test
  public void testProcessCreateContainersMapGLSTimestampParseException() throws Exception {

    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_MANUAL_GDC_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getCcmValue(
            32987, ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    when(configUtils.getOrgUnitId()).thenReturn("5");

    instruction.getContainer().setGlsWeightUOM("LB");
    instruction.getContainer().setGlsWeight(386.7192);
    instruction.getContainer().setGlsTimestamp("2022-05-25");

    containerService.processCreateContainers(instruction, instructionRequest, httpHeaders);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
  }

  @Test
  public void testFindOneContainerByInstructionId_noContainerFound() {
    when(containerRepository.findByInstructionId(anyLong())).thenReturn(null);
    Optional<Container> ret = containerService.findOneContainerByInstructionId(anyLong());
    verify(containerRepository, Mockito.times(1)).findByInstructionId(anyLong());
    verify(containerItemRepository, Mockito.times(0)).findByTrackingId(anyString());
    assertFalse(ret.isPresent());
  }

  @Test
  public void testFindOneContainerByInstructionId_ContainerFound() {
    when(containerRepository.findByInstructionId(anyLong())).thenReturn(containers);
    when(containerItemRepository.findByTrackingId(any())).thenReturn(containerItemList);
    Optional<Container> ret = containerService.findOneContainerByInstructionId(anyLong());
    verify(containerRepository, Mockito.times(1)).findByInstructionId(anyLong());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingId(anyString());
    assertTrue(ret.isPresent());
  }

  @Test
  public void testAdjustQuantityInInventoryService() throws ReceivingException {
    ContainerItem containerItem1 = container1.getContainerItems().get(0);
    String correlationId = "cId";
    ContainerItem containerItem2 = container1.getContainerItems().get(0);
    containerItem2.setTrackingId("trackingId");
    when(restUtils.put(anyString(), any(HttpHeaders.class), anyMap(), anyString()))
        .thenReturn(new ResponseEntity<String>(OK));

    containerService.adjustQuantityInInventoryService(
        correlationId,
        containerItem2.getTrackingId(),
        Integer.valueOf(5),
        httpHeaders,
        containerItem2,
        Integer.valueOf(containerItem1.getQuantity()));

    ArgumentCaptor<String> jsonValueCaptor = ArgumentCaptor.forClass(String.class);
    verify(restUtils, times(1))
        .put(anyString(), any(HttpHeaders.class), anyMap(), jsonValueCaptor.capture());

    Map<String, Object> keyValue = gson.fromJson(jsonValueCaptor.getValue(), LinkedHashMap.class);

    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID),
        containerItem2.getTrackingId());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_BASE_DIVISION_CODE),
        containerItem2.getBaseDivisionCode());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM),
        containerItem2.getQuantityUOM());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_FINANCIAL_REPORTING_GROUP),
        containerItem2.getFinancialReportingGroupCode());
    assertEquals(
        ((Double) keyValue.get(INVENTORY_ADJUSTMENT_REASON_CODE)),
        Double.valueOf(ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE));
  }

  @Test
  public void testAdjustQuantityByEachesInInventoryService() throws ReceivingException {

    Gson gson = new Gson();
    String correlationId = "cId";

    ContainerItem containerItem2 = container1.getContainerItems().get(0);
    containerItem2.setTrackingId("trackingId");
    when(restUtils.put(anyString(), any(HttpHeaders.class), anyMap(), anyString()))
        .thenReturn(new ResponseEntity<String>(OK));

    containerService.adjustQuantityByEachesInInventoryService(
        correlationId,
        containerItem2.getTrackingId(),
        Integer.valueOf(5),
        httpHeaders,
        containerItem2,
        Integer.valueOf(containerItem1.getQuantity()));

    ArgumentCaptor<String> jsonValueCaptor = ArgumentCaptor.forClass(String.class);
    verify(restUtils, times(1))
        .put(anyString(), any(HttpHeaders.class), anyMap(), jsonValueCaptor.capture());

    Map<String, Object> keyValue = gson.fromJson(jsonValueCaptor.getValue(), LinkedHashMap.class);

    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID),
        containerItem2.getTrackingId());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_BASE_DIVISION_CODE),
        containerItem2.getBaseDivisionCode());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM),
        containerItem2.getQuantityUOM());
    assertEquals(
        keyValue.get(ReceivingConstants.INVENTORY_FINANCIAL_REPORTING_GROUP),
        containerItem2.getFinancialReportingGroupCode());
    assertEquals(
        ((Double) keyValue.get(INVENTORY_ADJUSTMENT_REASON_CODE)),
        Double.valueOf(ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE));
  }

  @Test
  public void testAdjustQuantityByEachesInInventoryService_Inventory2Url()
      throws ReceivingException {

    Gson gson = new Gson();
    String correlationId = "cId";

    ContainerItem containerItem2 = container1.getContainerItems().get(0);
    final String trackingId1 = "abc123";
    containerItem2.setTrackingId(trackingId1);
    when(restUtils.put(anyString(), any(HttpHeaders.class), anyMap(), anyString()))
        .thenReturn(new ResponseEntity<String>(OK));

    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32987", INV_V2_ENABLED, false);
    doReturn("https://gls-atlas-inventory-core-gdc-qa.walmart.com")
        .when(appConfig)
        .getInventoryCoreBaseUrl();
    doReturn(new ResponseEntity<>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(anyString(), any(), any(), any());

    containerService.adjustQuantityByEachesInInventoryService(
        correlationId,
        containerItem2.getTrackingId(),
        Integer.valueOf(5),
        httpHeaders,
        containerItem2,
        Integer.valueOf(containerItem1.getQuantity()));

    ArgumentCaptor<String> requestArgCaptorValue = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> urlArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> headerArgument = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restUtils, times(1))
        .post(
            urlArgumentCaptor.capture(),
            headerArgument.capture(),
            anyMap(),
            requestArgCaptorValue.capture());

    final HttpHeaders headerArgumentValue = headerArgument.getValue();
    assertEquals(headerArgumentValue.getFirst(FLOW_NAME), ADJUSTMENT_FLOW);
    assertEquals(headerArgumentValue.getFirst(IDEM_POTENCY_KEY), correlationId + "-" + trackingId1);

    final String urlArgumentCaptorValue = urlArgumentCaptor.getValue();
    assertEquals(
        urlArgumentCaptorValue,
        "https://gls-atlas-inventory-core-gdc-qa.walmart.com/container/item/adjust");
    final String invAdjustRequestCaptorJson = requestArgCaptorValue.getValue();
    InventoryAdjustRequest inventoryAdjustRequestCaptor =
        gson.fromJson(invAdjustRequestCaptorJson, InventoryAdjustRequest.class);
    assertNotNull(inventoryAdjustRequestCaptor);
    final AdjustmentData adjustmentDataActual = inventoryAdjustRequestCaptor.getAdjustmentData();
    assertNotNull(adjustmentDataActual);
    assertEquals(adjustmentDataActual.getTrackingId(), containerItem2.getTrackingId());
    assertEquals(adjustmentDataActual.getReasonCode(), INVENTORY_RECEIVING_CORRECTION_REASON_CODE);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAdjustQuantityByEachesInInventoryService_throwsException()
      throws ReceivingException {

    String correlationId = "cId";

    ContainerItem containerItem2 = container1.getContainerItems().get(0);
    containerItem2.setTrackingId("trackingId");
    when(restUtils.put(anyString(), any(HttpHeaders.class), anyMap(), anyString()))
        .thenReturn(new ResponseEntity<String>(BAD_REQUEST));

    containerService.adjustQuantityByEachesInInventoryService(
        correlationId,
        containerItem2.getTrackingId(),
        Integer.valueOf(5),
        httpHeaders,
        containerItem2,
        Integer.valueOf(containerItem1.getQuantity()));

    verify(restUtils, times(1)).put(anyString(), any(HttpHeaders.class), anyMap(), anyString());
  }

  @Test
  public void
      testprocessCreateChildContainersWhenParentContainerisNotPresent_withMiscContainerItemInfo()
          throws ReceivingException, NoSuchMethodException, InvocationTargetException,
              IllegalAccessException {

    instructionRequest.setDeliveryDocumentLines(null);
    instructionRequest.setDeliveryDocumentLines(contentsList1);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setParentTrackingId(instruction.getSsccNumber());
    containerDetails1.setTrackingId("CASE_CONTAINER_TRACKINGID");

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setParentTrackingId(containerDetails1.getTrackingId());
    containerDetails2.setTrackingId("CONTAINER_TRACKINGID");
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    Class[] paramArguments = new Class[5];
    paramArguments[0] = Instruction.class;
    paramArguments[1] = ContainerDetails.class;
    paramArguments[2] = Boolean.class;
    paramArguments[3] = Boolean.class;
    paramArguments[4] = UpdateInstructionRequest.class;
    Method privateMethodParentContainer =
        ContainerService.class.getDeclaredMethod("constructContainer", paramArguments);
    privateMethodParentContainer.setAccessible(true);
    Container container =
        (Container)
            privateMethodParentContainer.invoke(
                containerService, instruction, containerDetails1, true, false, instructionRequest);
    containerService.processCreateChildContainers(
        instruction, instructionRequest, containerDetails1, containerDetails2);

    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(containerRepository, Mockito.times(2)).saveAll(any(List.class));
    verify(containerItemRepository, Mockito.times(2)).saveAll(any(List.class));
  }

  @Test
  public void testPublishContainer_sendAuditInfoGDC() {
    container.setChildContainers(ContainerChildContainers);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    ArgumentCaptor<ContainerDTO> containerDTOArgumentCaptor_GDC =
        ArgumentCaptor.forClass(ContainerDTO.class);
    containerService.publishContainer(container, new HashMap<>(), false);
    verify(JMSReceiptPublisher, times(1)).publish(containerDTOArgumentCaptor_GDC.capture(), any());

    assertEquals(
        containerDTOArgumentCaptor_GDC.getValue().getTags().get(0).getAction(), CONTAINER_SET);
    assertEquals(
        containerDTOArgumentCaptor_GDC.getValue().getTags().get(0).getTag(),
        CONTAINER_TO_BE_AUDITED);
  }

  @Test
  public void testPublishContainer_dontSendAuditInfoGDC() {
    container.setChildContainers(ContainerChildContainers);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(false);
    ArgumentCaptor<ContainerDTO> containerDTOArgumentCaptor_GDC =
        ArgumentCaptor.forClass(ContainerDTO.class);
    containerService.publishContainer(container, new HashMap<>(), false);
    verify(JMSReceiptPublisher, times(1)).publish(containerDTOArgumentCaptor_GDC.capture(), any());

    assertEquals(containerDTOArgumentCaptor_GDC.getValue().getTags(), null);
  }

  @Test
  public void testPostReceiptsReceiveAsCorrection_sendAuditInfoGDC() {
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    Map<String, String> destinationMap = new HashMap<>();
    destinationMap.put(ReceivingConstants.SLOT, "PL0012");
    destinationMap.put(ReceivingConstants.SLOT_TYPE, "Prime");
    container.setDestination(destinationMap);
    containerService.postReceiptsReceiveAsCorrection(container, httpHeaders);

    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> containerDTOArgumentCaptor_reqest_GDC =
        ArgumentCaptor.forClass(String.class);
    verify(asyncPersister, times(1))
        .persistAsyncHttp(
            any(),
            any(),
            containerDTOArgumentCaptor_reqest_GDC.capture(),
            headerCaptor.capture(),
            any());
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    Gson gsonDateFormatBuilder = new GsonBuilder().setDateFormat(INVENTORY_DATE_FORMAT).create();
    Map<String, Object> containerPayloadMap =
        gsonDateFormatBuilder.fromJson(
            containerDTOArgumentCaptor_reqest_GDC.getValue(), HashMap.class);
    ContainerDTO containerDTO =
        gson.fromJson(
            String.valueOf(containerPayloadMap.get(INVENTORY_RECEIPT)), ContainerDTO.class);
    assertEquals(containerDTO.getTags().get(0).getAction(), CONTAINER_SET);
    assertEquals(containerDTO.getTags().get(0).getTag(), CONTAINER_TO_BE_AUDITED);
    assertEquals(containerDTO.getTags().get(1).getTag(), PUTAWAY_TO_PRIME);
  }

  @Test
  public void testPostReceiptsReceiveAsCorrection_doNotSendPrimePutAwayToInventory() {
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    Map<String, String> destinationMap = new HashMap<>();
    destinationMap.put(ReceivingConstants.SLOT, "PL0012");
    destinationMap.put(ReceivingConstants.SLOT_TYPE, "Reserve");
    container.setDestination(destinationMap);
    containerService.postReceiptsReceiveAsCorrection(container, httpHeaders);

    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> containerDTOArgumentCaptor_reqest_GDC =
        ArgumentCaptor.forClass(String.class);
    verify(asyncPersister, times(1))
        .persistAsyncHttp(
            any(),
            any(),
            containerDTOArgumentCaptor_reqest_GDC.capture(),
            headerCaptor.capture(),
            any());
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    Gson gsonDateFormatBuilder = new GsonBuilder().setDateFormat(INVENTORY_DATE_FORMAT).create();
    Map<String, Object> containerPayloadMap =
        gsonDateFormatBuilder.fromJson(
            containerDTOArgumentCaptor_reqest_GDC.getValue(), HashMap.class);
    ContainerDTO containerDTO =
        gson.fromJson(
            String.valueOf(containerPayloadMap.get(INVENTORY_RECEIPT)), ContainerDTO.class);
    assertEquals(containerDTO.getTags().get(0).getAction(), CONTAINER_SET);
    assertEquals(containerDTO.getTags().get(0).getTag(), CONTAINER_TO_BE_AUDITED);
  }

  @Test
  public void test_Container_isAtlasConvertedItem() throws ReceivingException {

    when(containerRepository.findByTrackingId(anyString()))
        .thenReturn(mockResponseForGetParentContainer(null, "12345", 36));
    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(mockResponseForGetContainerIncludesChildren("12345"));
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(containerItemRepository.findByTrackingIdIn(anyList()))
        .thenReturn(mockResponseForGetContainerItems(Arrays.asList("123", "456"), 36));

    //    boolean isAtlasConvertedItem = containerService.isAtlasConvertedItem("12345");
  }

  @Test
  public void testGetReceivedHistoryByDeliveryNumber_SendContainer() throws ReceivingException {
    when(containerPersisterService.findReceivedHistoryByDeliveryNumber(anyLong()))
        .thenReturn(
            Arrays.asList(
                new PalletHistory(
                    "test", 1L, 1, new Date(), new Date(), MockContainer.getDestinationInfo())));
    List<PalletHistory> containerList = containerService.getReceivedHistoryByDeliveryNumber(1L);
    verify(containerRepository, Mockito.times(1)).findByOnlyDeliveryNumber(1L);
    assertNotNull(containerList);
  }

  @Test
  public void testGetReceivedHistoryByDeliveryNumberWithPO_SendContainer()
      throws ReceivingException {
    when(containerPersisterService.findReceivedHistoryByDeliveryNumberWithPO(
            anyLong(), anyString(), anyInt()))
        .thenReturn(
            Arrays.asList(
                new PalletHistory(
                    "test", 1L, 1, new Date(), new Date(), MockContainer.getDestinationInfo())));
    List<PalletHistory> containerList =
        containerService.getReceivedHistoryByDeliveryNumberWithPO(1L, "test", 1);
    assertNotNull(containerList);
    verify(containerRepository, Mockito.times(1)).findByDeliveryNumberWithPO(1L, "test", 1);
  }

  @Ignore
  public void testUpdateContainerData_validInventoryMoveContainerStatus()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(true);
    Container mockContainer = MockContainer.getSSTKContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainer
        .getContainerItems()
        .stream()
        .findFirst()
        .get()
        .setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(mockContainer.getContainerItems());
    when(moveRestApiClient.getMoveContainerByContainerId(anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.fromJson(mockResponse, JsonArray.class));
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), 6085, 0));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(any(), eq("invalidInventoryStatusForCorrection"), any()))
        .thenReturn("ALLOCATED,PICKED,LOADED");
    when(configUtils.getCcmValue(any(), eq("invalidMoveStatusForCorrection"), any()))
        .thenReturn("WORKING");
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
    assertNotNull(container);
    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getInventoryQuantity().intValue(), 12);
  }

  @Ignore
  public void testUpdateContainerData_inValidInventoryContainerStatus()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(true);
    Container mockContainer = MockContainer.getSSTKContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainer
        .getContainerItems()
        .stream()
        .findFirst()
        .get()
        .setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(mockContainer.getContainerItems());
    when(moveRestApiClient.getMoveContainerByContainerId(anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.fromJson(mockResponse, JsonArray.class));
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(new InventoryContainerDetails(12, InventoryStatus.PICKED.name(), 6085, 0));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(any(), eq("invalidInventoryStatusForCorrection"), any()))
        .thenReturn("ALLOCATED,PICKED,LOADED");
    when(configUtils.getCcmValue(any(), eq("invalidMoveStatusForCorrection"), any()))
        .thenReturn("WORKING");
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      assertNotNull(container);
      fail("Method will fail due to invalid inventory status");
    } catch (ReceivingBadDataException re) {
      assertEquals(
          re.getMessage(),
          ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
    }
  }

  @Ignore
  public void testUpdateContainerData_inValidMoveContainerStatus()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(true);
    Container mockContainer = MockContainer.getSSTKContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainer
        .getContainerItems()
        .stream()
        .findFirst()
        .get()
        .setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(mockContainer.getContainerItems());
    JsonArray moveContainersList = gson.fromJson(mockResponse, JsonArray.class);
    moveContainersList.get(0).getAsJsonObject().addProperty(MOVE_STATUS, "WORKING");
    when(moveRestApiClient.getMoveContainerByContainerId(anyString(), any(HttpHeaders.class)))
        .thenReturn(moveContainersList);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), 6085, 0));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(any(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), any()))
        .thenReturn("ALLOCATED,PICKED,LOADED");
    when(configUtils.getCcmValue(any(), eq(INVALID_MOVE_STATUS_CORRECTION), any()))
        .thenReturn("WORKING");
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      assertNotNull(container);
      fail("Method will fail due to invalid move status");
    } catch (ReceivingBadDataException re) {
      assertEquals(
          re.getMessage(),
          ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
    }
  }

  @Ignore
  public void testUpdateContainerData_skipValidateInventoryAndMoveStatusForCorrection()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(false);

    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), 6085, 0));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));

    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

    Container container =
        containerService.updateContainerData(
            MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
    assertNotNull(container);
    assertEquals(container.getContainerItems().get(0).getActualTi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getActualHi().intValue(), 4);
    assertEquals(container.getContainerItems().get(0).getInventoryQuantity().intValue(), 12);
  }

  @Ignore
  public void testUpdateContainerData_emptyMoveContainerResponse()
      throws ReceivingException, IOException, ItemConfigRestApiClientException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setTempPalletTi(4);
    when(configUtils.getConfiguredFeatureFlag(any(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(true);
    Container mockContainer = MockContainer.getSSTKContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainer
        .getContainerItems()
        .stream()
        .findFirst()
        .get()
        .setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(mockContainer.getContainerItems());
    when(moveRestApiClient.getMoveContainerByContainerId(anyString(), any(HttpHeaders.class)))
        .thenReturn(new JsonArray());
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(inventoryService.getInventoryContainerDetails(anyString(), any(HttpHeaders.class)))
        .thenReturn(
            new InventoryContainerDetails(12, InventoryStatus.WORK_IN_PROGRESS.name(), 6085, 0));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR));
    when(configUtils.getCcmValue(any(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), any()))
        .thenReturn("ALLOCATED,PICKED,LOADED");
    when(configUtils.getCcmValue(any(), eq(INVALID_MOVE_STATUS_CORRECTION), any()))
        .thenReturn("WORKING");
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();

    doReturn(getItemConfigDetails_Converted())
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));

    try {
      Container container =
          containerService.updateContainerData(
              MockContainer.getSSTKContainer(), Boolean.TRUE, mockHeaders);
      assertNotNull(container);
      fail("Method will fail due to invalid move status");
    } catch (ReceivingBadDataException re) {
      assertEquals(
          re.getMessage(),
          ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG);
    }
  }

  private static List<ItemConfigDetails> getItemConfigDetails_Converted() {
    final List<ItemConfigDetails> itemConfigList =
        Collections.singletonList(
            ItemConfigDetails.builder().createdDateTime(null).desc("desc").item("100000").build());
    return itemConfigList;
  }

  @Test
  public void testPublishContainer_isReceivingToOSSSubCenter() {
    Container container2 = SerializationUtils.clone(container);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(TO_SUBCENTER, "5");
    containerItemMiscInfo.put(FROM_SUBCENTER, "2");
    containerItemMiscInfo.put(TO_ORG_UNIT_ID, "5");
    containerItemMiscInfo.put(FROM_ORG_UNIT_ID, "2");
    containerItemMiscInfo.put(PO_TYPE, "28");
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setContainerItemMiscInfo(containerItemMiscInfo);
    List<ContainerItem> containerItemList1 = new ArrayList<>();
    containerItemList1.add(containerItem2);
    container2.setContainerItems(containerItemList1);

    when(configUtils.getOrgUnitId()).thenReturn("5");
    when(configUtils.getOrgUnitId()).thenReturn("5");
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn("28,29");
    ArgumentCaptor<ContainerDTO> containerDTOArgumentCaptor_GDC =
        ArgumentCaptor.forClass(ContainerDTO.class);
    containerService.publishContainer(container2, new HashMap<>(), false);
    verify(JMSReceiptPublisher, times(1)).publish(containerDTOArgumentCaptor_GDC.capture(), any());

    assert containerDTOArgumentCaptor_GDC.getValue().getOrgUnitIdInfo().getDestinationId() == 5;
    assert containerDTOArgumentCaptor_GDC.getValue().getOrgUnitIdInfo().getSourceId() == 2;
  }

  @Test
  public void testPublishContainer_isNotReceivingToOSSSubCenter() {
    Container container2 = SerializationUtils.clone(container);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setContainerItemMiscInfo(new HashMap<>());
    List<ContainerItem> containerItemList1 = new ArrayList<>();
    containerItemList1.add(containerItem2);
    container2.setContainerItems(containerItemList1);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(false);
    ArgumentCaptor<ContainerDTO> containerDTOArgumentCaptor_GDC =
        ArgumentCaptor.forClass(ContainerDTO.class);
    containerService.publishContainer(container2, new HashMap<>(), false);
    verify(JMSReceiptPublisher, times(1)).publish(containerDTOArgumentCaptor_GDC.capture(), any());

    assert null == containerDTOArgumentCaptor_GDC.getValue().getOrgUnitIdInfo();
  }

  @Test
  public void
      testprocessCreateChildContainersWhenParentContainer_isTransferFormOSSForGDC_calledforConvertedItemOneAtlasOnly()
          throws ReceivingException, NoSuchMethodException, InvocationTargetException,
              IllegalAccessException {
    // instructionRequest2 --> POCON
    // instructionRequest --> CROSSU
    instructionRequest.setDeliveryDocumentLines(contentsList);
    Instruction instructionGDC = instruction2;
    instructionGDC.setDeliveryDocument(MockInstruction.getMockDeliveryDocumentConvertedItemGDC());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setParentTrackingId(instruction.getSsccNumber());
    containerDetails1.setTrackingId("CASE_CONTAINER_TRACKINGID");

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setParentTrackingId(containerDetails1.getTrackingId());
    containerDetails2.setTrackingId("CONTAINER_TRACKINGID");
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            32987, ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    when(configUtils.getOrgUnitId()).thenReturn("5");
    Class[] paramArguments = new Class[5];
    paramArguments[0] = Instruction.class;
    paramArguments[1] = ContainerDetails.class;
    paramArguments[2] = Boolean.class;
    paramArguments[3] = Boolean.class;
    paramArguments[4] = UpdateInstructionRequest.class;
    Method privateMethodParentContainer =
        ContainerService.class.getDeclaredMethod("constructContainer", paramArguments);
    privateMethodParentContainer.setAccessible(true);
    Container container =
        (Container)
            privateMethodParentContainer.invoke(
                containerService,
                instructionGDC,
                containerDetails1,
                true,
                false,
                instructionRequest);
    containerService.processCreateChildContainers(
        instructionGDC, instructionRequest, containerDetails1, containerDetails2);

    assert container.getContainerItems().get(0).getContainerItemMiscInfo().containsKey(PO_TYPE);
    assert container
        .getContainerItems()
        .get(0)
        .getContainerItemMiscInfo()
        .containsKey(FROM_SUBCENTER);
    assert container
        .getContainerItems()
        .get(0)
        .getContainerItemMiscInfo()
        .containsKey(TO_SUBCENTER);

    assert "28"
        .equalsIgnoreCase(
            container.getContainerItems().get(0).getContainerItemMiscInfo().get(PO_TYPE));
    assert "2"
        .equalsIgnoreCase(
            container.getContainerItems().get(0).getContainerItemMiscInfo().get(FROM_SUBCENTER));
    assert "5"
        .equalsIgnoreCase(
            container.getContainerItems().get(0).getContainerItemMiscInfo().get(TO_SUBCENTER));
  }

  @Test
  public void testPostReceiptsReceiveAsCorrection_fromOSS() {
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(TO_SUBCENTER, "5");
    containerItemMiscInfo.put(FROM_SUBCENTER, "2");
    containerItemMiscInfo.put(TO_ORG_UNIT_ID, "5");
    containerItemMiscInfo.put(FROM_ORG_UNIT_ID, "2");
    containerItemMiscInfo.put(PO_TYPE, "28");
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setContainerItemMiscInfo(containerItemMiscInfo);
    List<ContainerItem> containerItemList1 = new ArrayList<>();
    containerItemList1.add(containerItem2);
    Container container2 = new Container();
    container2 = SerializationUtils.clone(container);
    container2.setContainerItems(containerItemList1);

    when(configUtils.getOrgUnitId()).thenReturn("5");
    when(configUtils.getOrgUnitId()).thenReturn("5");

    ArgumentCaptor<String> containerDTOArgumentCaptor_reqest_GDC =
        ArgumentCaptor.forClass(String.class);
    containerService.postReceiptsReceiveAsCorrection(container2, httpHeaders);

    verify(asyncPersister, times(1))
        .persistAsyncHttp(
            any(), any(), containerDTOArgumentCaptor_reqest_GDC.capture(), any(), any());
    Gson gsonDateFormatBuilder = new GsonBuilder().setDateFormat(INVENTORY_DATE_FORMAT).create();
    Map<String, Object> containerPayloadMap =
        gsonDateFormatBuilder.fromJson(
            containerDTOArgumentCaptor_reqest_GDC.getValue(), HashMap.class);
    ContainerDTO containerDTO =
        gson.fromJson(
            String.valueOf(containerPayloadMap.get(INVENTORY_RECEIPT)), ContainerDTO.class);
    assert containerDTO.getOrgUnitIdInfo().getSourceId() == 2;
    assert containerDTO.getOrgUnitIdInfo().getDestinationId() == 5;
  }

  @Test
  public void testSetPackInformation() {
    Instruction instruction = MockInstruction.getInstruction();
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

    instructionRequest.getDeliveryDocuments().get(0).setAdditionalInfo(new PoAdditionalInfo());
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getAdditionalInfo()
        .setPackId("086001043710000001");
    instructionRequest.getDeliveryDocuments().get(0).getAdditionalInfo().setIsAuditRequired(false);

    Container container = new Container();
    containerService.setPackInformation(
        instructionRequest.getDeliveryDocuments().get(0), container);

    assertEquals(container.getSsccNumber(), "086001043710000001");
    assertFalse(container.getIsAuditRequired());
  }

  @Test
  public void testSetPackInformationWithNullFields() {
    Instruction instruction = MockInstruction.getInstruction();
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

    instructionRequest.getDeliveryDocuments().get(0).setAdditionalInfo(new PoAdditionalInfo());
    Container container = new Container();
    containerService.setPackInformation(
        instructionRequest.getDeliveryDocuments().get(0), container);
  }

  @Test
  public void testProcessCreateContainer() throws Exception {
    when(appConfig.getPackagedAsUom()).thenReturn(VNPK);
    when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
    when(containerRepository.existsByTrackingId(anyString())).thenReturn(false);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false))
        .thenReturn(true);

    containerService.processCreateContainers(
        MockContainer.getInstruction(), MockContainer.getUpdateInstructionRequest(), httpHeaders);

    verify(containerRepository, times(1)).save(any(Container.class));
  }

  @Test
  public void test_addSubcenterInfo_receiveIntoOSS() throws Exception {
    ContainerDTO containerDTO = new ContainerDTO();

    containerDTO.setOrgUnitId(3);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(FLOW_DESCRIPTOR, FLOW_RECEIVE_INTO_OSS);
    containerDTO.setContainerMiscInfo(containerMiscInfo);
    containerService.addSubcenterInfo(containerDTO);
    final OrgUnitIdInfo orgUnitIdInfo = containerDTO.getOrgUnitIdInfo();
    assertNotNull(orgUnitIdInfo);
    assertEquals(orgUnitIdInfo.getSourceId().intValue(), 3);
    assertEquals(orgUnitIdInfo.getDestinationId().intValue(), 3);
  }

  @Test
  public void test_addOrgUnitInfo_ContainerItemMisc() throws Exception {
    ContainerDTO containerDTO = new ContainerDTO();
    containerDTO.setOrgUnitId(3);
    Map<String, String> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(FLOW_DESCRIPTOR, FLOW_RECEIVE_INTO_OSS);
    containerMiscInfo.put(FROM_ORG_UNIT_ID, "2");
    when(configUtils.getOrgUnitId()).thenReturn("2");
    ContainerItem containerItem = new ContainerItem();
    containerItem.setContainerItemMiscInfo(containerMiscInfo);
    containerDTO.setContainerItems(Arrays.asList(containerItem));
    containerService.addSubcenterInfo(containerDTO);
    final OrgUnitIdInfo orgUnitIdInfo = containerDTO.getOrgUnitIdInfo();
    assertNotNull(orgUnitIdInfo);
    assertEquals(orgUnitIdInfo.getSourceId().intValue(), 2);
    assertEquals(orgUnitIdInfo.getDestinationId().intValue(), 2);
  }

  @Test
  public void blockReceivingIsDCPOFinalized_throwException() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(true);
    when(dcFinRestApiClient.isPoFinalizedInDcFin(any(), any())).thenReturn(true);
    ContainerItem containerItem2 = new ContainerItem();
    Map<String, String> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("isReceiveFromOSS", "true");
    containerItem2.setContainerItemMiscInfo(containerMiscInfo);

    Receipt receipt = new Receipt();
    receipt.setFinalizeTs(new Date());
    receipt.setFinalizedUserId("test");

    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_REQUEST);
      assertEquals(e.getMessage(), ReceivingException.OSS_TRANSFER_PO_FINALIZED_CORRECTION_ERROR);
    }
  }

  @Test
  public void blockReceivingIsReceiptPOFinalized_throwException() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(false);
    when(dcFinRestApiClient.isPoFinalizedInDcFin(anyString(), anyString())).thenReturn(true);

    ContainerItem containerItem2 = new ContainerItem();
    Map<String, String> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("isReceiveFromOSS", "true");
    containerItem2.setContainerItemMiscInfo(containerMiscInfo);

    Receipt receipt = new Receipt();
    receipt.setFinalizeTs(new Date());
    receipt.setFinalizedUserId("test");

    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_REQUEST);
      assertEquals(e.getMessage(), ReceivingException.OSS_TRANSFER_PO_FINALIZED_CORRECTION_ERROR);
    }
  }

  @Test
  public void blockReceivingIsReceiptPO_NOT_Finalized_masterReceipt_Null()
      throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(false);
    ContainerItem containerItem2 = new ContainerItem();
    Receipt receipt = null;
    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
      fail("should be ReceivingException instead got success call");
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), BAD_REQUEST);
      final ErrorResponse errorResponse = re.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorHeader(), ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorMessage(), "Please confirm PO 12343 before correcting this LPN.");
    } catch (Exception e) {
      fail("should be ReceivingException instead got error=" + e.getMessage());
    }
  }

  @Test
  public void blockReceivingIsReceiptPO_NOT_Finalized_UserId_Null() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(false);

    ContainerItem containerItem2 = new ContainerItem();

    Receipt receipt = new Receipt();
    receipt.setFinalizeTs(new Date());

    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
      fail("should be ReceivingException instead got success call");
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), BAD_REQUEST);
      final ErrorResponse errorResponse = re.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorHeader(), ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorMessage(), "Please confirm PO 12343 before correcting this LPN.");
    } catch (Exception e) {
      fail("should be ReceivingException instead got error=" + e.getMessage());
    }
  }

  @Test
  public void blockReceivingIsReceiptPO_NOT_Finalized_FinalizeTs_null() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(false);

    ContainerItem containerItem2 = new ContainerItem();

    Receipt receipt = new Receipt();
    receipt.setFinalizedUserId("test");

    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
      fail("should be ReceivingException instead got success call");
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), BAD_REQUEST);
      final ErrorResponse errorResponse = re.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorHeader(), ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      assertEquals(
          errorResponse.getErrorMessage(), "Please confirm PO 12343 before correcting this LPN.");
    } catch (Exception e) {
      fail("should be ReceivingException instead got error=" + e.getMessage());
    }
  }

  @Test
  public void isDCFINAndReceiptPONotFinalized_throwException() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(true);
    when(dcFinRestApiClient.isPoFinalizedInDcFin(anyString(), anyString())).thenReturn(false);

    ContainerItem containerItem2 = new ContainerItem();
    Map<String, String> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("isReceiveFromOSS", "true");
    containerItem2.setContainerItemMiscInfo(containerMiscInfo);

    Receipt receipt = new Receipt();
    receipt.setFinalizeTs(new Date());
    receipt.setFinalizedUserId("test");

    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getMessage(), "Please try once DcFin confirms the PO 12343 closure.");
    }

    reset(configUtils);
    when(configUtils.getConfiguredFeatureFlag(
            "32987", ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED, false))
        .thenReturn(false);
    Receipt receipt2 = new Receipt();
    receipt.setFinalizeTs(new Date());
    try {
      containerService.validatePOClose(containerItem2, 1234L, "12343", receipt2);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getMessage(), "Please confirm PO 12343 before correcting this LPN.");
    }
  }

  @Test
  public void testConstructContainer() throws ReceivingException {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOrgUnitId(1);
    when(configUtils.getConfiguredFeatureFlag(
            "32987", IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM, true))
        .thenReturn(false);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, null);
    Container container =
        containerService.constructContainer(
            setInstructionRepository(new Instruction()),
            containerDetails,
            false,
            false,
            setUpdateInstructionRequest(new UpdateInstructionRequest()));
    assertEquals(container.getLocation(), "3");
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, "nonValid");
    container =
        containerService.constructContainer(
            setInstructionRepository(new Instruction()),
            containerDetails,
            false,
            false,
            setUpdateInstructionRequest(new UpdateInstructionRequest()));
    assertEquals(container.getLocation(), "3");
    assertEquals(container.getActivityName(), "POCON");
  }

  @Test
  public void testConstructContainerIsReceivingInstructsPutAwayMoveToMMTrue()
      throws ReceivingException {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOrgUnitId(1);
    when(configUtils.getConfiguredFeatureFlag(
            "32987", IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM, true))
        .thenReturn(true);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, "nonValid");
    Container container =
        containerService.constructContainer(
            setInstructionRepository(new Instruction()),
            containerDetails,
            false,
            false,
            setUpdateInstructionRequest(new UpdateInstructionRequest()));
    assertEquals(container.getLocation(), "3");
    assertEquals(container.getActivityName(), "POCON");
  }

  @Test
  public void testConstructContainerList() throws ReceivingException {
    // setting child container null value for test
    instruction.setChildContainers(null);

    Container container2 = containerService.constructContainerList(instruction, instructionRequest);

    verify(containerRepository, Mockito.times(0)).save(any(Container.class));
    assert container2.getTrackingId() == instruction.getContainer().getTrackingId();
  }

  @Test
  public void testContainerListComplete() {
    Container containerOne = new Container();
    containerOne.setContainerItems(Arrays.asList(new ContainerItem()));
    Container containerTwo = new Container();
    containerTwo.setContainerItems(Arrays.asList(new ContainerItem()));
    List<Container> containerList = new ArrayList<>();
    containerList.add(containerOne);
    containerList.add(containerTwo);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    containerService.containerListComplete(containerList, "testUser");

    verify(containerRepository, Mockito.times(1)).saveAll(any(List.class));
  }

  public Instruction setInstructionRepository(Instruction instruction) {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setCtrShippable(false);
    containerDetails.setCtrType("PALLET");
    containerDetails.setCtrReusable(false);
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setTrackingId("A08852000020029522");
    instruction.setId(3L);
    instruction.setDeliveryNumber(42229032L);
    instruction.setActivityName("POCON");
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setInstructionCode("Build  Container");
    instruction.setInstructionMsg("Build  the Container");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setPoDcNumber("32988");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setGtin(null);
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(null);
    instruction.setProjectedReceiveQty(3);
    instruction.setProviderId("DA-SSTK");
    instruction.setContainer(containerDetails);

    String deliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"2963320149\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"12344\",\n"
            + "    \"deptNumber\": \"14\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"33\",\n"
            + "    \"poDCNumber\": \"32988\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"itemUPC\": \"00076501380104\",\n"
            + "            \"caseUPC\": \"00076501380104\",\n"
            + "            \"purchaseReferenceNumber\": \"2963320149\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 8.22,\n"
            + "            \"vendorPackCost\": 6.6,\n"
            + "            \"vnpkQty\": 2,\n"
            + "            \"whpkQty\": 2,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"expectedQty\": 400,\n"
            + "            \"overageQtyLimit\": 11,\n"
            + "            \"itemNbr\": 444444441,\n"
            + "            \"purchaseRefType\": \"POCON\",\n"
            + "            \"palletTi\": 7,\n"
            + "            \"palletHi\": 8,\n"
            + "            \"vnpkWgtQty\": 14.84,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.432,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"\",\n"
            + "            \"size\": \"\",\n"
            + "            \"itemDescription1\": \"70QT XTREME BLUE\",\n"
            + "            \"itemDescription2\": \"WH TO ASM\",\n"
            + "            \"isConveyable\": true\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 106,\n"
            + "    \"weight\": 12.0,\n"
            + "    \"weightUOM\": \"LB\",\n"
            + "    \"cubeQty\": 23.5,\n"
            + "    \"cubeUOM\": \"CF\",\n"
            + "    \"freightTermCode\": \"COLL\"\n"
            + "}";

    instruction.setDeliveryDocument(deliveryDocument);
    return instruction;
  }

  private UpdateInstructionRequest setUpdateInstructionRequest(
      UpdateInstructionRequest updateInstructionRequest) {
    List<DocumentLine> documentLines = new ArrayList<>();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseRefType("REF");
    documentLine.setQuantity(1);
    documentLine.setQuantityUOM("EA");
    documentLine.setEvent("testEvent");
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryNumber(42229032L);
    updateInstructionRequest.setDoorNumber("3");
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);
    return updateInstructionRequest;
  }

  @Test
  public void testGetContainerMiscInfoForWFS() {
    InstructionRequest instructionRequest = new InstructionRequest();
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW, Boolean.TRUE);
    instructionRequest.setAdditionalParams(parameters);

    Map<String, Object> containerMiscInfo =
        containerService.getContainerMiscInfoForWFS(instructionRequest, new Container());

    assertEquals(containerMiscInfo.get(ReceivingConstants.IS_RE_RECEIVING_CONTAINER), Boolean.TRUE);
  }

  @Test
  public void testGetContainerMiscInfoForWFSNoData() {
    assertEquals(
        containerService
            .getContainerMiscInfoForWFS(new InstructionRequest(), new Container())
            .size(),
        0);
  }

  @Test
  public void testUpdateContainerInventoryStatus() throws Exception {
    String trackingId = "8771291299812";
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    Container updatedContainer =
        containerService.updateContainerInventoryStatus(trackingId, InventoryStatus.PICKED.name());
    assertNotNull(updatedContainer);
    assertNotNull(updatedContainer.getInventoryStatus());
    assertTrue(
        updatedContainer.getInventoryStatus().equalsIgnoreCase(InventoryStatus.PICKED.name()));
    verify(containerRepository, times(1)).findByTrackingId(trackingId);
    verify(containerRepository, times(1)).save(any(Container.class));
  }
}
