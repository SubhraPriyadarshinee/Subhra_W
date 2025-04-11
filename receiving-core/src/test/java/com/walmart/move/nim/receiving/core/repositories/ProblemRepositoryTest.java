package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ProblemRepositoryTest extends ReceivingTestBase {

  @Autowired private ProblemRepository problemRepository;

  private Long id;

  @BeforeClass
  public void insertDataIntoH2Db() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setDeliveryNumber(543322l);
    problemLabel.setProblemTagId("55436");
    problemLabel.setIssueId("226");
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setCreateUserId("UTSysadmin");
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());
    id = problemRepository.save(problemLabel).getId();
  }

  @Test
  public void testGetOperation() {

    Optional<ProblemLabel> response = problemRepository.findById(id);

    if (response.isPresent()) {

      ProblemLabel problemLabel = response.get();
      assertEquals(problemLabel.getDeliveryNumber(), Long.valueOf("543322"));
      assertEquals(problemLabel.getProblemTagId(), "55436");
      assertEquals(problemLabel.getCreateUserId(), "UTSysadmin");
    } else {
      assertTrue(false);
    }
  }

  @Test
  public void testUpdateOperation() {

    Optional<ProblemLabel> response = problemRepository.findById(id);

    String issueId = "1232321";

    if (response.isPresent()) {

      ProblemLabel problemLabel = response.get();
      problemLabel.setIssueId(issueId);
      problemRepository.save(problemLabel);

    } else {
      assertTrue(false);
    }

    Optional<ProblemLabel> responseUpdated = problemRepository.findById(id);

    if (responseUpdated.isPresent()) {

      ProblemLabel problemLabel = responseUpdated.get();
      assertTrue(problemLabel.getIssueId().equals(issueId));

    } else {
      assertTrue(false);
    }
  }

  @AfterClass
  public void testDeleteOperation() {

    Optional<ProblemLabel> response = problemRepository.findById(id);

    if (response.isPresent()) {
      problemRepository.deleteById(id);
    } else {
      assertTrue(false);
    }

    Optional<ProblemLabel> idFound = problemRepository.findById(id);

    assertFalse(idFound.isPresent());
  }
}
