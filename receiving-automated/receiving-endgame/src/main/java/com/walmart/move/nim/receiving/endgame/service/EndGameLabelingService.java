package com.walmart.move.nim.receiving.endgame.service;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.core.advice.*;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.framework.expression.StandardExpressionEvaluator;
import com.walmart.move.nim.receiving.core.framework.expression.TenantPlaceholder;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.LabelUtils;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.model.LabelSummary;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataCustomRepository;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * * Labeling Service implementation for Endgame specific
 *
 * @author sitakant
 */
public class EndGameLabelingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameLabelingService.class);

  @Value("${endgame.label.topic}")
  private String hawkeyeLabelTopic;

  @Autowired private PreLabelDataRepository preLabelDataRepository;
  @Autowired private PreLabelDataCustomRepository preLabelDataCustomRepository;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private IOutboxPublisherService outboxPublisherService;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private ReceivingCounterService receivingCounterService;
  @Autowired private Gson gson;
  @ManagedConfiguration private EndgameManagedConfig endgameManagedConfig;
  @ManagedConfiguration private KafkaConfig kafkaConfig;
  @SecurePublisher private KafkaTemplate securePublisher;

  public EndGameLabelData generateLabel(LabelRequestVO labelRequestVO) {

    persistMetaData(labelRequestVO);

    return EndGameLabelData.builder()
        .defaultTCL(generateDefaultTCL(labelRequestVO.getDeliveryNumber()))
        .defaultDestination(endgameManagedConfig.getEndgameDefaultDestination())
        .trailerCaseLabels(generateTCL(labelRequestVO.getQuantity(), labelRequestVO.getType()))
        .doorNumber(labelRequestVO.getDoor())
        .clientId(EndgameConstants.CLIENT_ID)
        .formatName(EndgameConstants.TCL_LABEL_FORMAT_NAME)
        .deliveryNumber(labelRequestVO.getDeliveryNumber())
        .trailer(labelRequestVO.getTrailerId())
        .user(EndgameConstants.DEFAULT_USER)
        .tclTemplate(LabelUtils.getTCLTemplate(endgameManagedConfig.getPrintableZPLTemplate()))
        .labelGenMode(labelRequestVO.getLabelGenMode().getMode())
        .type(labelRequestVO.getType())
        .build();
  }

  public String generateDefaultTCL(String deliveryNumber) {
    return EndgameConstants.ENDGAME_LABEL_PREFIX
        + (char) EndgameConstants.DEFAULT_TCL_CHAR_ASCII
        + deliveryNumber;
  }

  private DeliveryMetaData persistMetaData(LabelRequestVO labelRequestVO) {
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(labelRequestVO.getDeliveryNumber()))
            .orElse(null);

    if (deliveryMetaData == null) {
      deliveryMetaData =
          DeliveryMetaData.builder()
              .deliveryNumber(String.valueOf(labelRequestVO.getDeliveryNumber()))
              .totalCaseCount(labelRequestVO.getQuantity())
              .totalCaseLabelSent(labelRequestVO.getQuantity())
              .doorNumber(labelRequestVO.getDoor())
              .trailerNumber(labelRequestVO.getTrailerId())
              .carrierScacCode(labelRequestVO.getCarrierScanCode())
              .billCode(labelRequestVO.getBillCode())
              .carrierName(labelRequestVO.getCarrierName())
              .build();
    } else {
      deliveryMetaData.setTotalCaseLabelSent(
          deliveryMetaData.getTotalCaseLabelSent() + labelRequestVO.getQuantity());
    }
    LOGGER.info(
        "Going to store the meta-data for [deliveryNumber={}]", labelRequestVO.getDeliveryNumber());
    return deliveryMetaDataService.save(deliveryMetaData);
  }

  @Transactional
  @InjectTenantFilter
  public void persistLabel(EndGameLabelData endGameLabelData) {
    List<PreLabelData> preLabelDataList = new ArrayList<>();
    endGameLabelData
        .getTrailerCaseLabels()
        .forEach(
            tcl -> {
              LabelType labelType =
                  Objects.isNull(endGameLabelData.getType())
                      ? LabelType.TCL
                      : endGameLabelData.getType();

              PreLabelData preLabelData =
                  PreLabelData.builder()
                      .status(LabelStatus.GENERATED)
                      .tcl(tcl)
                      .deliveryNumber(Long.valueOf(endGameLabelData.getDeliveryNumber()))
                      .type(labelType)
                      .build();
              preLabelDataList.add(preLabelData);
            });

    preLabelDataRepository.saveAll(preLabelDataList);
  }

  @Timed(name = "HKW-TCL-Upload", level1 = "uwms-receiving", level2 = "HKW-TCL-Upload")
  @ExceptionCounted(
      name = "HKW-TCL-Upload-Exception",
      level1 = "uwms-receiving",
      level2 = "HKW-TCL-Upload-Exception")
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "HKW-TCL-Upload",
      externalCall = true)
  public String send(EndGameLabelData endGameLabelData) {
    String payload = gson.toJson(endGameLabelData);
    Integer facilityNum = TenantContext.getFacilityNum();
    try {
      if (tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(facilityNum)) {
        Map<String, Object> headers =
            EndGameUtils.getHawkeyeHeaderMap(
                hawkeyeLabelTopic,
                endGameLabelData.getDeliveryNumber(),
                facilityNum,
                TenantContext.getFacilityCountryCode(),
                UUID.randomUUID().toString());
        outboxPublisherService.publishToKafka(
            payload,
            headers,
            hawkeyeLabelTopic,
            facilityNum,
            TenantContext.getFacilityCountryCode(),
            endGameLabelData.getDeliveryNumber());
      } else {
        Message<String> message =
            EndGameUtils.setDefaultHawkeyeHeaders(
                payload,
                StandardExpressionEvaluator.EVALUATOR.evaluate(
                    hawkeyeLabelTopic, new TenantPlaceholder(facilityNum)),
                endGameLabelData.getUser(),
                endGameLabelData.getDeliveryNumber());
        securePublisher.send(message);
      }
    } catch (Exception exception) {
      LOGGER.error("Unable to send to hawkeye  [error={}]", ExceptionUtils.getStackTrace(exception));
      return exception.getMessage();
    }
    return EndgameConstants.SUCCESS_MSG;
  }

  @Transactional
  @InjectTenantFilter
  public void updateStatus(
      LabelStatus expectedStatus, LabelStatus status, String reason, Long deliveryNumber) {
    preLabelDataRepository.updateStatus(expectedStatus, status, reason, deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteTCLByDeliveryNumber(long deliveryNumber) {
    preLabelDataRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteDeliveryMetaData(String deliveryNumber) {
    deliveryMetaDataService.deleteByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public Optional<DeliveryMetaData> findDeliveryMetadataByDeliveryNumber(String deliveryNumber) {
    return deliveryMetaDataService.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public Optional<PreLabelData> findByTcl(String tcl) {
    return preLabelDataRepository.findByTcl(tcl);
  }

  @Transactional
  @InjectTenantFilter
  public Optional<PreLabelData> saveOrUpdateLabel(PreLabelData preLabelData) {
    return Optional.of(preLabelDataRepository.save(preLabelData));
  }

  @Transactional
  @InjectTenantFilter
  public long countByStatusInAndDeliveryNumber(List<LabelStatus> labelStatus, long deliveryNumber) {
    return preLabelDataRepository.countByStatusInAndDeliveryNumber(labelStatus, deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<PreLabelData> findTCLsByDeliveryNumber(long deliveryNumber) {
    List<PreLabelData> preLabelData = preLabelDataRepository.findByDeliveryNumber(deliveryNumber);
    if (CollectionUtils.isEmpty(preLabelData)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(EndgameConstants.NO_LABEL_FOUND_ERROR_MSG, deliveryNumber));
    }
    return preLabelData;
  }

  public Set<String> generateTCL(long quantity, LabelType type) {
    ReceivingCounter receivingCounter = receivingCounterService.counterUpdation(quantity, type);

    long endIndex = receivingCounter.getCounterNumber() % EndgameConstants.MAX_TCL;
    Set<String> tcls = new HashSet<>();
    for (long tclNumber = (endIndex - quantity + 1); tclNumber <= endIndex; tclNumber++) {
      tcls.add(receivingCounter.getPrefix() + prependZero(String.valueOf(tclNumber), 8));
    }
    return tcls;
  }

  private String prependZero(String number, int i) {
    if (number.length() == i) {
      return number;
    }
    while (i - number.length() > 0) {
      number = EndgameConstants.ZERO + number;
    }
    return number;
  }

  @Transactional
  @InjectTenantFilter
  public List<PreLabelData> findByDeliveryNumberAndStatus(
      long deliveryNumber, LabelStatus labelStatus) {
    return preLabelDataRepository.findByDeliveryNumberAndStatus(deliveryNumber, labelStatus);
  }

  /**
   * This method will fetch the PreLabelData based on the DeliveryNumber and LabelType. Pagination
   * is done using Pageable interface. If deliveryNumber is not found then Exception will be thrown.
   * If search index is more than the total number of records then exception will be thrown
   *
   * @param deliveryNumber DeliveryNumber
   * @param labelType labelType
   * @param currIndex currentIndex
   * @param pageNum PageNumber
   * @return PreLabelData
   */
  @Transactional
  @InjectTenantFilter
  public Page<PreLabelData> findByDeliveryNumberAndLabelType(
      long deliveryNumber, LabelType labelType, Integer currIndex, Integer pageNum) {
    Pageable paging = PageRequest.of(currIndex - 1, pageNum);

    Page<PreLabelData> preLabelData =
        preLabelDataRepository.findByDeliveryNumberAndType(deliveryNumber, labelType, paging);

    return preLabelData;
  }

  /**
   * This method will fetch the PreLabelData based on the DeliveryNumber. Pagination is done using
   * Pageable interface. If deliveryNumber is not found then Exception will be thrown. If search
   * index is more than the total number of records then exception will be thrown.
   *
   * @param deliveryNumber DeliveryNumber
   * @param currIndex CurrentIndex
   * @param pageSize PageSize
   * @return PreLabelData
   */
  @Transactional
  @InjectTenantFilter
  public Page<PreLabelData> findByDeliveryNumber(
      long deliveryNumber, Integer currIndex, Integer pageSize) {
    Pageable paging = PageRequest.of(currIndex - 1, pageSize);

    Page<PreLabelData> preLabelData =
        preLabelDataRepository.findByDeliveryNumber(deliveryNumber, paging);

    return preLabelData;
  }

  /**
   * This method fetches the TPL and TCL count based on the deliveryNumber and labelType
   *
   * @param deliveryNumber deliveryNumber
   * @param labelType labelType
   * @return List of LabelCount
   */
  @Transactional
  @InjectTenantFilter
  public List<LabelSummary> findLabelSummary(Long deliveryNumber, String labelType) {
    return preLabelDataCustomRepository.findLabelSummary(deliveryNumber, labelType);
  }

  /**
   * This method is persisting the list of PreLabelData
   *
   * @param preLabelDataList list of PreLabelData
   */
  @Transactional
  @InjectTenantFilter
  public List<PreLabelData> saveLabels(List<PreLabelData> preLabelDataList) {
    return preLabelDataRepository.saveAll(preLabelDataList);
  }

  @Transactional
  @InjectTenantFilter
  public List<PreLabelData> findByDeliveryNumberAndCaseUpcAndStatusAndDiverAckEventIsNotNull(
      long deliveryNumber, String caseUpc, LabelStatus labelStatus) {
    return preLabelDataRepository.findByDeliveryNumberAndCaseUpcAndStatusAndDiverAckEventIsNotNull(
        deliveryNumber, caseUpc, labelStatus);
  }
}
