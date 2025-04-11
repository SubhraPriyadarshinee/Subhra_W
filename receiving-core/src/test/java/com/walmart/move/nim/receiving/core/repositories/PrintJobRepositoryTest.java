package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PrintJobRepositoryTest extends ReceivingTestBase {

  @Autowired private PrintJobRepository printJobRepository;

  private Set<String> labelIdentifier;
  private Set<String> completedLabelIdentifier;
  private Long id;

  @BeforeMethod
  public void insertDataIntoH2Db() {

    labelIdentifier = new HashSet<>();

    labelIdentifier.add("a328990000000000000106509");
    labelIdentifier.add("a328990000000000000106519");

    completedLabelIdentifier = new HashSet<>();

    completedLabelIdentifier.add("a328990000000000000106519");
    completedLabelIdentifier.add("a328990000000000000106509");

    PrintJob printJob = new PrintJob();
    printJob.setDeliveryNumber(Long.valueOf("21231313"));
    printJob.setInstructionId(Long.valueOf("1036"));
    printJob.setCreateUserId("sysadmin");
    printJob.setLabelIdentifier(labelIdentifier);
    printJob.setCompletedLabelIdentifier(completedLabelIdentifier);

    id = printJobRepository.save(printJob).getId();
  }

  @Test
  public void testGetOperation() {

    Optional<PrintJob> response = printJobRepository.findById(id);

    if (response.isPresent()) {

      PrintJob printJob = response.get();
      assertEquals(printJob.getDeliveryNumber(), Long.valueOf("21231313"));
      assertEquals(printJob.getInstructionId(), Long.valueOf("1036"));
      assertEquals(printJob.getCreateUserId(), "sysadmin");
      assertTrue(printJob.getLabelIdentifier().equals(labelIdentifier));
      assertTrue(printJob.getCompletedLabelIdentifier().equals(completedLabelIdentifier));
    } else {
      assertTrue(false);
    }
  }

  @Test
  public void testUpdateOperation() {

    Optional<PrintJob> response1 = printJobRepository.findById(id);

    Set<String> labels = new HashSet<>();

    labels.add("a328990000000000000106519");
    labels.add("a328990000000000000106507");
    labels.add("a328990000000000000106509");

    if (response1.isPresent()) {

      PrintJob printJob1 = response1.get();
      Set<String> tmp = printJob1.getLabelIdentifier();
      tmp.add("a328990000000000000106507");
      printJob1.setLabelIdentifier(tmp);
      printJobRepository.save(printJob1);

    } else {
      assertTrue(false);
    }

    Optional<PrintJob> response2 = printJobRepository.findById(id);

    if (response2.isPresent()) {

      PrintJob printJob2 = response2.get();
      assertTrue(printJob2.getLabelIdentifier().equals(labels));

    } else {
      assertTrue(false);
    }
  }

  @Test
  public void testDeleteOperation() {

    Optional<PrintJob> response1 = printJobRepository.findById(id);

    if (response1.isPresent()) {
      printJobRepository.deleteById(id);
    } else {
      assertTrue(false);
    }

    Optional<PrintJob> response2 = printJobRepository.findById(id);

    assertFalse(response2.isPresent());
  }
}
