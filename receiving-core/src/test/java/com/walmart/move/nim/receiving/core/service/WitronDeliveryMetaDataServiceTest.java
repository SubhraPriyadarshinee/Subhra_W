package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APPROVED_HACCP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DocumentMeta;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PoLineDetails;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseReferenceLineMeta;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WitronDeliveryMetaDataServiceTest extends ReceivingTestBase {

  @InjectMocks private WitronDeliveryMetaDataService witronDeliveryMetaDataService;
  @Spy private DeliveryMetaDataRepository deliveryMetaDataRepository;

  private Gson gson = new Gson();
  private DeliveryWithOSDRResponse mockDelivery;
  File resource, resourceForHaccp = null;
  String delivery_number = "172855";

  String purchaseReferenceNumber0 = "7971298360";
  String purchaseReferenceNumber1 = "7971298361";
  String purchaseReferenceNumber2 = "7971298362";
  String purchaseReferenceNumber3 = "7971298363";
  String purchaseReferenceNumber4 = "7971298364";
  Integer purchasereferencelinenumber1 = 1;
  Integer purchasereferencelinenumber2 = 2;
  final DeliveryMetaData deliveryMeta = new DeliveryMetaData();
  final DeliveryMetaData deliveryMetaForHaccp = new DeliveryMetaData();
  Optional<DeliveryMetaData> optionalDeliveryMeta, optionalDeliveryMetaForHaccp = null;
  String mgrUserId = "k0c0e5k";

  @BeforeClass
  public void initMocks() throws Exception {

    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    resource = new ClassPathResource("deliveryMetaData.json").getFile();
    String deliveryMetaDataJson = new String(Files.readAllBytes(resource.toPath()));
    deliveryMeta.setPoLineDetails(deliveryMetaDataJson);
    optionalDeliveryMeta = Optional.of(deliveryMeta);

    resourceForHaccp = new ClassPathResource("deliveryMetaData_haccp_payload.json").getFile();
    String jsonForHaccp = new String(Files.readAllBytes(resourceForHaccp.toPath()));
    deliveryMetaForHaccp.setPoLineDetails(jsonForHaccp);
    optionalDeliveryMetaForHaccp = Optional.of(deliveryMetaForHaccp);

    resource = new ClassPathResource("gdm_v3_getDelivery.json").getFile();
    String mockData = new String(Files.readAllBytes(resource.toPath()));
    mockDelivery = gson.fromJson(mockData, DeliveryWithOSDRResponse.class);
  }

  @BeforeClass
  public void setReflectionTestUtil() {
    ReflectionTestUtils.setField(witronDeliveryMetaDataService, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryMetaDataRepository);
  }

  @Test
  public void doManagerOverride_IgnoreOverage() throws Exception {
    String delivery_number = "172855";
    String purchaseReferenceNumber = "7971298361";
    Integer purchaseReferenceLineNumber = 1;
    String expectedPoLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7971298361\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreOverage\":\"true\",\"ignoreOverageBy\":\"k0c0e5k\"}]}]}";

    final DeliveryMetaData deliveryMetaData_new =
        witronDeliveryMetaDataService.doManagerOverride(
            mgrUserId,
            delivery_number,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            ReceivingConstants.IGNORE_OVERAGE);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
    verify(deliveryMetaDataRepository, times(1)).save(any());
    assertNotNull(deliveryMetaData_new);
    Assert.assertEquals(deliveryMetaData_new.getPoLineDetails(), expectedPoLineDetails);
  }

  @Test
  public void doManagerOverride_IgnoreExpiry() throws Exception {

    String purchaseReferenceNumber = "7971298361";
    Integer purchaseReferenceLineNumber = 1;
    final DeliveryMetaData deliveryMeta = new DeliveryMetaData();
    String expectedPoLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7971298361\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreExpiry\":\"true\",\"ignoreExpiryBy\":\"k0c0e5k\"}]}]}";

    final DeliveryMetaData deliveryMetaData_new =
        witronDeliveryMetaDataService.doManagerOverride(
            mgrUserId,
            delivery_number,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            ReceivingConstants.IGNORE_EXPIRY);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
    verify(deliveryMetaDataRepository, times(1)).save(any());

    assertNotNull(deliveryMetaData_new);

    Assert.assertEquals(deliveryMetaData_new.getPoLineDetails(), expectedPoLineDetails);
  }

  @Test
  public void doManagerOverride_ApproveHaccp() throws Exception {
    String delivery_number = "172855";
    String purchaseReferenceNumber = "7971298361";
    Integer purchaseReferenceLineNumber = 1;
    String expectedPoLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7971298361\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"approvedHaccp\":\"true\",\"approvedHaccpBy\":\"k0c0e5k\"}]}]}";

    final DeliveryMetaData deliveryMetaData_new =
        witronDeliveryMetaDataService.doManagerOverride(
            mgrUserId,
            delivery_number,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            APPROVED_HACCP);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
    verify(deliveryMetaDataRepository, times(1)).save(any());
    assertNotNull(deliveryMetaData_new);
    Assert.assertEquals(deliveryMetaData_new.getPoLineDetails(), expectedPoLineDetails);
  }

  @Test
  public void testManagerOverride_IgnoreExpiryTrue() throws Exception {

    resource = new ClassPathResource("deliveryMetaData.json").getFile();
    String deliveryMetaDataJson = new String(Files.readAllBytes(resource.toPath()));
    deliveryMeta.setPoLineDetails(deliveryMetaDataJson);
    Optional<DeliveryMetaData> optionalDeliveryMeta2 = Optional.of(deliveryMeta);

    doReturn(optionalDeliveryMeta2)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(delivery_number);

    // execution
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number,
            purchaseReferenceNumber2,
            purchasereferencelinenumber1,
            ReceivingConstants.IGNORE_EXPIRY);

    boolean isManagerOverride2 =
        witronDeliveryMetaDataService.isManagerOverrideV2(
            delivery_number,
            purchaseReferenceNumber2,
            purchasereferencelinenumber1,
            ReceivingConstants.IGNORE_EXPIRY);

    assertTrue(isManagerOverride);
    assertTrue(isManagerOverride2);
    verify(deliveryMetaDataRepository, times(2)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_IgnoreExpiryFalse() throws Exception {

    doReturn(optionalDeliveryMeta).when(deliveryMetaDataRepository).findByDeliveryNumber(any());
    // execution
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number,
            purchaseReferenceNumber1,
            purchasereferencelinenumber2,
            ReceivingConstants.IGNORE_EXPIRY);

    assertFalse(isManagerOverride);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_IgnoreExpiryFalseForNoPurchaseReferenceNumber3() throws Exception {

    doReturn(optionalDeliveryMeta).when(deliveryMetaDataRepository).findByDeliveryNumber(any());

    // execution
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number,
            purchaseReferenceNumber3,
            purchasereferencelinenumber1,
            ReceivingConstants.IGNORE_EXPIRY);

    assertFalse(isManagerOverride);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void testCreateDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("9967271326");
    List<DocumentMeta> documents = new ArrayList<>();
    DocumentMeta document = new DocumentMeta();
    document.setPurchaseReferenceNumber("9164390046");
    document.setPoType("20");
    documents.add(document);
    PoLineDetails poLineDetails = new PoLineDetails();
    poLineDetails.setDocuments(documents);
    deliveryMetaData.setPoLineDetails(gson.toJson(poLineDetails, PoLineDetails.class));

    witronDeliveryMetaDataService.createDeliveryMetaData(mockDelivery);

    verify(deliveryMetaDataRepository, times(1)).save(deliveryMetaData);
  }

  @Test
  public void testUpdateDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("9967271326");
    List<DocumentMeta> documents = new ArrayList<>();
    DocumentMeta document = new DocumentMeta();
    document.setPurchaseReferenceNumber("9164390046");
    document.setPoType("20");
    documents.add(document);
    PoLineDetails poLineDetails = new PoLineDetails();
    poLineDetails.setDocuments(documents);
    deliveryMetaData.setPoLineDetails(gson.toJson(poLineDetails, PoLineDetails.class));

    witronDeliveryMetaDataService.updateDeliveryMetaData(deliveryMetaData, mockDelivery);

    verify(deliveryMetaDataRepository, times(1)).save(deliveryMetaData);
  }

  @Test
  public void testUpdateDeliveryMetaData_null_PO_LineDetails() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("9967271326");
    deliveryMetaData.setPoLineDetails(null);

    witronDeliveryMetaDataService.updateDeliveryMetaData(deliveryMetaData, mockDelivery);

    verify(deliveryMetaDataRepository, times(1)).save(deliveryMetaData);
  }

  @Test
  public void isManagerOverride_IgnoreExpiryFalseForNoLines() throws Exception {

    doReturn(optionalDeliveryMeta).when(deliveryMetaDataRepository).findByDeliveryNumber(any());

    // execution
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number,
            purchaseReferenceNumber0,
            purchasereferencelinenumber1,
            ReceivingConstants.IGNORE_EXPIRY);

    assertFalse(isManagerOverride);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_forPoAndPoLine() throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverrideV2(
            delivery_number, "0552442972", 1, APPROVED_HACCP);
    assertTrue(isManagerOverride);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_forPoAndPoLine_V2Disabled() throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean isManagerOverride =
        witronDeliveryMetaDataService.isManagerOverrideV2(
            delivery_number, "0552442972", 1, APPROVED_HACCP);
    assertTrue(isManagerOverride);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_for_DifferentLine1_nohaccp_but_same_PO()
      throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean isManagerOverride_forPO_CheckLine1 =
        witronDeliveryMetaDataService.isManagerOverrideV2(
            delivery_number, "0552442972", 1, APPROVED_HACCP);
    assertTrue(isManagerOverride_forPO_CheckLine1);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_for_DifferentLine1_nohaccp_but_same_PO_V2Disabled()
      throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean isManagerOverride_forPO_CheckLine1 =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number, "0552442972", 1, APPROVED_HACCP);
    assertTrue(isManagerOverride_forPO_CheckLine1);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_check_for_Line81_nohaccp_but_same_PO_Line2_IsHaccp()
      throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean ismanageroverride_check_for_line81_nohaccp_but_same_po_line2_ishaccp =
        witronDeliveryMetaDataService.isManagerOverrideV2(
            delivery_number, "0552442969", 1, APPROVED_HACCP);
    assertTrue(ismanageroverride_check_for_line81_nohaccp_but_same_po_line2_ishaccp);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void
      isManagerOverride_approvedHaccp_check_for_Line81_nohaccp_but_same_PO_Line2_IsHaccp_V2Disabled()
          throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean ismanageroverride_check_for_line81_nohaccp_but_same_po_line2_ishaccp =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number, "0552442969", 1, APPROVED_HACCP);
    assertTrue(ismanageroverride_check_for_line81_nohaccp_but_same_po_line2_ishaccp);
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
  }

  @Test
  public void isManagerOverride_approvedHaccp_forDelivery_butNOT_for_thisPO() throws Exception {
    doReturn(optionalDeliveryMetaForHaccp)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(any());
    boolean ismanageroverride_fordelivery_butnot_for_thispo =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number, "0550598141", 1, APPROVED_HACCP);
    assertFalse(ismanageroverride_fordelivery_butnot_for_thispo);

    boolean ismanageroverride_fordelivery_but_no_po_exists =
        witronDeliveryMetaDataService.isManagerOverride(
            delivery_number, "111111", 1, APPROVED_HACCP);
    assertFalse(ismanageroverride_fordelivery_but_no_po_exists);
    verify(deliveryMetaDataRepository, times(2)).findByDeliveryNumber(any());
  }

  @Test
  public void doManagerOverride_update_document_no_lines() {
    String delivery_number = "172855";
    Integer purchaseReferenceLineNumber = 1;
    String expectedPoLineDetails =
        "{\"documents\":[{\"purchaseReferenceNumber\":\"7971298360\"},{\"purchaseReferenceNumber\":\"7971298361\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreExpiry\":\"true\"}]},{\"purchaseReferenceNumber\":\"7971298362\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreExpiry\":\"true\"},{\"purchaseReferenceLineNumber\":2,\"ignoreExpiry\":\"true\"}]},{\"purchaseReferenceNumber\":\"7971298364\",\"lines\":[{\"purchaseReferenceLineNumber\":1,\"ignoreExpiry\":\"true\",\"ignoreExpiryBy\":\"k0c0e5k\"}]}]}";
    doReturn(optionalDeliveryMeta).when(deliveryMetaDataRepository).findByDeliveryNumber(any());

    final DeliveryMetaData deliveryMetaData_new =
        witronDeliveryMetaDataService.doManagerOverride(
            mgrUserId,
            delivery_number,
            purchaseReferenceNumber4,
            purchaseReferenceLineNumber,
            ReceivingConstants.IGNORE_EXPIRY);
    assertNotNull(deliveryMetaData_new);

    final String expectedPoLineDetails_actual = deliveryMetaData_new.getPoLineDetails();
    Assert.assertEquals(expectedPoLineDetails_actual, expectedPoLineDetails);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(any());
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testGetOverrideInfoLine_Null_deliveryNum() {

    Integer purchaseReferenceLineNumber = 1;

    deliveryMeta.setPoLineDetails(
        getFileAsString(
            "../receiving-test/src/main/resources/json/delivery_metadata_override.json"));
    Optional<DeliveryMetaData> deliveryMeta_approved = Optional.of(deliveryMeta);

    doReturn(deliveryMeta_approved)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(anyString());
    final PurchaseReferenceLineMeta lineMeta =
        witronDeliveryMetaDataService.getPurchaseReferenceLineMeta(
            null, null, purchaseReferenceLineNumber);
    assertNull(lineMeta);
  }

  @Test
  public void testGetOverrideInfoLine_Null_PoNum() {

    Integer purchaseReferenceLineNumber = 1;

    deliveryMeta.setPoLineDetails(
        getFileAsString(
            "../receiving-test/src/main/resources/json/delivery_metadata_override.json"));
    Optional<DeliveryMetaData> deliveryMeta_approved = Optional.of(deliveryMeta);

    doReturn(deliveryMeta_approved)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(anyString());
    final PurchaseReferenceLineMeta lineMeta =
        witronDeliveryMetaDataService.getPurchaseReferenceLineMeta(
            delivery_number, null, purchaseReferenceLineNumber);
    assertNull(lineMeta);
  }

  @Test
  public void testGetOverrideInfoLine_PoNoMatch() {

    Integer purchaseReferenceLineNumber = 1;

    deliveryMeta.setPoLineDetails(
        getFileAsString(
            "../receiving-test/src/main/resources/json/delivery_metadata_override.json"));
    Optional<DeliveryMetaData> deliveryMeta_approved = Optional.of(deliveryMeta);

    doReturn(deliveryMeta_approved)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(anyString());
    final PurchaseReferenceLineMeta lineMeta =
        witronDeliveryMetaDataService.getPurchaseReferenceLineMeta(
            delivery_number, "PoNoMatch123", purchaseReferenceLineNumber);
    assertNull(lineMeta);
  }

  @Test
  public void testGetOverrideInfoLine_ignoreOverageBy_manager() {
    Integer purchaseReferenceLineNumber = 1;

    deliveryMeta.setPoLineDetails(
        getFileAsString(
            "../receiving-test/src/main/resources/json/delivery_metadata_override.json"));
    Optional<DeliveryMetaData> deliveryMeta_approved = Optional.of(deliveryMeta);
    doReturn(deliveryMeta_approved)
        .when(deliveryMetaDataRepository)
        .findByDeliveryNumber(anyString());
    final PurchaseReferenceLineMeta overrideInfoLine =
        witronDeliveryMetaDataService.getPurchaseReferenceLineMeta(
            delivery_number, "4389559606", purchaseReferenceLineNumber);
    assertNotNull(overrideInfoLine);
  }

  @Test
  public void testUpdateAuditInfoInDeliveryMetaData() {
    assertFalse(
        witronDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(new ArrayList<>(), 1, 1L));
  }

  @Test
  public void testGetReceivedQtyFromMetadata() {
    assertEquals(witronDeliveryMetaDataService.getReceivedQtyFromMetadata(1234L, 1L), 0);
  }
}
