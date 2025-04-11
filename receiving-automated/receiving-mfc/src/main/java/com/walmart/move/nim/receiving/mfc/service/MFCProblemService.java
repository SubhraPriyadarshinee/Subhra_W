package com.walmart.move.nim.receiving.mfc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class MFCProblemService extends ProblemService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCProblemService.class);

  @InjectTenantFilter
  @Transactional
  public ProblemLabel createProblemByContainer(
      ContainerDTO containerDTO, ProblemType problemType, CreateExceptionResponse response) {
    ProblemLabel problem = new ProblemLabel();
    problem.setProblemTagId(containerDTO.getSsccNumber());
    problem.setDeliveryNumber(
        Long.valueOf(
            containerDTO
                .getContainerMiscInfo()
                .getOrDefault(
                    MFCConstant.ORIGINAL_DELIVERY_NUMBER, containerDTO.getDeliveryNumber())
                .toString()));
    if (Objects.nonNull(response)) {
      problem.setProblemResponse(response.getResponse());
      problem.setIssueId(response.getIssueId());
    } else {
      problem.setProblemResponse(MFCConstant.NA);
      problem.setIssueId(MFCConstant.NA);
    }
    problem.setProblemStatus(
        problemType.getName() + "-" + ProblemResolutionType.UNRESOLVED.getName());
    LOGGER.info("Going to save problem: {}", problem);
    return problemRepository.save(problem);
  }

  @InjectTenantFilter
  @Transactional
  public ProblemLabel getProblemLabel(
      String problemStatus, String problemTagId, Long deliveryNumber) {
    Optional<ProblemLabel> _problemLabel =
        findProblemLabelByProblemStatusAndProblemTagIdAndDeliveryNumber(
            problemStatus, problemTagId, deliveryNumber);
    return _problemLabel.orElse(null);
  }

  @InjectTenantFilter
  @Transactional
  public Set<ProblemLabel> getProblemLabels(
      String problemStatus, Set<String> problemTagIds, Long deliveryNumber) {
    return problemRepository.findProblemLabelByProblemStatusAndProblemTagIdInAndDeliveryNumber(
        problemStatus, problemTagIds, deliveryNumber);
  }

  @InjectTenantFilter
  @Transactional
  public ProblemLabel updateProblem(
      ProblemLabel problemLabel,
      ProblemType problemType,
      ProblemResolutionType problemResolutionType) {
    problemLabel.setProblemStatus(problemType.getName() + "-" + problemResolutionType.getName());
    LOGGER.info("Going to update problem: {}", problemLabel);
    return problemRepository.save(problemLabel);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Optional<ProblemLabel> findProblemLabelByProblemStatusAndProblemTagIdAndDeliveryNumber(
      String problemStatus, String problemTagId, Long deliveryNumber) {
    return problemRepository.findOneByProblemStatusAndProblemTagIdAndDeliveryNumber(
        problemStatus, problemTagId, deliveryNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public ProblemLabel getProblemLabelByProblemTagIdAndDeliveryNumber(
      String problemTagId, Long deliveryNumber) {
    Optional<ProblemLabel> _problemLabel =
        problemRepository.findOneByProblemTagIdAndDeliveryNumber(problemTagId, deliveryNumber);
    return _problemLabel.orElse(null);
  }
}
