package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.sql.Date;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.time.DateUtils;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ContainerRepositoryTest extends ReceivingTestBase {

  @Autowired private ContainerRepository containerRepository;

  private Container container, container1;
  private Container backedOutContainer;

  private static final int HOURS = 24;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    //		ContainerModel data
    container = new Container();
    container.setTrackingId("a328980000000000000106519");
    container.setInstructionId(Long.valueOf(1));
    container.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32a312b7689");
    container.setInventoryStatus("PICKED");
    container.setLocation("171");
    container.setDeliveryNumber(21119003L);
    Map<String, String> ctrDestination = new HashMap<String, String>();
    ctrDestination.put("countryCode", "US");
    ctrDestination.put("buNumber", "6012");
    container.setFacility(ctrDestination);
    container.setDestination(ctrDestination);
    container.setContainerType("Vendor Pack");
    container.setContainerStatus("");
    container.setWeight(5F);
    container.setWeightUOM("EA");
    container.setCube(2F);
    container.setCubeUOM("EA");
    container.setCtrShippable(Boolean.TRUE);
    container.setCtrShippable(Boolean.TRUE);
    container.setCompleteTs(new Date(0));
    container.setOrgUnitId("1");
    container.setPublishTs(new java.util.Date());
    container.setCreateUser("sysAdmin");
    container.setLastChangedTs(new Date(0));
    container.setLastChangedUser("sysAdmin");
    container.setContainerItems(null);
    container.setFacilityNum(6020);
    container.setFacilityCountryCode("US");
    container.setContainerStatus("BECKED");
    container.setLastChangedUser("sysAdmin");
    ContainerItem item = new ContainerItem();
    item.setDescription("container");
    item.setTrackingId(container.getTrackingId());
    container.setContainerItems(Arrays.asList(item));

    container = containerRepository.save(container);

    container1 = new Container();
    container1.setTrackingId("a328980000000000000106520");
    container1.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32a312b7689");
    container1.setDeliveryNumber(21119003L);
    container1.setPublishTs(new java.util.Date());
    container1.setCreateUser("sysAdmin");
    container1.setLastChangedUser("sysAdmin");
    container1.setFacilityNum(6020);
    container1.setFacilityCountryCode("US");
    container1.setContainerStatus("BECKED");
    container1.setLastChangedUser("sysAdmin");
    ContainerItem item1 = new ContainerItem();
    item1.setDescription("container1");
    item1.setTrackingId(container1.getTrackingId());
    container1.setContainerItems(Arrays.asList(item1));
    container1.setCreateTs(DateUtils.addMinutes(container.getCreateTs(), 1));
    container1.setCompleteTs(DateUtils.addMinutes(container.getCompleteTs(), 1));

    containerRepository.save(container1);

    backedOutContainer = new Container();
    backedOutContainer.setTrackingId("a328980000000000000106530");
    backedOutContainer.setInstructionId(Long.valueOf(1));
    backedOutContainer.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32a312b7690");
    backedOutContainer.setInventoryStatus("PICKED");
    backedOutContainer.setLocation("171");
    backedOutContainer.setDeliveryNumber(21119003L);
    ctrDestination.put("countryCode", "US");
    ctrDestination.put("buNumber", "6012");
    backedOutContainer.setFacility(ctrDestination);
    backedOutContainer.setDestination(ctrDestination);
    backedOutContainer.setContainerType("Vendor Pack");
    backedOutContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    backedOutContainer.setWeight(5F);
    backedOutContainer.setWeightUOM("EA");
    backedOutContainer.setCube(2F);
    backedOutContainer.setCubeUOM("EA");
    backedOutContainer.setCtrShippable(Boolean.TRUE);
    backedOutContainer.setCtrShippable(Boolean.TRUE);
    backedOutContainer.setCompleteTs(new Date(0));
    backedOutContainer.setOrgUnitId("1");
    backedOutContainer.setPublishTs(null);
    backedOutContainer.setCreateUser("sysAdmin");
    backedOutContainer.setLastChangedTs(new Date(0));
    backedOutContainer.setLastChangedUser("sysAdmin");
    backedOutContainer.setContainerItems(null);

    containerRepository.save(backedOutContainer);

    Container childContainer1 = new Container();
    childContainer1.setTrackingId("a328980000000000000106520");
    childContainer1.setInstructionId(Long.valueOf(1));
    childContainer1.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32a312b7689");
    childContainer1.setParentTrackingId("a328980000000000000106519");
    childContainer1.setInventoryStatus("PICKED");
    childContainer1.setLocation("171");
    childContainer1.setDeliveryNumber(21119003L);
    childContainer1.setFacility(ctrDestination);
    childContainer1.setDestination(ctrDestination);
    childContainer1.setContainerType("Vendor Pack");
    childContainer1.setContainerStatus("");
    childContainer1.setWeight(5F);
    childContainer1.setWeightUOM("EA");
    childContainer1.setCube(5F);
    childContainer1.setCubeUOM("EA");
    childContainer1.setCtrShippable(Boolean.TRUE);
    childContainer1.setCtrReusable(Boolean.TRUE);
    childContainer1.setCompleteTs(new Date(0));
    childContainer1.setOrgUnitId("1");
    childContainer1.setPublishTs(new Date(0));
    childContainer1.setCreateUser("sysAdmin");
    childContainer1.setLastChangedTs(new Date(0));
    childContainer1.setLastChangedUser("sysAdmin");

    // container contents
    List<ContainerItem> contentsLists1 = new ArrayList<ContainerItem>();
    ContainerItem contents1 = new ContainerItem();
    childContainer1.setInstructionId(Long.valueOf(1));
    contents1.setTrackingId("a328980000000000000106520");
    contents1.setPurchaseReferenceNumber("5512670021");
    contents1.setPurchaseReferenceLineNumber(5);
    contents1.setInboundChannelMethod("CROSSU");
    contents1.setOutboundChannelMethod("CROSSU");
    contents1.setTotalPurchaseReferenceQty(100);
    contents1.setPurchaseCompanyId(1);
    contents1.setPoDeptNumber("0092");
    contents1.setDeptNumber(1);
    contents1.setItemNumber(1084445L);
    contents1.setVendorGS128("");
    contents1.setGtin("00049807100025");
    contents1.setVnpkQty(1);
    contents1.setWhpkQty(1);
    contents1.setQuantity(1);
    contents1.setQuantityUOM("EA"); // by default EA
    contents1.setVendorPackCost(25.0);
    contents1.setWhpkSell(5.0);
    contents1.setBaseDivisionCode("WM");
    contents1.setFinancialReportingGroupCode("US");
    contents1.setRotateDate(null);

    childContainer1.setContainerItems(contentsLists1);

    containerRepository.save(childContainer1);

    Container childContainer2 = new Container();
    childContainer2.setTrackingId("a328980000000000000106521");
    childContainer2.setInstructionId(Long.valueOf(1));
    childContainer2.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32a312b7689");
    childContainer2.setParentTrackingId("a328980000000000000106519");
    childContainer2.setInventoryStatus("PICKED");
    childContainer2.setLocation("171");
    childContainer2.setDeliveryNumber(21119003L);
    childContainer2.setFacility(ctrDestination);
    childContainer2.setDestination(ctrDestination);
    childContainer2.setContainerType("Vendor Pack");
    childContainer2.setContainerStatus("");
    childContainer2.setWeight(5F);
    childContainer2.setWeightUOM("EA");
    childContainer2.setCube(6F);
    childContainer2.setCubeUOM("EA");
    childContainer2.setCtrShippable(Boolean.TRUE);
    childContainer2.setCtrShippable(Boolean.TRUE);
    childContainer2.setCompleteTs(new Date(0));
    childContainer2.setOrgUnitId("1");
    childContainer2.setPublishTs(new Date(0));
    childContainer2.setCreateUser("sysAdmin");
    childContainer2.setLastChangedTs(new Date(0));
    childContainer2.setLastChangedUser("sysAdmin");

    // container contents
    List<ContainerItem> contentsLists2 = new ArrayList<ContainerItem>();
    ContainerItem contents2 = new ContainerItem();
    contents2.setTrackingId("a328980000000000000106520");
    contents2.setPurchaseReferenceNumber("5512670021");
    contents2.setPurchaseReferenceLineNumber(5);
    contents2.setInboundChannelMethod("CROSSU");
    contents2.setOutboundChannelMethod("CROSSU");
    contents2.setTotalPurchaseReferenceQty(100);
    contents2.setPurchaseCompanyId(1);
    contents2.setPoDeptNumber("0092");
    contents2.setDeptNumber(1);
    contents2.setItemNumber(1084445L);
    contents2.setVendorGS128("");
    contents2.setGtin("00049807100025");
    contents2.setVnpkQty(1);
    contents2.setWhpkQty(1);
    contents2.setQuantity(1);
    contents2.setQuantityUOM("EA"); // by default EA
    contents2.setVendorPackCost(25.0);
    contents2.setWhpkSell(5.0);
    contents2.setBaseDivisionCode("WM");
    contents2.setFinancialReportingGroupCode("US");
    contents2.setRotateDate(null);

    childContainer2.setContainerItems(contentsLists2);

    containerRepository.save(childContainer2);
  }

  /** This method will test findContainerByTrackId */
  @Test
  public void testFindContainerByTrackId() {
    Container resultContainer = containerRepository.findByTrackingId("a328980000000000000106519");
    assertEquals(resultContainer.getMessageId(), container.getMessageId());
    assertEquals(resultContainer.getInventoryStatus(), container.getInventoryStatus());
    assertEquals(resultContainer.getLocation(), container.getLocation());
    assertEquals(resultContainer.getInstructionId(), container.getInstructionId());
    assertEquals(resultContainer.getDeliveryNumber(), container.getDeliveryNumber());
    assertEquals(resultContainer.getFacility(), container.getFacility());
    assertEquals(resultContainer.getDestination(), container.getDestination());
    assertEquals(resultContainer.getWeight(), container.getWeight());
    assertEquals(resultContainer.getContainerStatus(), container.getContainerStatus());
    assertEquals(resultContainer.getCube(), container.getCube());
    assertEquals(resultContainer.getWeight(), container.getWeight());
    assertEquals(resultContainer.getWeightUOM(), container.getWeightUOM());
    assertEquals(resultContainer.getCubeUOM(), container.getCubeUOM());
    assertEquals(resultContainer.getCtrShippable(), container.getCtrShippable());
    assertTrue(resultContainer.getCreateTs() != null);
    assertEquals(resultContainer.getCreateUser(), container.getCreateUser());
    assertNotNull(resultContainer.getLastChangedTs());
    assertEquals(resultContainer.getLastChangedUser(), container.getLastChangedUser());
    assertTrue(resultContainer.getPublishTs() != null);
    assertEquals(resultContainer.getOrgUnitId(), container.getOrgUnitId());
    assertNotNull(resultContainer.getCompleteTs());
  }

  /** This method will test FindAllByParentContainerTrackId(). */
  @Test
  public void testFindAllByParentContainerTrackId() {
    Set<Container> resultContainers =
        containerRepository.findAllByParentTrackingId("a328980000000000000106519");

    resultContainers.forEach(
        resultContainer -> {
          assertNotNull(resultContainer.getMessageId());
          assertNotNull(resultContainer.getInventoryStatus());
          assertNotNull(resultContainer.getLocation());
          assertNotNull(resultContainer.getInstructionId());
          assertNotNull(resultContainer.getDeliveryNumber());
          assertNotNull(resultContainer.getFacility());
          assertNotNull(resultContainer.getDestination());
          assertNotNull(resultContainer.getWeight());
          assertNotNull(resultContainer.getContainerStatus());
          assertNotNull(resultContainer.getCube());
          assertNotNull(resultContainer.getWeight());
          assertNotNull(resultContainer.getWeightUOM());
          assertNotNull(resultContainer.getCubeUOM());
          assertNotNull(resultContainer.getCtrShippable());
          assertNotNull(resultContainer.getCreateTs());
          assertNotNull(resultContainer.getCreateTs());
          assertNotNull(resultContainer.getLastChangedTs());
          assertNotNull(resultContainer.getLastChangedUser());
          assertNotNull(resultContainer.getPublishTs());
          assertNotNull(resultContainer.getOrgUnitId());
          assertNotNull(resultContainer.getCompleteTs());
        });
  }

  /** Test case for report's count labels printed */
  public void testCountAllLabelsPrinted() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -HOURS);
    java.util.Date afterDate = cal.getTime();
    Integer numberOfLabelsPrinted =
        containerRepository.countByCreateTsAfterAndCreateTsBefore(
            afterDate, Calendar.getInstance().getTime());

    assertEquals(numberOfLabelsPrinted.intValue(), 4);
  }

  /** Test case for report's count labels VTRed */
  @Test
  public void testCountAllLabelsVTRed() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -HOURS);
    java.util.Date fromDate = cal.getTime();
    Integer numberOfLabelsPrinted =
        containerRepository.countByContainerStatusAndCreateTsBetween(
            ReceivingConstants.STATUS_BACKOUT, fromDate, Calendar.getInstance().getTime());

    assertEquals(numberOfLabelsPrinted.intValue(), 1);
  }

  @Test
  public void testGetInstructionIdsByTrackingIds_Exists() {
    List<Long> instructionIds =
        containerRepository.getInstructionIdsByTrackingIds(
            Arrays.asList("a328980000000000000106519"), 6020, "US");
    assertEquals(instructionIds.size(), 1);
  }

  @Test
  public void testGetInstructionIdsByTrackingIds_NotExists() {
    List<Long> instructionIds =
        containerRepository.getInstructionIdsByTrackingIds(
            Arrays.asList("a328980000000000000106519"), 6040, "US");
    assertEquals(instructionIds.size(), 0);
  }

  @Test
  public void testGetDataForPrintingLabelByDeliveryNumberByUserId() {
    List<ReprintLabelData> labelData =
        containerRepository.getDataForPrintingLabelByDeliveryNumberByUserId(
            21119003L, "sysAdmin", null, 6020, "US", "PICKED", Pageable.unpaged());
    assertNotNull(labelData);
    assertEquals(labelData.size(), 2);
    assertTrue(CollectionUtils.isNotEmpty(labelData));
    assertEquals(labelData.get(0).getTrackingId(), container1.getTrackingId());
    assertEquals(labelData.get(1).getTrackingId(), container.getTrackingId());
  }

  @Test
  public void testGetDataForPrintingLabelByDeliveryNumber() {
    List<ReprintLabelData> labelData =
        containerRepository.getDataForPrintingLabelByDeliveryNumber(
            21119003L, null, 6020, "US", "PICKED", Pageable.unpaged());
    assertNotNull(labelData);
    assertEquals(labelData.size(), 2);
    assertTrue(CollectionUtils.isNotEmpty(labelData));
    assertEquals(labelData.get(0).getTrackingId(), container1.getTrackingId());
    assertEquals(labelData.get(1).getTrackingId(), container.getTrackingId());
  }
}
