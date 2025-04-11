package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.testng.Assert.*;

public class RxDeliveryDocumentsMapperV2Test {

    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    @InjectMocks private RxDeliveryDocumentsMapperV2 rxDeliveryDocumentsMapperV2;

    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(tenantSpecificConfigReader);
    }

    @Test
    public void testMapGdmResponse() throws IOException, ReceivingException {

        SsccScanResponse ssccScanResponse = getGdmResponse();

        List<DeliveryDocument> returnedObj = rxDeliveryDocumentsMapperV2.mapGdmResponse(ssccScanResponse);

        Assert.assertNotNull(returnedObj);

        // SSCC MUST BE SAME 00800109399919082945
        Assert.assertEquals(ssccScanResponse.getContainers().get(0).getSscc(),
                returnedObj.get(0).getGdmCurrentNodeDetail().getContainers().get(0).getSscc());

    }

    private SsccScanResponse getGdmResponse() {
        return gson.fromJson("{\n" +
                "  \"containers\": [\n" +
                "    {\n" +
                "      \"id\": \"cdc258b6-7156-4092-809c-2e258436e4d1\",\n" +
                "      \"sscc\": \"00800109399919082945\",\n" +
                "      \"lotNumber\": \"\",\n" +
                "      \"unitCount\": 96,\n" +
                "      \"childCount\": 4,\n" +
                "      \"shipmentDocumentId\": \"50048525_2225371795_241224061100_v2_6032_US\",\n" +
                "      \"receivingStatus\": \"Open\",\n" +
                "      \"hints\": [\n" +
                "        \"CASE_PACK_ITEM\",\n" +
                "        \"SSCC_PACKAGE\",\n" +
                "        \"SINGLE_SKU_PACKAGE\"\n" +
                "      ],\n" +
                "      \"level\": 1,\n" +
                "      \"topLevelContainerSscc\": \"00800109399919082945\",\n" +
                "      \"topLevelContainerId\": \"cdc258b6-7156-4092-809c-2e258436e4d1\",\n" +
                "      \"itemInfo\": [\n" +
                "        {\n" +
                "          \"itemNumber\": 553413723,\n" +
                "          \"gtin\": \"00316571657100\",\n" +
                "          \"totalUnitQty\": 96\n" +
                "        }\n" +
                "      ],\n" +
                "      \"trackingStatus\": \"ValidationSuccessful\",\n" +
                "      \"shipmentNumber\": \"50048525_2225371795_241224061100_v2\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"delivery\": {\n" +
                "    \"deliveryNumber\": 50048525,\n" +
                "    \"statusInformation\": {\n" +
                "      \"operationalStatus\": \"OPN\",\n" +
                "      \"statusReasonCode\": [\n" +
                "        \"DOOR_OPEN\"\n" +
                "      ],\n" +
                "      \"status\": \"OPN\"\n" +
                "    },\n" +
                "    \"scheduled\": \"2024-12-24T23:45:00.000Z\",\n" +
                "    \"arrivalTimeStamp\": \"2024-12-24T17:41:20.992Z\",\n" +
                "    \"doorOpenTime\": \"2024-12-24T06:49:13.524Z\",\n" +
                "    \"trailerId\": \"T50048525\"\n" +
                "  },\n" +
                "  \"shipments\": [\n" +
                "    {\n" +
                "      \"documentId\": \"50048525_2225371795_241224061100_v2_6032_US\",\n" +
                "      \"shipmentNumber\": \"50048525_2225371795_241224061100_v2\",\n" +
                "      \"documentType\": \"EPCIS\",\n" +
                "      \"documentRevision\": 0,\n" +
                "      \"source\": {\n" +
                "        \"globalLocationNumber\": \"0078742030548\",\n" +
                "        \"shipperId\": \"988246\",\n" +
                "        \"shipperName\": \"BAMA COMPANIES INC\"\n" +
                "      },\n" +
                "      \"shipmentDetail\": {\n" +
                "        \"reportedDeliveryNumber\": 50048525\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"purchaseOrders\": [\n" +
                "    {\n" +
                "      \"poNumber\": \"2225371795\",\n" +
                "      \"freightBillQty\": 4,\n" +
                "      \"cubeQty\": 150.0,\n" +
                "      \"poDcCountry\": \"US\",\n" +
                "      \"baseDivisionCode\": \"WM\",\n" +
                "      \"poType\": \"20\",\n" +
                "      \"purchaseCompanyId\": 1,\n" +
                "      \"poStatus\": \"ACTV\",\n" +
                "      \"financialGroupCode\": \"US\",\n" +
                "      \"vendorInformation\": {\n" +
                "        \"name\": \"BAMA COMPANIES INC\",\n" +
                "        \"number\": 988246,\n" +
                "        \"department\": 38,\n" +
                "        \"serialInfoEnabled\": true\n" +
                "      },\n" +
                "      \"poDates\": {\n" +
                "        \"cancel\": \"2019-03-26\",\n" +
                "        \"ordered\": \"2019-03-19\",\n" +
                "        \"mabd\": \"2019-03-26\",\n" +
                "        \"ship\": \"2019-03-26\"\n" +
                "      },\n" +
                "      \"poDcNumber\": 6032,\n" +
                "      \"lines\": [\n" +
                "        {\n" +
                "          \"poLineNumber\": 1,\n" +
                "          \"channel\": \"SSTKU\",\n" +
                "          \"whpkQty\": 100,\n" +
                "          \"whpkSell\": 54.7,\n" +
                "          \"vnpkQty\": 2400,\n" +
                "          \"vnpkCost\": 1.84,\n" +
                "          \"vnpkWeightQty\": 2.97,\n" +
                "          \"vnpkWeightQtyUom\": \"LB\",\n" +
                "          \"vnpkCubeQty\": 0.246,\n" +
                "          \"vnpkCubeQtyUom\": \"CF\",\n" +
                "          \"event\": \"POS REPLEN\",\n" +
                "          \"polAdditionalFields\": {\n" +
                "            \"Sample_Po_Ind\": \"N\",\n" +
                "            \"Manual_Po_Ind\": \"N\"\n" +
                "          },\n" +
                "          \"poLineStatus\": \"ACTIVE\",\n" +
                "          \"orderedQty\": 4.0,\n" +
                "          \"orderedQtyUom\": \"ZA\",\n" +
                "          \"itemDetails\": {\n" +
                "            \"consumableGTIN\": \"00316571657100\",\n" +
                "            \"orderableGTIN\": \"50316571657105\",\n" +
                "            \"warehousePackGTIN\": \"30316571657101\",\n" +
                "            \"supplierStockId\": \"16571-0657-10\",\n" +
                "            \"conveyable\": false,\n" +
                "            \"itemTypeCode\": \"7\",\n" +
                "            \"vendorDepartment\": \"38\",\n" +
                "            \"color\": \"CAPSUL\",\n" +
                "            \"size\": \"100\",\n" +
                "            \"warehousePackQuantity\": 100,\n" +
                "            \"orderableQuantity\": 2400,\n" +
                "            \"handlingCode\": \"C\",\n" +
                "            \"packType\": \"B\",\n" +
                "            \"complianceItem\": false,\n" +
                "            \"isControlledSubstance\": false,\n" +
                "            \"isTemperatureSensitive\": false,\n" +
                "            \"descriptions\": [\n" +
                "              \"CEVIMELINE HCL 30MG\",\n" +
                "              \"CEVIMELINE HCL 30MG\"\n" +
                "            ],\n" +
                "            \"itemAdditonalInformation\": {\n" +
                "              \"isDscsaExemptionInd\": false,\n" +
                "              \"weight\": 0.08,\n" +
                "              \"cubeUOM\": \"CF\",\n" +
                "              \"cube\": 0.006,\n" +
                "              \"weightUOM\": \"LB\",\n" +
                "              \"supplierCasePackType\": \"B\",\n" +
                "              \"weightFormatTypeCode\": \"F\",\n" +
                "              \"isTemperatureSensitive\": \"N\",\n" +
                "              \"isControlledSubstance\": \"N\"\n" +
                "            },\n" +
                "            \"number\": 553413723\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"weightQty\": 100.0,\n" +
                "      \"weightQtyUom\": \"LB\",\n" +
                "      \"cubeQtyUom\": \"CF\"\n" +
                "    }\n" +
                "  ]\n" +
                "}", SsccScanResponse.class);
    }


}