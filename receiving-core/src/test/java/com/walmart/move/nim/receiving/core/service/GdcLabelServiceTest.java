package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.mockito.*;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcLabelServiceTest extends ReceivingTestBase {
  @InjectMocks GdcLabelServiceImpl gdcLabelService;
  @Mock AppConfig appConfig;
  @Mock ContainerRepository containerRepository;

  @Spy ContainerService containerService;
  @Mock ContainerPersisterService containerPersisterService;
  @Mock LabelPersisterService labelPersisterService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
    TenantContext.setFacilityCountryCode("US");
    ReflectionTestUtils.setField(containerService, "containerRepository", containerRepository);
  }

  @AfterTest
  @AfterMethod
  public void resetMocks() {
    reset(containerRepository);
    reset(containerPersisterService);
    reset(labelPersisterService);
  }

  List<GdcReprintLabelData> getReprintLabelDataList() {
    List<GdcReprintLabelData> reprintLabelDataList = new ArrayList<>();
    GdcReprintLabelData reprintLabelData1 =
        new GdcReprintLabelData(
            "78347348", "dummy Description 1", "sysadmin", new Date(), null, 1, 1, 1);
    GdcReprintLabelData reprintLabelData2 =
        new GdcReprintLabelData("78347348", null, "sysadmin", new Date(), null, 1, 1, 1);
    GdcReprintLabelData reprintLabelData3 =
        new GdcReprintLabelData("78347348", null, "rcvuser", new Date(), "DT", 1, 1, 1);
    GdcReprintLabelData reprintLabelData4 =
        new GdcReprintLabelData(
            "78347348", "dummy Description 1", "rcvuser", new Date(), null, 1, 1, 1);
    reprintLabelDataList.add(reprintLabelData1);
    reprintLabelDataList.add(reprintLabelData2);
    reprintLabelDataList.add(reprintLabelData3);
    reprintLabelDataList.add(reprintLabelData4);
    return reprintLabelDataList;
  }

  @Test
  public void testReprintLabelsByAllUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getGdcDataForPrintingLabelByDeliveryNumber(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(getReprintLabelDataList());

    List<PrintJobResponse> responses = gdcLabelService.getLabels(22112211L, "sysadmin", false);
    verify(containerRepository, times(1))
        .getGdcDataForPrintingLabelByDeliveryNumber(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 4);
  }

  @Test
  public void testReprintLabelsBySingleUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            getReprintLabelDataList()
                .stream()
                .filter(temp -> temp.getCreateUser().equalsIgnoreCase("sysAdmin"))
                .collect(Collectors.toList()));

    List<PrintJobResponse> responses = gdcLabelService.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 2);
  }

  @Test
  public void testReprintLabelsByLimitingRecords_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(4);
    when(containerRepository.getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(getReprintLabelDataList());

    List<PrintJobResponse> responses = gdcLabelService.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(argumentCaptor.getValue().getPageSize(), 4);
  }

  @Test
  public void testReprintLabelsWhenNoRecordFound_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(new ArrayList<>());

    List<PrintJobResponse> responses = gdcLabelService.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getGdcDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 0);
  }
}
