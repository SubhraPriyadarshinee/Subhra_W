package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;

public class MockPrintJobData {

  public static List<PrintJob> getPrintJobs(int count, boolean dockTagPrintJob)
      throws InterruptedException {

    List<PrintJob> printJobs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      PrintJob printJob = new PrintJob();

      Set<String> printJobLpnSet = new HashSet<>();
      if (dockTagPrintJob) printJobLpnSet.add(RandomStringUtils.randomAlphanumeric(25));
      else printJobLpnSet.add(RandomStringUtils.randomNumeric(18));

      printJob.setDeliveryNumber(21119003L);
      printJob.setCreateUserId("sysadmin");
      Thread.sleep(1000);
      printJob.setCreateTs(new Date(System.currentTimeMillis()));
      printJob.setInstructionId(1L);

      printJob.setLabelIdentifier(printJobLpnSet);
      printJobs.add(printJob);
    }

    return printJobs;
  }

  public static List<ContainerItem> getContainerItems(List<PrintJob> printJobs) {

    List<ContainerItem> containerItems = new ArrayList<>();
    for (PrintJob printJob : printJobs) {
      ContainerItem containerItem = new ContainerItem();
      containerItem.setTrackingId((String) printJob.getLabelIdentifier().toArray()[0]);
      containerItem.setDescription("Item description");
      containerItem.setQuantity(2);
      containerItem.setQuantityUOM("EA");
      containerItems.add(containerItem);
    }
    return containerItems;
  }
}
