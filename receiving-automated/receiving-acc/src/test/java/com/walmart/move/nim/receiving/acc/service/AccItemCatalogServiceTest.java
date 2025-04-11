package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.VendorUpcUpdateRequest;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AccItemCatalogServiceTest extends ReceivingTestBase {

  @InjectMocks private AccItemCatalogService accItemCatalogService;

  @Mock private ItemCatalogRepository itemCatalogRepository;

  @Mock private ReportService reportService;

  @Mock private ACLService aclService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private ItemMDMService itemMDMService;

  @Mock private TenantSpecificConfigReader configUtils;

  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;

  private List<Integer> facilityNumberList;

  private LocationInfo onlineDoor;
  private LocationInfo offlineDoor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);

    onlineDoor = LocationInfo.builder().isOnline(Boolean.TRUE).build();
    offlineDoor = LocationInfo.builder().isOnline(Boolean.FALSE).build();

    facilityNumberList = new ArrayList<>();
    facilityNumberList.add(32818);
    facilityNumberList.add(6561);
    facilityNumberList.add(32987);

    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    TenantContext.setFacilityNum(32818);
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
    reset(aclService);
    reset(itemCatalogRepository);
    reset(configUtils);
    reset(itemMDMService);
  }

  @Test
  public void testUpdateVendorUpcForSuccessScenario() {
    doReturn(true).when(accManagedConfig).isAclItemCatalogEnabled();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    doNothing()
        .when(aclService)
        .updateVendorUpc(any(VendorUpcUpdateRequest.class), any(HttpHeaders.class));
    accItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(aclService, Mockito.times(1))
        .updateVendorUpc(any(VendorUpcUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "ACL service is down")
  public void testUpdateVendorUpcWhenACLServiceThrowsError() {
    doReturn(true).when(accManagedConfig).isAclItemCatalogEnabled();
    doThrow(new ReceivingInternalException(ExceptionCodes.ACL_ERROR, ACCConstants.ACL_SERVICE_DOWN))
        .when(aclService)
        .updateVendorUpc(any(VendorUpcUpdateRequest.class), any(HttpHeaders.class));
    accItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(aclService, Mockito.times(1))
        .updateVendorUpc(any(VendorUpcUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(0)).save(any());
  }

  @Test
  public void testUpdateVendorUpcAclNotCalledForOfflineDoor() {
    itemCatalogUpdateRequest.setLocationInfo(offlineDoor);
    doReturn(true).when(accManagedConfig).isAclItemCatalogEnabled();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    accItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, MockHttpHeaders.getHeaders());
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(aclService, Mockito.times(0))
        .updateVendorUpc(any(VendorUpcUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }
}
