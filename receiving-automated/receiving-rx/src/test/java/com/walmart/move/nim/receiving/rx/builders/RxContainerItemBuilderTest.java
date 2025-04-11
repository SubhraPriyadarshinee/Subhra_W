package com.walmart.move.nim.receiving.rx.builders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxContainerItemBuilderTest {

  private Gson gson = new Gson();

  @Mock private AppConfig appConfig;

  @InjectMocks private RxContainerItemBuilder rxContainerItemBuilder;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test_build() {

    when(tenantSpecificConfigReader.getDCTimeZone(
           any()))
            .thenReturn("UTC");

    ContainerItem containerItem =
        rxContainerItemBuilder.build(
            "MOCK_TRACKING_ID",
            MockInstruction.getInstruction(),
            getMockUpdateInstructionRequest(),
            Collections.emptyMap());

    assertNotNull(containerItem);
    assertEquals(containerItem.getTrackingId(), "MOCK_TRACKING_ID");
  }

  @Test
  public void test_build_NonFacility() {
    TenantContext.setFacilityNum(null);
    ContainerItem containerItem =
            rxContainerItemBuilder.build(
                    "MOCK_TRACKING_ID",
                    MockInstruction.getInstruction(),
                    getMockUpdateInstructionRequest(),
                    Collections.emptyMap());

    assertNotNull(containerItem);
    assertEquals(containerItem.getTrackingId(), "MOCK_TRACKING_ID");
  }

  private UpdateInstructionRequest getMockUpdateInstructionRequest() {
    String updateInstructionRequestBody =
        "{\n"
            + "    \"deliveryNumber\": 95352689,\n"
            + "    \"doorNumber\": \"608\",\n"
            + "    \"containerType\": \"Chep Pallet\",\n"
            + "    \"facility\": {\n"
            + "        \"buNumber\": \"{{siteId}}\",\n"
            + "        \"countryCode\": \"us\"\n"
            + "    },\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"quantity\": 1,\n"
            + "            \"totalPurchaseReferenceQty\": 100,\n"
            + "            \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"purchaseRefType\": \"{{type}}\",\n"
            + "            \"poDCNumber\": \"{{siteId}}\",\n"
            + "            \"quantityUOM\": \"ZA\",\n"
            + "            \"purchaseCompanyId\": \"1\",\n"
            + "            \"shippedQty\": \"12\",\n"
            + "            \"shippedQtyUom\": \"ZA\",\n"
            + "            \"deptNumber\": \"94\",\n"
            + "            \"gtin\": \"00029695410987\",\n"
            + "            \"itemNumber\": 561298341,\n"
            + "            \"vnpkQty\": 6,\n"
            + "            \"whpkQty\": 6,\n"
            + "            \"palletTi\": 0,\n"
            + "            \"palletHi\": 0,\n"
            + "            \"vendorPackCost\": 23.89,\n"
            + "            \"whpkSell\": 23.89,\n"
            + "            \"maxOverageAcceptQty\": 20,\n"
            + "            \"maxReceiveQty\": 120,\n"
            + "            \"expectedQty\": 100,\n"
            + "            \"vnpkWgtQty\": 10.0,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.852,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"description\": \"Tylenol\",\n"
            + "            \"secondaryDescription\": \"<T&S>\",\n"
            + "            \"financialReportingGroupCode\": \"US\",\n"
            + "            \"baseDivisionCode\": \"WM\",\n"
            + "            \"rotateDate\": \"2020-01-03\",\n"
            + "            \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "            \"promoBuyInd\": \"N\",\n"
            + "            \"profiledWarehouseArea\": \"CPS\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"shipmentDetails\": {\n"
            + "        \"inboundShipmentDocId\": \"546191213_20191106_719468_VENDOR_US\",\n"
            + "        \"shipmentNumber\": \"546191213\",\n"
            + "        \"loadNumber\": \"88528711\",\n"
            + "        \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "        \"shipperId\": null,\n"
            + "        \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "    },\n"
            + "    \"scannedDataList\": [\n"
            + "        {\n"
            + "            \"key\": \"gtin\",\n"
            + "            \"value\": \"01123840356119\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"key\": \"lot\",\n"
            + "            \"value\": \"12345678\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"key\": \"serial\",\n"
            + "            \"value\": \"{{serial}}\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"key\": \"expiryDate\",\n"
            + "            \"value\": \"200726\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    return gson.fromJson(updateInstructionRequestBody, UpdateInstructionRequest.class);
  }
}
