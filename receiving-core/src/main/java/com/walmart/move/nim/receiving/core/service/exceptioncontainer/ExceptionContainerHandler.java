package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExceptionContainerHandler {

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired protected ContainerService containerService;

  public void publishException(Container container) {}

  public void publishExceptionDivertToSorter(
      String lpn, SorterExceptionReason sorterExceptionReason, Date labelDate) {

    tenantSpecificConfigReader
        .getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class)
        .publishException(lpn, sorterExceptionReason, labelDate);
  }
}
