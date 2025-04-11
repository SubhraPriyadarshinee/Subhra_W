package com.walmart.move.nim.receiving.core.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.repositories.LabelDataLpnRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelDataLpnServiceTest {
  @InjectMocks private LabelDataLpnService labelDataLpnService;

  @Mock private LabelDataRepository labelDataRepository;
  @Mock private LabelDataLpnRepository labelDataLpnRepository;

  private String lpn;
  private LabelDataLpn labelDataLpn;
  private LabelData labelData;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    lpn = "a329870000000000000000000";
    labelDataLpn = LabelDataLpn.builder().lpn(lpn).labelDataId(1234L).build();
    labelData = LabelData.builder().id(1234L).build();
  }

  @AfterMethod
  private void resetMocks() {
    reset(labelDataRepository);
    reset(labelDataLpnRepository);
  }

  @Test
  public void testFindLabelDataByLpn_HappyPath() {
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));

    Optional<LabelData> labelDataResponse = labelDataLpnService.findLabelDataByLpn(lpn);

    verify(labelDataRepository, times(1)).findById(anyLong());
    verify(labelDataLpnRepository, times(1)).findByLpn(lpn);
    assertTrue(labelDataResponse.isPresent());
  }

  @Test
  public void testFindLabelDataByLpn_LabelDataLpnNotPresent() {
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(null);

    Optional<LabelData> labelDataResponse = labelDataLpnService.findLabelDataByLpn(lpn);

    verify(labelDataRepository, times(0)).findById(anyLong());
    verify(labelDataLpnRepository, times(1)).findByLpn(lpn);
    assertFalse(labelDataResponse.isPresent());
  }

  @Test
  public void testFindLabelDataByLpn_LabelDataNotPresent() {
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.empty());

    Optional<LabelData> labelDataResponse = labelDataLpnService.findLabelDataByLpn(lpn);

    verify(labelDataRepository, times(1)).findById(anyLong());
    verify(labelDataLpnRepository, times(1)).findByLpn(lpn);
    assertFalse(labelDataResponse.isPresent());
  }
}
