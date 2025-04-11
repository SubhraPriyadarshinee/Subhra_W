package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DCFinServiceTest extends ReceivingTestBase {

  @InjectMocks private DCFinService dcFinService;

  @Mock RestUtils restUtils;

  @Mock AppConfig appConfig;

  @Mock ContainerService containerService;

  @Mock private RetryService jmsRecoveryService;
  @Mock RapidRelayerService rapidRelayerService;

  @Spy private AsyncPersister asyncPersister;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private String dcFinBaseUrl = "http://localhost:8080/dcfin";
  private Boolean isReceiptPostingEnaledForDCFin = Boolean.TRUE;
  private String dcFinApiKey = "a1-b1-c2";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestData() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString()))
        .thenReturn(true);

    ReflectionTestUtils.setField(asyncPersister, "restUtils", restUtils);
    ReflectionTestUtils.setField(dcFinService, "asyncPersister", asyncPersister);
    ReflectionTestUtils.setField(asyncPersister, "jmsRecoveryService", jmsRecoveryService);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(restUtils);
  }

  @Test
  public void testGetReceiptsPayloadForDCFinForSSTK() throws IOException {
    String dcFinReceiptRequest =
        dcFinService.getReceiptsPayloadForDCFin(MockContainer.getSSTKContainer());
    String dcFinReceiptSSTKSChemaFilePath =
        new File("../receiving-test/src/main/resources/jsonSchema/dcfinPostReceiptSSTK.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(dcFinReceiptSSTKSChemaFilePath))),
            dcFinReceiptRequest));
  }

  @Test
  public void testGetReceiptsPayloadForDCFinForDA() throws IOException {

    String dcFinReceiptRequest =
        dcFinService.getReceiptsPayloadForDCFin(MockContainer.getDAContainer());
    String dcFinReceiptDASChemaFilePath =
        new File("../receiving-test/src/main/resources/jsonSchema/dcfinPostReceiptDA.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(dcFinReceiptDASChemaFilePath))),
            dcFinReceiptRequest));
  }

  @Test
  public void testGetReceiptsPayloadForDCFinForSSTK_Imports() throws IOException {
    Container mockContainer = MockContainer.getSSTKContainer();
    mockContainer
        .getContainerItems()
        .forEach(
            o -> {
              o.setPoDcCountry("US");
              o.setPoDCNumber("6561");
              o.setImportInd(true);
            });
    String dcFinReceiptRequest = dcFinService.getReceiptsPayloadForDCFin(mockContainer);
    String dcFinReceiptSSTKSChemaFilePath =
        new File(
                "../receiving-test/src/main/resources/jsonSchema/dcfinPostReceiptSSTK_Imports.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(dcFinReceiptSSTKSChemaFilePath))),
            dcFinReceiptRequest));
  }

  @Test
  public void testGetReceiptsPayloadForDCFinForDA_Import() throws IOException {
    Container mockContainer = MockContainer.getDAContainer();
    mockContainer
        .getChildContainers()
        .forEach(
            o -> {
              o.getContainerItems().get(0).setPoDcCountry("US");
              o.getContainerItems().get(0).setPoDCNumber("6561");
              o.getContainerItems().get(0).setImportInd(true);
            });
    String dcFinReceiptRequest = dcFinService.getReceiptsPayloadForDCFin(mockContainer);
    String dcFinReceiptDASChemaFilePath =
        new File("../receiving-test/src/main/resources/jsonSchema/dcfinPostReceiptDA_Imports.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(dcFinReceiptDASChemaFilePath))),
            dcFinReceiptRequest));
  }

  //  @Test
  //  public void testGetReceiptsPayloadForDCFinForPBYL() throws IOException {
  //
  //    String dcFinReceiptRequest =
  //        dcFinService.getReceiptsPayloadForDCFin(MockContainer.getPBYLContainer());
  //    String dcFinReceiptDASChemaFilePath =
  //        new File("../receiving-test/src/main/resources/jsonSchema/dcfinPostReceiptPBYL.json")
  //            .getCanonicalPath();
  //    JsonSchemaValidator.validateContract(
  //        new String(Files.readAllBytes(Paths.get(dcFinReceiptDASChemaFilePath))),
  //        dcFinReceiptRequest);
  //  }

  @Test
  public void testPostReceiptsToDCFin_AsyncPostingEnabled() {
    reset(jmsRecoveryService);
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    when(appConfig.getIsAsyncDCFinPostEnabled()).thenReturn(true);
    when(jmsRecoveryService.putForRetries(
            anyString(), eq(HttpMethod.POST), any(HttpHeaders.class), anyString(), any()))
        .thenReturn(new RetryEntity());
    try {
      dcFinService.postReceiptsToDCFin(
          MockContainer.getDAContainer(), MockHttpHeaders.getHeaders(), true);
      verify(jmsRecoveryService, times(1))
          .putForRetries(
              anyString(), eq(HttpMethod.POST), any(HttpHeaders.class), anyString(), any());
      verify(asyncPersister, times(1))
          .asyncPost(
              anyString(),
              anyString(),
              any(RetryEntity.class),
              anyString(),
              any(HttpHeaders.class),
              anyString());
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testPostReceiptsToDCFin_outbox_Enabled() {
    reset(jmsRecoveryService);
    reset(asyncPersister);
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(isReceiptPostingEnaledForDCFin);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    try {
      dcFinService.postReceiptsToDCFin(
          MockContainer.getDAContainer(), MockHttpHeaders.getHeaders(), true);
      verify(jmsRecoveryService, times(0))
          .putForRetries(
              anyString(), eq(HttpMethod.POST), any(HttpHeaders.class), anyString(), any());
      verify(asyncPersister, times(0))
          .asyncPost(
              anyString(),
              anyString(),
              any(RetryEntity.class),
              anyString(),
              any(HttpHeaders.class),
              anyString());
      verify(rapidRelayerService, times(1)).produceHttpMessage(anyString(), anyString(), anyMap());
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "We are having trouble reaching DC Fin now")
  public void testPostReceiptsToDCFin_ExceptionDcFinDown() throws ReceivingException {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.SERVICE_UNAVAILABLE))
        .when(restUtils)
        .post(any(), any(), any(), any());
    dcFinService.postReceiptsToDCFin(
        MockContainer.getDAContainer(), MockHttpHeaders.getHeaders(), false);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Error while posting to DC Fin. DC Fin response.*")
  public void testPostReceiptsToDCFin_Exception2() throws ReceivingException {
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(isReceiptPostingEnaledForDCFin);
    doReturn(new ResponseEntity<String>("{}", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());
    dcFinService.postReceiptsToDCFin(
        MockContainer.getDAContainer(), MockHttpHeaders.getHeaders(), false);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = ReceivingException.CONTAINER_IS_NULL)
  public void testPostReceiptsToDCFin_ContainerException() throws ReceivingException {
    Container container = null;
    dcFinService.postReceiptsToDCFin(container);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Container is not a parent container : a329870000000000000000001")
  public void testPostReceiptsToDCFin_ContainerException2() throws ReceivingException {
    dcFinService.postReceiptsToDCFin(MockContainer.getChildContainer());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Container is yet not completed : a329870000000000000000001")
  public void testPostReceiptsToDCFin_ContainerException3() throws ReceivingException {
    dcFinService.postReceiptsToDCFin(MockContainer.getContainerInfo());
  }

  @Test
  public void testPostReceiptsToDCFin_Container() {

    try {
      Container container = MockContainer.getDAContainer();
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restUtils)
          .post(any(), any(), any(), any());
      when(containerService.getContainerIncludingChild(any(Container.class))).thenReturn(container);
      dcFinService.postReceiptsToDCFin(container);
      verify(containerService, times(1)).getContainerIncludingChild(container);
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testPostReceiptsToDCFin_TrackingId() {

    try {
      when(appConfig.getIsReceiptPostingEnaledForDCFin())
          .thenReturn(isReceiptPostingEnaledForDCFin);
      when(appConfig.getDcFinApiKey()).thenReturn(dcFinApiKey);
      when(appConfig.getDcFinBaseUrl()).thenReturn(dcFinBaseUrl);
      Container container = MockContainer.getDAContainer();
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restUtils)
          .post(any(), any(), any(), any());
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
      when(containerService.getContainerIncludingChild(any(Container.class))).thenReturn(container);
      dcFinService.postReceiptsToDCFin(container.getTrackingId());
      verify(containerService, times(1)).getContainerByTrackingId(container.getTrackingId());
      verify(containerService, times(1)).getContainerIncludingChild(container);
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Container is null")
  public void testPostReceiptsToDCFin_TrackingIdException() throws ReceivingException {
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(isReceiptPostingEnaledForDCFin);
    when(appConfig.getDcFinApiKey()).thenReturn(dcFinApiKey);
    when(appConfig.getDcFinBaseUrl()).thenReturn(dcFinBaseUrl);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(null);
    dcFinService.postReceiptsToDCFin("123");
  }

  @Test
  public void testPostReceiptsForDelivery() {
    Container container = MockContainer.getDAContainer();
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(isReceiptPostingEnaledForDCFin);
    when(appConfig.getDcFinApiKey()).thenReturn(dcFinApiKey);
    when(appConfig.getDcFinBaseUrl()).thenReturn(dcFinBaseUrl);
    List<Container> containerList = new ArrayList<>();
    containerList.add(container);
    containerList.add(MockContainer.getChildContainer());
    try {
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restUtils)
          .post(any(), any(), any(), any());
      when(containerService.getContainerByDeliveryNumber(1234l)).thenReturn(containerList);
      when(containerService.getContainerIncludingChild(any(Container.class))).thenReturn(container);
      dcFinService.postReceiptsForDelivery(1234l);
      verify(containerService, times(1)).getContainerByDeliveryNumber(1234l);
      verify(containerService, times(1)).getContainerIncludingChild(container);
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Unable to post receipts to DC Fin for following containers.*")
  public void testPostReceiptsForDelivery_Exception() throws ReceivingException {
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(isReceiptPostingEnaledForDCFin);
    when(appConfig.getDcFinApiKey()).thenReturn(dcFinApiKey);
    when(appConfig.getDcFinBaseUrl()).thenReturn(dcFinBaseUrl);
    List<Container> containerList = new ArrayList<>();
    containerList.add(MockContainer.getDAContainer());
    containerList.add(MockContainer.getDAContainer());
    containerList.add(MockContainer.getChildContainer());

    when(restUtils.post(any(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("{}", HttpStatus.INTERNAL_SERVER_ERROR))
        .thenReturn(new ResponseEntity<String>("{}", HttpStatus.OK));
    when(containerService.getContainerByDeliveryNumber(1234l)).thenReturn(containerList);
    when(containerService.getContainerIncludingChild(any(Container.class)))
        .thenReturn(MockContainer.getDAContainer());
    dcFinService.postReceiptsForDelivery(1234l);
    verify(restUtils, times(2)).post(any(), any(), any(), any());
  }

  @Test
  public void testGetHeadersForDCFin() {
    Container container = MockContainer.getDAContainer();
    HttpHeaders httpHeaders = dcFinService.getHeadersForDCFin(container);
    assertEquals(
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0),
        ReceivingUtils.getContainerUser(container));
    assertEquals(
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0),
        container.getTrackingId());
    assertEquals(
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).get(0),
        container.getFacilityNum().toString());
    assertEquals(
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).get(0),
        container.getFacilityCountryCode());
  }
}
