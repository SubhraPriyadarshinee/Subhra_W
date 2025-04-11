package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcLabelServiceImplTest {

  @InjectMocks RdcLabelServiceImpl rdcLabelServiceImpl;
  @Mock AppConfig appConfig;
  @Mock ContainerRepository containerRepository;
  @Spy ContainerService containerService;
  @Mock ContainerPersisterService containerPersisterService;
  @Mock LabelPersisterService labelPersisterService;

  @Autowired private Gson gson;

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

  List<ReprintLabelData> getReprintLabelDataList() {
    List<ReprintLabelData> reprintLabelDataList = new ArrayList<>();
    ReprintLabelData reprintLabelData1 =
        new ReprintLabelData(
            "78347348",
            "dummy Description 1",
            "sysadmin",
            new Date(),
            null,
            null,
            "00059845600456412103");
    ReprintLabelData reprintLabelData2 =
        new ReprintLabelData(
            "78347348", null, "sysadmin", new Date(), null, null, "00059845600456412104");
    ReprintLabelData reprintLabelData3 =
        new ReprintLabelData(
            "78347348", null, "rcvuser", new Date(), "DT", null, "00059845600456412105");
    ReprintLabelData reprintLabelData4 =
        new ReprintLabelData(
            "78347348",
            "dummy Description 1",
            "rcvuser",
            new Date(),
            null,
            null,
            "00059845600456412106");
    reprintLabelDataList.add(reprintLabelData1);
    reprintLabelDataList.add(reprintLabelData2);
    reprintLabelDataList.add(reprintLabelData3);
    reprintLabelDataList.add(reprintLabelData4);
    return reprintLabelDataList;
  }

  @Test
  public void testReprintLabelsByAllUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getDataForPrintingLabelByDeliveryNumber(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(getReprintLabelDataList());

    List<PrintJobResponse> responses = rdcLabelServiceImpl.getLabels(22112211L, "sysadmin", false);
    verify(containerRepository, times(1))
        .getDataForPrintingLabelByDeliveryNumber(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 4);
  }

  @Test
  public void testReprintLabelsBySingleUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            getReprintLabelDataList()
                .stream()
                .filter(temp -> temp.getCreateUser().equalsIgnoreCase("sysAdmin"))
                .collect(Collectors.toList()));

    List<PrintJobResponse> responses = rdcLabelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 2);
  }

  @Test
  public void testReprintLabelsByLimitingRecords_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(4);
    when(containerRepository.getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(getReprintLabelDataList());

    List<PrintJobResponse> responses = rdcLabelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(argumentCaptor.getValue().getPageSize(), 4);
  }

  @Test
  public void testReprintLabelsWhenNoRecordFound_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(new ArrayList<>());

    List<PrintJobResponse> responses = rdcLabelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getDataForPrintingLabelByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 0);
  }
}
