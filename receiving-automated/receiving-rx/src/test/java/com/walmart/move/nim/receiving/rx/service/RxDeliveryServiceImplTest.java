package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.AsnToDeliveryDocumentsCustomMapper;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitRequestMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitSerialRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxDeliveryLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.TwoDScanAsnDeliveryDocumentMapper;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxDeliveryServiceImplTest {

  @Spy private RxDeliveryLabelBuilder rxDeliveryLabelBuilder = new RxDeliveryLabelBuilder();
  @InjectMocks @Spy private RxDeliveryServiceImpl rxDeliveryService;
  @Mock private TwoDScanAsnDeliveryDocumentMapper twoDScanAsnDeliveryDocumentMapper;
  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;
  @Mock private ItemCatalogRepository itemCatalogRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryService deliveryService;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        rxDeliveryService, RxDeliveryServiceImpl.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        rxDeliveryService, DeliveryService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(rxDeliveryService, "gson", gson);
    ReflectionTestUtils.setField(
        rxDeliveryService,
        RxDeliveryServiceImpl.class,
        "itemCatalogRepository",
        itemCatalogRepository,
        ItemCatalogRepository.class);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void afterMethod() {
    reset(twoDScanAsnDeliveryDocumentMapper);
    reset(restConnector);
    reset(appConfig);
    reset(rxDeliveryService);
  }

  @Test
  public void testgetContainerSsccDetails() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(twoDScanAsnDeliveryDocumentMapper)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32897",
            ReceivingConstants.ASN_CUSTOM_MAPPER,
            AsnToDeliveryDocumentsCustomMapper.class);
    doReturn(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(), anyString(), any(HttpHeaders.class));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    SsccScanResponse gdmPackResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
    doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Optional<List<DeliveryDocument>> deliveryDoc =
        rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
    assertTrue(deliveryDoc.isPresent());
    assertTrue(CollectionUtils.isNotEmpty(deliveryDoc.get()));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testgetContainerSsccDetails_GdmNotFound() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(), anyString(), any(HttpHeaders.class));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testgetContainerSsccDetails_GdmUnavailable() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(), anyString(), any(HttpHeaders.class));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doThrow(new ResourceAccessException("Service unavailable"))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testgetContainerSsccDetails_NoResponse() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(), anyString(), any(HttpHeaders.class));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetGDMData() throws ReceivingException {
    rxDeliveryService.getGDMData(new DeliveryUpdateMessage());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testgetContainerSsccDetailsMultiSku() throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMultiSkuResponse.json").getFile();
    SsccScanResponse multiSkuResponse =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(resource.toPath())), SsccScanResponse.class);
    doThrow(new ReceivingInternalException("MultiSKU", "MultiSKU"))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(SsccScanResponse.class), anyString(), any(HttpHeaders.class));
    doReturn(new ResponseEntity<>(multiSkuResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
  }

  @Test
  public void testFindDeliveryDocument() throws ReceivingException, IOException {
    File resource = new ClassPathResource("gdm_Upc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    rxDeliveryService.findDeliveryDocument(9898L, "123456789", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testgetContainerSsccDetails_204_Response() throws Exception {
    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)))
        .when(twoDScanAsnDeliveryDocumentMapper)
        .mapGdmResponse(any(), anyString(), any(HttpHeaders.class));
    File gdmPackResponseFile = new ClassPathResource("GdmPackResponse.json").getFile();
    String gdmPackResponse = new String(Files.readAllBytes(gdmPackResponseFile.toPath()));
    doReturn(new ResponseEntity<>(null, HttpStatus.NO_CONTENT))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Optional<List<DeliveryDocument>> containerSsccDetailsResponse =
        rxDeliveryService.getContainerSsccDetails("9898", "123456789", new HttpHeaders());
    assertFalse(containerSsccDetailsResponse.isPresent());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_findDeliveryDocumentBySSCCWithShipmentLinking_exception()
      throws ReceivingException {

    doReturn(Optional.empty())
        .when(rxDeliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doReturn(mockShipment)
        .when(rxDeliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(rxDeliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentsResponse =
        rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentsResponse));
    verify(rxDeliveryService, times(2))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_searchShipment() throws Exception {

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    Optional<List<DeliveryDocument>> deliveryDocListOptional =
        Optional.of(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)));

    doReturn(Optional.empty(), deliveryDocListOptional)
        .when(rxDeliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doReturn(mockShipment)
        .when(rxDeliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(rxDeliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentsResponse =
        rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentsResponse));
    verify(rxDeliveryService, times(2))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_findDeliveryDocumentBySSCCWithLatestShipmentLinkingSearchLatestShipmentAndLink()
      throws Exception {

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    Optional<List<DeliveryDocument>> deliveryDocListOptional =
        Optional.of(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)));

    doReturn(deliveryDocListOptional)
        .when(rxDeliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doReturn(mockShipment)
        .when(rxDeliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(rxDeliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    Optional<List<DeliveryDocument>> deliveryDocumentsResponse =
        rxDeliveryService.findDeliveryDocumentBySSCCWithLatestShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentsResponse.get()));
    verify(rxDeliveryService, times(1))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_findDeliveryDocumentBySSCCWithLatestShipmentLinking_NoNewShipmentAvailable()
      throws Exception {

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String ssccScanMappedResponse = new String(Files.readAllBytes(resource.toPath()));
    Optional<List<DeliveryDocument>> deliveryDocListOptional =
        Optional.of(Arrays.asList(gson.fromJson(ssccScanMappedResponse, DeliveryDocument[].class)));

    doReturn(deliveryDocListOptional)
        .when(rxDeliveryService)
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));

    Shipment mockShipment = new Shipment();
    mockShipment.setShipmentNumber("MOCK_SHIPMENT_NUMBER");
    mockShipment.setDocumentId("MOCK_DOCUMENT_ID");
    doThrow(new ReceivingBadDataException("", ""))
        .when(rxDeliveryService)
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));

    doReturn("MOCK_LINK_DELIVERY_RESPONSE")
        .when(rxDeliveryService)
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    Optional<List<DeliveryDocument>> deliveryDocumentsResponse =
        rxDeliveryService.findDeliveryDocumentBySSCCWithLatestShipmentLinking(
            "9898", "123456789", MockRxHttpHeaders.getHeaders());

    assertFalse(deliveryDocumentsResponse.isPresent());
    verify(rxDeliveryService, times(0))
        .getContainerSsccDetails(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .searchShipment(anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(0))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testSearchShipment() throws ReceivingException, IOException {
    File resource = new ClassPathResource("gdm_Upc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    rxDeliveryService.findDeliveryDocument(9898L, "123456789", MockHttpHeaders.getHeaders());
  }

  @Test
  public void test_linkDeliveryWithShipment_rest_invocation() throws Exception {

    doReturn(new ResponseEntity<>("MOCK RESPONSE", HttpStatus.OK))
        .when(restConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    rxDeliveryService.linkDeliveryWithShipment(
        "9898", "mockShipmentNumber", "mockShipmentDocumentId", MockRxHttpHeaders.getHeaders());

    verify(restConnector, times(1)).put(anyString(), anyString(), any(Map.class), any(Class.class));
  }

  @Test
  public void test_getContainerSsccDetails_inline_partial_error() throws Exception {

    try {
      File gdmPackResponseFile =
          new ClassPathResource("GdmMappedResponseV2_inline_error.json").getFile();
      SsccScanResponse gdmPackResponse =
          gson.fromJson(
              new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
      doReturn(new ResponseEntity<>(gdmPackResponse, HttpStatus.PARTIAL_CONTENT))
          .when(restConnector)
          .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
      doReturn(twoDScanAsnDeliveryDocumentMapper)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32897",
              ReceivingConstants.ASN_CUSTOM_MAPPER,
              AsnToDeliveryDocumentsCustomMapper.class);
      doThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA,
                  ReceivingConstants.GDM_PARTIAL_RESPONSE,
                  "PO not found in delivery : [8458708681]"))
          .when(twoDScanAsnDeliveryDocumentMapper)
          .checkIfPartialContent(any(List.class));

      rxDeliveryService.getContainerSsccDetails(
          "9898", "mockShipmentNumber", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
      assertEquals(e.getDescription(), ReceivingConstants.GDM_PARTIAL_RESPONSE);
    }

    verify(restConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(twoDScanAsnDeliveryDocumentMapper, times(1)).checkIfPartialContent(any(List.class));
  }

  @Test
  public void testGdmGetShipmentsByGtinAndLotNumberReturns200()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    rxDeliveryService.getShipmentsByGtinAndLotNumberFromGdm(
        "95768482", "10368382330069", "1131444", "123221", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGdmGetShipmentsByGtinAndLotNumberReturns200_v1()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(ReceivingConstants.VERSION_V1).when(appConfig).getShipmentSearchVersion();
    rxDeliveryService.getShipmentsByGtinAndLotNumberFromGdm(
        "95768482", "10368382330069", "1131444", "123221", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGdmGetShipmentsByGtinAndLotNumberReturns404() throws Exception {
    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      rxDeliveryService.getShipmentsByGtinAndLotNumberFromGdm(
          "95768482", "10368382330069", "1131444", "123221", MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE);
      assertNotNull(e.getMessage());
    }
  }

  @Test
  public void testGdmGetShipmentsByGtinAndLotNumberReturnsServiceUnavailable() throws Exception {
    doThrow(new ResourceAccessException("Service unavailable"))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      rxDeliveryService.getShipmentsByGtinAndLotNumberFromGdm(
          "95768482", "10368382330069", "1131444", "123221", MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  @Test
  public void testGetShipmentsByGtinAndLotNumberReturns200()
      throws ReceivingException, IOException {
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("10368382330069");
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue("1131444");
    scannedDataMap.put("lot", lotNumberScannedData);
    ScannedData serialNumberScannedData = new ScannedData();
    serialNumberScannedData.setKey(ApplicationIdentifier.SERIAL.getKey());
    serialNumberScannedData.setValue("111222111");
    scannedDataMap.put("serial", serialNumberScannedData);
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(mockResponse)
        .when(rxDeliveryService)
        .getShipmentsByGtinAndLotNumberFromGdm(
            "95768482", "10368382330069", "1131444", "111222111", MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    List<DeliveryDocument> deliveryDocuments =
        rxDeliveryService.findDeliveryDocumentsByGtinAndLotNumber(
            "95768482", scannedDataMap, MockHttpHeaders.getHeaders());

    assertNotNull(deliveryDocuments);
  }

  @Test
  public void testGetShipmentsByGtinAndLotNumberReturns200_AutoSwitch()
      throws ReceivingException, IOException {
    // given
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("10368382330069");
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue("1131444");
    scannedDataMap.put("lot", lotNumberScannedData);
    ScannedData serialNumberScannedData = new ScannedData();
    serialNumberScannedData.setKey(ApplicationIdentifier.SERIAL.getKey());
    serialNumberScannedData.setValue("111222111");
    scannedDataMap.put("serial", serialNumberScannedData);
    File resource =
        new ClassPathResource("gdm_get_shipments_by_gtin_and_lotNumber_success_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    SsccScanResponse ssccScanResponse = gson.fromJson(mockResponse, SsccScanResponse.class);
    ssccScanResponse.setErrors(
        Collections.singletonList(
            new Error(ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND, Collections.emptyList())));
    mockResponse = gson.toJson(ssccScanResponse);
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(Rx_ASN_RCV_OVER_RIDE_KEY, Rx_ASN_RCV_OVER_RIDE_VALUE);
    doReturn(mockResponse)
        .when(rxDeliveryService)
        .getShipmentsByGtinAndLotNumberFromGdm(
            "95768482", "10368382330069", "1131444", "111222111", headers);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    // when
    List<DeliveryDocument> deliveryDocuments =
        rxDeliveryService.findDeliveryDocumentsByGtinAndLotNumber(
            "95768482", scannedDataMap, headers);
    // then
    assertNotNull(deliveryDocuments);
  }


  @Test
  public void testGetCurrentNode_valid_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<SsccScanResponse>(
                            mockSsccScanResponse(mockContainer()), HttpStatus.OK));

    SsccScanResponse returnedObj = rxDeliveryService.getCurrentNode(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>());
    Assert.assertNotNull(returnedObj);


  }

  @Test
  public void testGetUnitLevelContainers_valid_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<SsccScanResponse>(
                            mockSsccScanResponse(mockContainer()), HttpStatus.OK));

    SsccScanResponse returnedObj = rxDeliveryService.getUnitLevelContainers(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders());
    Assert.assertNotNull(returnedObj);


  }

  @Test
  public void testGetUnitLevelContainers_invalid_no_content() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.NO_CONTENT));

    SsccScanResponse returnedObj = rxDeliveryService.getUnitLevelContainers(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders());
    // RETURNS NULL
    Assert.assertNull(returnedObj);
  }

  @Test
  public void testUpdateEpcisReceiving_valid_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");
    when(restConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(
                    new ResponseEntity<String>(
                            "", HttpStatus.OK));
    HttpStatus returnedObj = rxDeliveryService.updateEpcisReceivingStatus(Arrays.asList(new UpdateGdmStatusV2Request()), MockHttpHeaders.getHeaders());
    // SUCCESS
    Assert.assertTrue(returnedObj.is2xxSuccessful());

  }

  @Test
  public void testUpdateEpcisReceiving_invalid_error() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.NOT_FOUND));

    HttpStatus returnedObj = rxDeliveryService.updateEpcisReceivingStatus(Arrays.asList(new UpdateGdmStatusV2Request()), MockHttpHeaders.getHeaders());

    // RETURNS error
    Assert.assertTrue(returnedObj.isError());
  }

  @Test
  public void testGetCurrentAndSiblings_valid_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<SsccScanResponse>(
                            mockSsccScanResponse(mockContainer()), HttpStatus.OK));

    SsccScanResponse returnedObj = rxDeliveryService.getCurrentAndSiblings(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>());
    Assert.assertNotNull(returnedObj);


  }

  @Test
  public void testGetCurrentAndSiblings_valid_null_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.OK));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getCurrentAndSiblings(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>()));
    // ERROR
    Assertions.assertEquals( "Scanned SSCC %s was not found on this delivery. Please quarantine this freight and submit a problem ticket.", exception2.getMessage());


  }

  @Test
  public void testGetUnitLevelContainers_valid_null_response() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.OK));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getUnitLevelContainers(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Scanned SSCC %s was not found on this delivery. Please quarantine this freight and submit a problem ticket.", exception2.getMessage());


  }

  @Test
  public void testGetCurrentAndSiblings_invalid_no_content() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.NO_CONTENT));

    SsccScanResponse returnedObj = rxDeliveryService.getCurrentAndSiblings(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>());
    // RETURNS NULL
    Assert.assertNull(returnedObj);


  }

  @Test
  public void testGetCurrentAndSiblings_invalid_throw_exception() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getCurrentAndSiblings(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>()));
    // ERROR
    Assertions.assertEquals( "Error from GDM while getting the details of other cases/units associated with this barcode. Please contact support.", exception1.getMessage());

    doThrow(new ResourceAccessException("Resource unavailable"))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getCurrentAndSiblings(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>()));
    // ERROR
    Assertions.assertEquals( "Error while calling GDM", exception2.getMessage());


  }


  @Test
  public void testUpdateEpcisReceivingStatus_invalid_throw_exception() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));

    Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.updateEpcisReceivingStatus(Arrays.asList(new UpdateGdmStatusV2Request()), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error in updating the Receiving status at GDM. Please contact support.", exception1.getMessage());

    doThrow(new ResourceAccessException("Resource unavailable"))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.updateEpcisReceivingStatus(Arrays.asList(new UpdateGdmStatusV2Request()), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error while calling GDM", exception2.getMessage());


  }

  @Test
  public void testGetUnitLevelContainers_invalid_throw_exception() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getUnitLevelContainers(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error while getting unit serial details.", exception1.getMessage());

    doThrow(new ResourceAccessException("Resource unavailable"))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getUnitLevelContainers(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error while calling GDM", exception2.getMessage());


  }


  @Test
  public void testGetUnitSerializedInfo_invalid_throw_exception() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getUnitSerializedInfo(new UnitSerialRequest(), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error while getting unit serial details.", exception1.getMessage());

    doThrow(new ResourceAccessException("Resource unavailable"))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getUnitSerializedInfo(new UnitSerialRequest(), MockHttpHeaders.getHeaders()));
    // ERROR
    Assertions.assertEquals( "Error while calling GDM", exception2.getMessage());


  }



  @Test
  public void testGetCurrentNode_invalid_throw_exception() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getCurrentNode(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>()));
    // ERROR
    Assertions.assertEquals( "Scanned barcode is not found on this delivery. Please quarantine this freight and submit a problem ticket.", exception1.getMessage());

    doThrow(new ResourceAccessException("Resource unavailable"))
            .when(restConnector)
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class));

    Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
            () -> rxDeliveryService.getCurrentNode(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>()));
    // ERROR
    Assertions.assertEquals( "Error while calling GDM", exception2.getMessage());


  }

  @Test
  public void testGetCurrentNode_invalid_no_content() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, HttpStatus.NO_CONTENT));

    SsccScanResponse returnedObj = rxDeliveryService.getCurrentNode(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>());
    // RETURN NULL
    Assert.assertNull(returnedObj);


  }



  @Test
  public void testGetCurrentNode_invalid_internal_server_error() throws IOException {

    Mockito.when(appConfig.getGdmBaseUrl()).thenReturn("http://mockurl.com");

    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(SsccScanResponse.class)))
            .thenReturn(
                    new ResponseEntity<>(
                            null, INTERNAL_SERVER_ERROR));

    SsccScanResponse returnedObj = rxDeliveryService.getCurrentNode(new ShipmentsContainersV2Request(), MockHttpHeaders.getHeaders(), new HashMap<>());
    // RETURN NULL
    Assert.assertNull(returnedObj);


  }
  @Test
  public void testSearchShipmentsByDeliveryAndGtinAndLotNumberReturnsSuccess()
      throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("gdm_search_shipments_by_delivery_gtin_lot_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<Shipment> shipmentList =
        rxDeliveryService.searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            "95768482", "10368382330069", "1131444", MockHttpHeaders.getHeaders());

    assertNotNull(shipmentList);
    assertTrue(shipmentList.size() == 1);
  }

  @Test
  public void testSearchShipmentsByDeliveryAndGtinAndLotNumberReturnsNothingBeforeItemCataloging()
      throws ReceivingException, IOException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    doReturn(new ResponseEntity<>(null, HttpStatus.NO_CONTENT))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    doReturn(new ArrayList<>())
        .when(itemCatalogRepository)
        .findByDeliveryNumberAndNewItemUPC(anyLong(), anyString());

    try {
      List<Shipment> shipmentList =
          rxDeliveryService.searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
              "95768482", "10368382330069", "1131444", MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_FAILURE);
      assertEquals(
          e.getMessage(),
          String.format(
              RxConstants.GDM_SHIPMENT_NOT_FOUND_FOR_DELIVERY_GTIN_AND_LOT,
              deliveryNumber,
              gtin,
              lotNumber));
    }
  }

  @Test
  public void testSearchShipmentsByDeliveryAndGtinAndLotNumberReturnsNothingAfterItemCataloging()
      throws ReceivingException, IOException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    doReturn(new ResponseEntity<>(null, HttpStatus.NO_CONTENT))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    doReturn(Arrays.asList(getItemCatalog()))
        .when(itemCatalogRepository)
        .findByDeliveryNumberAndNewItemUPC(anyLong(), anyString());

    try {
      List<Shipment> shipmentList =
          rxDeliveryService.searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
              "95768482", "10368382330069", "1131444", MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_SEARCH_SHIPMENT_FAILURE_AFTER_UPC_CATALOG);
      assertEquals(
          e.getMessage(),
          String.format(
              RxConstants.GDM_SEARCH_SHIPMENT_FAILURE_AFTER_UPC_CATALOG, gtin, deliveryNumber));
    }
  }

  @Test
  public void testSearchShipmentsByDeliveryAndGtinAndLotNumberReturnsError()
      throws ReceivingException {
    doThrow(new ResourceAccessException("Service unavailable"))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      List<Shipment> shipmentList =
          rxDeliveryService.searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
              "95768482", "10368382330069", "1131444", MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.GDM_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  @Test
  public void
      testFindDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingReturnsSuccessAndItsNotInvokingSearchShipmentsApi()
          throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(MockInstruction.getDeliveryDocuments())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentList =
        rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
            deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());

    assertNotNull(deliveryDocumentList);

    verify(rxDeliveryService, times(0))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      testFindDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingReturnsSuccessAndItsInvokingSearchShipmentsApi()
          throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(new ArrayList<>(), MockInstruction.getDeliveryDocuments())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    doReturn(searchShipmentsByGtinAndLotResponse())
        .when(rxDeliveryService)
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(restConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    List<DeliveryDocument> deliveryDocumentList =
        rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
            deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());

    assertNotNull(deliveryDocumentList);

    verify(rxDeliveryService, times(1))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    verify(rxDeliveryService, times(2))
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void testlinkDeliveryAndShipmentByGtinAndLotNumber() throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(MockInstruction.getDeliveryDocuments())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    doReturn(searchShipmentsByGtinAndLotResponse())
        .when(rxDeliveryService)
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(restConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    Optional<List<DeliveryDocument>> deliveryDocumentList =
        rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(
            deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());

    assertNotNull(deliveryDocumentList.get());

    verify(rxDeliveryService, times(1))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    verify(rxDeliveryService, times(1))
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void testlinkDeliveryAndShipmentByGtinAndLotNumberNoNewShipmentsToBeLinked()
      throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(MockInstruction.getDeliveryDocuments())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    doReturn(new ArrayList<>())
        .when(rxDeliveryService)
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(restConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    Optional<List<DeliveryDocument>> deliveryDocumentList =
        rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(
            deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());

    assertNotNull(deliveryDocumentList.get());

    verify(rxDeliveryService, times(1))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(0))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    verify(rxDeliveryService, times(1))
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void testlinkDeliveryAndShipmentByGtinAndLotNumber_NoNewShipmentsFound()
      throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(MockInstruction.getDeliveryDocuments())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    doThrow(new ReceivingBadDataException("", ""))
        .when(rxDeliveryService)
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(restConnector)
        .put(anyString(), anyString(), any(Map.class), any(Class.class));

    Optional<List<DeliveryDocument>> deliveryDocumentList =
        rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(
            deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());

    assertFalse(deliveryDocumentList.isPresent());

    verify(rxDeliveryService, times(1))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));
    verify(rxDeliveryService, times(0))
        .linkDeliveryWithShipment(anyString(), anyString(), anyString(), any(HttpHeaders.class));

    verify(rxDeliveryService, times(0))
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void testFindDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingReturnsNothing()
      throws ReceivingException {
    String deliveryNumber = "95768482";
    String gtin = "10368382330069";
    String lotNumber = "1131444";

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue(gtin);
    scannedDataMap.put("gtin", gtinScannedData);
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setValue(lotNumber);
    scannedDataMap.put("lot", lotNumberScannedData);

    doReturn(new ArrayList<>())
        .when(rxDeliveryService)
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));

    doReturn(new ArrayList<>())
        .when(rxDeliveryService)
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    try {
      deliveryDocumentList =
          rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
              deliveryNumber, scannedDataMap, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE);
      assertEquals(
          e.getMessage(),
          String.format(
              RxConstants.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
              deliveryNumber,
              gtin,
              lotNumber));
    }

    assertTrue(deliveryDocumentList.isEmpty());

    verify(rxDeliveryService, times(1))
        .searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
            anyString(), anyString(), anyString(), any(HttpHeaders.class));

    verify(rxDeliveryService, times(2))
        .findDeliveryDocumentsByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void test_prepareDeliveryLabelData() {

    PrintLabelData prepareDeliveryLabelData =
        rxDeliveryService.prepareDeliveryLabelData(12345l, 10, MockRxHttpHeaders.getHeaders());
    assertEquals(prepareDeliveryLabelData.getPrintRequests().size(), 10);
  }

  @Test
  public void testGetUnitSerializedInfo_happy_path() {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn("http://localhost:8080").when(appConfig).getGdmBaseUrl();
    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<String>(
                gson.toJson(mockPackItemResponse(mockPack())), HttpStatus.OK));
    UnitSerialRequest unitSerialRequest = new UnitSerialRequest();
    UnitRequestMap serialRequestMap = new UnitRequestMap();
    List identifierList = new ArrayList();
    serialRequestMap.setKey("SSCC");
    serialRequestMap.setValue("012325263214523658");
    identifierList.add(serialRequestMap);
    unitSerialRequest.setIdentifier(identifierList);
    unitSerialRequest.setDeliveryNumber("1234567");
    PackItemResponse response = null;
    try {
      response = rxDeliveryService.getUnitSerializedInfo(unitSerialRequest, new HttpHeaders());
    } catch (ReceivingException receivingException) {
    }
    assertTrue(CollectionUtils.isNotEmpty(response.getPacks()));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetUnitSerializedInfo_failure() {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_GLS_API_ENABLED, false);
    doReturn("http://localhost:8080").when(appConfig).getGdmBaseUrl();
    when(restConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, INTERNAL_SERVER_ERROR));
    UnitSerialRequest unitSerialRequest = new UnitSerialRequest();
    UnitRequestMap serialRequestMap = new UnitRequestMap();
    List identifierList = new ArrayList();
    serialRequestMap.setKey("SSCC");
    serialRequestMap.setValue("012325263214523658");
    identifierList.add(serialRequestMap);
    unitSerialRequest.setIdentifier(identifierList);
    unitSerialRequest.setDeliveryNumber("1234567");
    PackItemResponse response = null;
    try {
      response = rxDeliveryService.getUnitSerializedInfo(unitSerialRequest, new HttpHeaders());
    } catch (ReceivingException receivingException) {

    }
    assertFalse(CollectionUtils.isNotEmpty(response.getPacks()));
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      rxDeliveryService.findDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      AssertJUnit.assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      AssertJUnit.assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }

  private PackItemResponse mockPackItemResponse(Pack pack) {
    PackItemResponse packItemResponse = new PackItemResponse();
    packItemResponse.setPacks(Collections.singletonList(pack));
    return packItemResponse;
  }

  private SsccScanResponse mockSsccScanResponse(SsccScanResponse.Container container) {
    SsccScanResponse ssccScanResponse = new SsccScanResponse();
    ssccScanResponse.setContainers(Arrays.asList(container));
    return ssccScanResponse;
  }

  private SsccScanResponse.Container mockContainer() {
    SsccScanResponse.Container container = new SsccScanResponse.Container();
    container.setSerial("134");
    container.setHints(Arrays.asList("hint1", "hint2"));
    container.setGtin("123");
    container.setExpiryDate("261201");
    container.setLotNumber("123");
    return container;
  }

  private Pack mockPack() {
    Pack pack = new Pack();
    pack.setPackNumber("012325263214523658");
    pack.setExpiryDate("2022-01-01");
    pack.setGtin("00123123698456");
    pack.setLotNumber("L145225");
    pack.setSerial("5524562");
    pack.setTrackingStatus(RxConstants.VALID_ATTP_SERIALIZED_TRACKING_STATUS);
    pack.setMultiskuPack(false);
    pack.setPartialPack(false);
    Item packItem = new Item();
    List<ManufactureDetail> manufactureDetailList = new ArrayList<>();
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setExpirationDate("2022-01-0l1");
    manufactureDetail.setLotNumber("L145225");
    packItem.setGtin("00123123698477");
    packItem.setSerial("5524544");
    manufactureDetailList.add(manufactureDetail);
    packItem.setManufactureDetails(manufactureDetailList);
    pack.setItems(Collections.singletonList(packItem));
    return pack;
  }

  private List<Shipment> searchShipmentsByGtinAndLotResponse() {
    List<Shipment> shipments = new ArrayList<>();
    Shipment shipment = new Shipment();
    shipment.setDocumentId("546191216_20191106_719468_VENDOR_US");
    shipment.setShipmentNumber("546191216");
    shipments.add(shipment);
    return shipments;
  }

  private ItemCatalogUpdateLog getItemCatalog() {
    return ItemCatalogUpdateLog.builder()
        .id(0L)
        .itemNumber(567898765L)
        .deliveryNumber(95768482L)
        .newItemUPC("10368382330069")
        .oldItemUPC("00000943037194")
        .build();
  }
}
