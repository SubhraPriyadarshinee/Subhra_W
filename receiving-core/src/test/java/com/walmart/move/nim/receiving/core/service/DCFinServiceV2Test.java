package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DCFinPurchaseRequestBody;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
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

public class DCFinServiceV2Test extends ReceivingTestBase {

  @InjectMocks private DCFinServiceV2 dcFinServiceV2;

  @Mock RestUtils restUtils;

  @Mock AppConfig appConfig;

  @Mock private RetryService jmsRecoveryService;

  @Spy private AsyncPersister asyncPersister;

  @Mock private TenantSpecificConfigReader configUtils;

  private String dcFinBaseUrl = "http://localhost:8080/dcfin";
  private Boolean isReceiptPostingEnaledForDCFin = Boolean.TRUE;
  private String dcFinApiKey = "a1-b1-c2";
  private ArgumentCaptor<String> containerArgumentCaptor = ArgumentCaptor.forClass(String.class);
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestData() {

    ReflectionTestUtils.setField(asyncPersister, "restUtils", restUtils);
    ReflectionTestUtils.setField(dcFinServiceV2, "appConfig", appConfig);
    ReflectionTestUtils.setField(asyncPersister, "jmsRecoveryService", jmsRecoveryService);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(restUtils);
    reset(jmsRecoveryService);
  }

  /** https://jira.walmart.com/browse/SCTNGMS-146 - Send data to dcfin */
  @Test
  public void testPostReceiptsToDCFin_ListOfContainers_AsyncPostingDisabled() {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(true);
    when(appConfig.getIsAsyncDCFinPostEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.overwriteFacilityInfo()).thenReturn("7552");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);

    List<Container> containers = new ArrayList<>();
    containers.add(MockContainer.getChildContainer());
    ContainerItem containerItem = containers.get(0).getContainerItems().get(0);
    containerItem.setTotalPurchaseReferenceQty(55);
    containerItem.setBaseDivisionCode(ReceivingConstants.BASE_DIVISION_CODE);
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    dcFinServiceV2.postReceiptUpdateToDCFin(
        containers, MockHttpHeaders.getHeaders(), true, deliveryMetaData, "PO");
    verify(jmsRecoveryService, times(1))
        .putForRetries(
            anyString(), eq(HttpMethod.POST), any(HttpHeaders.class), anyString(), any());
  }

  /** https://jira.walmart.com/browse/SCTNGMS-146 - Send data to dcfin */
  @Test
  public void testPostReceiptsToDCFin_ListOfContainers_AsyncPostingEnabled() {
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    when(appConfig.getIsReceiptPostingEnaledForDCFin()).thenReturn(true);
    when(configUtils.isReceiptPostingDisabled(anyString())).thenReturn(false);
    when(tenantSpecificConfigReader.overwriteFacilityInfo()).thenReturn("7552");
    when(appConfig.getIsAsyncDCFinPostEnabled()).thenReturn(true);
    when(jmsRecoveryService.putForRetries(
            anyString(), eq(HttpMethod.POST), any(HttpHeaders.class), anyString(), any()))
        .thenReturn(new RetryEntity());
    List<Container> containers = new ArrayList<>();
    containers.add(MockContainer.getChildContainer());
    containers.get(0).getContainerItems().get(0).setTotalPurchaseReferenceQty(55);
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    dcFinServiceV2.postReceiptUpdateToDCFin(
        containers, MockHttpHeaders.getHeaders(), true, deliveryMetaData, "PO");
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
            containerArgumentCaptor.capture());

    DCFinPurchaseRequestBody dcFinPurchaseRequestBody =
        new Gson().fromJson(containerArgumentCaptor.getValue(), DCFinPurchaseRequestBody.class);

    Map<String, Object> containerMiscInfo = containers.get(0).getContainerMiscInfo();
    if (MapUtils.isNotEmpty(containerMiscInfo)) {
      assertNotNull(dcFinPurchaseRequestBody.getPurchase().get(0).getProDate());
      if (ReceivingConstants.IMPORTS_PO_TYPES.contains(
          containerMiscInfo.get(ReceivingConstants.PURCHASE_REF_LEGACY_TYPE))) {
        assertNotNull(dcFinPurchaseRequestBody.getPurchase().get(0).getOriginType());
        assertNotNull(dcFinPurchaseRequestBody.getPurchase().get(0).getOriginFacilityNum());
        assertNotNull(dcFinPurchaseRequestBody.getPurchase().get(0).getOriginFacilityCountryCode());
      }
    }
  }
}
