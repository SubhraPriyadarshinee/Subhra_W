package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CATALOG_MESSAGE_PUBLISH_FLOW;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaACCItemCatalogServiceTest extends ReceivingTestBase {
  @InjectMocks private KafkaACCItemCatalogService kafkaACCItemCatalogService;

  @Mock private ItemCatalogRepository itemCatalogRepository;

  @Mock private ReportService reportService;

  @Mock private KafkaTemplate securePublisher;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private ItemMDMService itemMDMService;

  @Mock private TenantSpecificConfigReader configUtils;

  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;
  private Gson gson = new Gson();

  private List<Integer> facilityNumberList;

  private LocationInfo onlineDoor;
  private LocationInfo offlineDoor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(kafkaACCItemCatalogService, "gson", gson);
    ReflectionTestUtils.setField(kafkaACCItemCatalogService, "securePublisher", securePublisher);

    onlineDoor = LocationInfo.builder().isOnline(Boolean.TRUE).build();
    offlineDoor = LocationInfo.builder().isOnline(Boolean.FALSE).build();

    facilityNumberList = new ArrayList<>();
    facilityNumberList.add(32898);
    facilityNumberList.add(6561);
    facilityNumberList.add(32987);

    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    TenantContext.setFacilityNum(32898);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setup() {
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setLocationId("100");
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");
    itemCatalogUpdateRequest.setOldItemUPC("00000943037194");
    itemCatalogUpdateRequest.setItemInfoHandKeyed(Boolean.TRUE);
    itemCatalogUpdateRequest.setLocationInfo(onlineDoor);
    itemCatalogUpdateRequest.setVendorStockNumber("9999999");
    itemCatalogUpdateRequest.setVendorNumber("888888");
  }

  @AfterMethod()
  public void resetMocks() {
    reset(itemCatalogRepository);
    reset(configUtils);
    reset(itemMDMService);
  }

  @Test
  public void testUpdateVendorUpcForSuccessScenario() {
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_ON_HAWKEYE_CATALOG_TOPIC))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    kafkaACCItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest, MockHttpHeaders.getHeaders());
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to access Kafka.*")
  public void testUpdateVendorUpcWhenKafkaServiceThrowsError() {
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_ON_HAWKEYE_CATALOG_TOPIC))
        .thenReturn(true);
    when(securePublisher.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, CATALOG_MESSAGE_PUBLISH_FLOW)));
    kafkaACCItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest, MockHttpHeaders.getHeaders());
    verify(itemMDMService, Mockito.times(0))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(0)).save(any());
  }

  @Test
  public void testUpdateVendorUpcNotCalledForOfflineDoor() {
    itemCatalogUpdateRequest.setLocationInfo(offlineDoor);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_ON_HAWKEYE_CATALOG_TOPIC))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    kafkaACCItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest, MockHttpHeaders.getHeaders());
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }
}
