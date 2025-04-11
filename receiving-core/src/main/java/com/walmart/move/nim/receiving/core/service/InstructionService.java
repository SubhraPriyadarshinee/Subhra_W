package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.isRegulatedItemType;
import static com.walmart.move.nim.receiving.core.common.ProblemUtils.getMinimumResolutionQty;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PROBLEM_CONFLICT;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.walmart.atlas.argus.metrics.annotations.CaptureMethodMetric;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.factory.DocumentSelectorProvider;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.validator.routines.checkdigit.EAN13CheckDigit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author g0k0072 Service responsible for providing implementation for instruction related
 *     operations
 */
@Primary
@Service(ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
public class InstructionService {

  private static final Logger log = LoggerFactory.getLogger(InstructionService.class);
  public static final String INSTRUCTION = "instruction";
  public static final String CONTAINER = "container";

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired protected InstructionRepository instructionRepository;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private ReceiptService receiptService;
  @Autowired protected ContainerService containerService;
  @Autowired private DCFinService dcFinService;
  @Autowired private NonNationalPoService nonNationalPoService;
  @Autowired protected Gson gson;
  @Autowired private JmsPublisher jmsPublisher;
  @Autowired protected InstructionUtils instructionUtils;
  @Autowired private ImportsInstructionUtils importsInstructionUtils;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  protected FdeService fdeService;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  protected WitronDeliveryMetaDataService witronDeliveryMetaDataService;

  @Autowired protected PrintJobService printJobService;
  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private DeliveryValidator deliveryValidator;
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired protected MovePublisher movePublisher;
  @Autowired protected DeliveryDocumentHelper deliveryDocumentHelper;
  @Autowired protected ManualInstructionService manualInstructionService;
  @Autowired protected InstructionHelperService instructionHelperService;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired protected RegulatedItemService regulatedItemService;
  @Autowired private ProblemReceivingHelper problemReceivingHelper;
  @Autowired private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Autowired protected InstructionStateValidator instructionStateValidator;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  @Autowired protected GlsRestApiClient glsRestApiClient;

  protected InstructionError instructionError;
  protected GdmError gdmError;
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "itemCategoryRuleSet")
  private RuleSet itemCategoryRuleSet;

  @Autowired private ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  @Autowired private LabelServiceImpl labelServiceImpl;
  @Autowired private DocumentSelectorProvider documentSelectorProvider;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  @Autowired
  private DeliveryService deliveryService;

  @Value("${pub.receipts.topic}")
  public String pubReceiptsTopic;

  public InstructionService() {}

  /**
   * @param instructionRequestString request object containing delivery, upc or po line details
   * @param httpHeaders headers that need to be forwarded
   * @return PO line or instruction
   * @throws ReceivingException
   */
  @CaptureMethodMetric
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestString, InstructionRequest.class);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    final long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
    validateGtin(instructionRequest);
    if (Objects.nonNull(instructionRequest.getIsDSDC())
        && Boolean.TRUE.equals(instructionRequest.getIsDSDC())) {
      instructionRequest.setNonNationPo(ReceivingConstants.DSDC_ACTIVITY_NAME);
      return serveNonNationalInstructionRequest(instructionRequest, httpHeaders);
    } else if (Objects.nonNull(instructionRequest.getIsPOCON())
        && Boolean.TRUE.equals(instructionRequest.getIsPOCON())) {
      instructionRequest.setNonNationPo(ReceivingConstants.POCON_ACTIVITY_NAME);
      return serveNonNationalInstructionRequest(instructionRequest, httpHeaders);
    }

    if (!StringUtils.isEmpty(instructionRequest.getAsnBarcode())) {
      return createInstructionForAsnReceiving(instructionRequest, httpHeaders);
    }

    // Handler for problems flow in Kotlin App
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);

    if (isKotlinEnabled && Objects.nonNull(instructionRequest.getProblemTagId())) {
      return servePtagInstructionRequest(instructionRequest, httpHeaders);
    }

    Instruction instruction = null;
    if (StringUtils.isEmpty(instructionRequest.getUpcNumber())
        && StringUtils.isEmpty(instructionRequest.getSscc())) {
      if (StringUtils.isEmpty(instructionRequest.getUpcNumber())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      } else if (StringUtils.isEmpty(instructionRequest.getSscc())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_SSCC_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            InstructionError.NO_SSCC_ERROR.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            InstructionError.NO_SSCC_ERROR.getErrorCode(),
            InstructionError.NO_SSCC_ERROR.getErrorHeader());
      }
    }

    if (!CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
      // Request contains delivery document.
      // (Assumption: There will be only one document and document line for now
      String purchaseRefType =
          instructionRequest
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseRefType();

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL)) {
        DeliveryDocumentLine deliveryDocumentLine =
            instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
        InstructionUtils.validateItemXBlocked(deliveryDocumentLine);
      }

      if (InstructionUtils.isNationalPO(purchaseRefType)) {
        // This is kept here to ensure that this check is done on request delivery docs
        // UI modifies field in request deliveryDocument, need to check that field before it is
        // refreshed with GDM delivery docs
        boolean isManualOrOVFConveyableLabelsMissingCreateInstrReq =
            instructionUtils.isLabelsMissingFlowManualOVFConvGetAllocationsRequest(
                instructionRequest);

        if (configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(),
            ReceivingConstants.RELOAD_DELIVERY_DOCUMENT_FEATURE_FLAG)) {
          DeliveryDocument deliveryDocument =
              fetchSpecificDeliveryDocument(instructionRequest, httpHeaders);
          // Check if Delivery is in receivable state
          if (appConfig.isCheckDeliveryStatusReceivable())
            InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
          instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
        }

        checkIfLineIsRejected(
            instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));

        regulatedItemService.updateVendorComplianceItem(
            instructionRequest.getRegulatedItemType(),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()
                .toString());

        boolean isOverflowReceivingConveyable =
            instructionRequest.isOverflowReceiving()
                && isDAConveyableFreight(instructionRequest.getDeliveryDocuments());

        // Flag to check if this is labels missing flow (for this case, both 2nd, 3rd API Calls
        // should have this flag as true)
        boolean isManualOrOVFConveyableLabelsMissing =
            instructionUtils.isLabelsMissingFlowManualOVFConv(instructionRequest);

        log.info(
            "InstructionService: Request contains deliveryDocs "
                + "isManualReceiving: {} "
                + "isOverflowReceivingConveyable: {} "
                + "isManualOrOVFConveyableLabelsMissing: {} "
                + "isManualOrOVFConveyableLabelsMissingCreateInstrReq: {} "
                + "regulatedItemType: {} ",
            instructionRequest.isManualReceivingEnabled(),
            isOverflowReceivingConveyable,
            isManualOrOVFConveyableLabelsMissing,
            isManualOrOVFConveyableLabelsMissingCreateInstrReq,
            instructionRequest.getRegulatedItemType());

        if (instructionRequest.isManualReceivingEnabled() || isOverflowReceivingConveyable) {
          if (isKotlinEnabled
              && (isRegulatedItemType(instructionRequest.getRegulatedItemType())
                  || (isManualOrOVFConveyableLabelsMissing
                      && !isManualOrOVFConveyableLabelsMissingCreateInstrReq))) {
            log.info(
                "InstructionService: Manual Receiving or Overflow Receiving: "
                    + "item is either regulatedItemType: {} "
                    + "or it is verification required but labels missing flow. "
                    + "sending delivery document with openQty updated using ManualInstructionService",
                instructionRequest.getRegulatedItemType());
            DeliveryDocumentLine deliveryDocumentLine =
                instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
            deliveryDocumentLine.setLithiumIonVerificationRequired(false);
            deliveryDocumentLine.setLimitedQtyVerificationRequired(false);
            // Send the instruction code back for manual receiving
            return manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
                instructionRequest, httpHeaders);
          } else {
            log.info(
                "InstructionService: Manual Receiving or Overflow Receiving: "
                    + "isManualOrOVFConveyableLabelsMissing: {} "
                    + "isManualOrOVFConveyableLabelsMissingCreateInstrReq: {} "
                    + "entering createManualInstruction() for request: {}",
                isManualOrOVFConveyableLabelsMissing,
                isManualOrOVFConveyableLabelsMissingCreateInstrReq,
                instructionRequest);
            return manualInstructionService.createManualInstruction(
                instructionRequest, httpHeaders);
          }
        }
        if (isKotlinEnabled
            && ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                instructionRequest.getFeatureType())) {
          log.info(
              "InstructionService: AUTO_CASE_RECEIVE Flow "
                  + "regulatedItemType: {} "
                  + "entering createManualInstruction() for request: {}",
              instructionRequest.getRegulatedItemType(),
              instructionRequest);
          return manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
        }
        log.info(
            "InstructionService: UPC Receiving Flow: "
                + "entering createInstructionForUpcReceiving() for request: {}",
            instructionRequest);

        // Override & get existing/new instruction for tenant (Witron, Rx...)
        InstructionResponse upcReceivingInstructionResponse =
            getUpcReceivingInstructionResponse(
                instructionRequest, instructionRequest.getDeliveryDocuments(), httpHeaders);
        return upcReceivingInstructionResponse;
      }

      return getInstructionResponseForPoCon(
          instructionRequest,
          deliveryNumber,
          instructionRequest.getDeliveryDocuments(),
          instructionResponse,
          httpHeaders);
    }

    // Get DeliveryDocument from GDM
    List<DeliveryDocument> deliveryDocuments_gdm =
        fetchDeliveryDocument(instructionRequest, httpHeaders);

    // Check if Delivery is in receivable state
    if (appConfig.isCheckDeliveryStatusReceivable())
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments_gdm.get(0));

    // update delivery docs
    deliveryDocuments_gdm = deliveryDocumentHelper.updateDeliveryDocuments(deliveryDocuments_gdm);
    boolean isPoConEnabled =
        configUtils.isFeatureFlagEnabled(ReceivingConstants.POCON_FEATURE_FLAG, getFacilityNum());

    // Throw error if delivery docs contain x blocked items
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
      InstructionUtils.checkForXBlockedItems(deliveryDocuments_gdm);

    // Filter out cancelled po/pols based on flag
    deliveryDocuments_gdm =
        configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO)
            ? InstructionUtils.filterCancelledPoPoLine(deliveryDocuments_gdm)
            : deliveryDocuments_gdm;

    // Currently receiving is not supporting DSDC receiving through scanning barcode, hence
    // filtering
    // out DSDC PO's
    deliveryDocuments_gdm = InstructionUtils.filteroutDSDCPos(deliveryDocuments_gdm);
    // this feature will enable for imports
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_STORAGE_CHECK_ENABLED)) {
      importsInstructionUtils.storageChecks(httpHeaders, deliveryDocuments_gdm);
    }
    // this check is for imports
    instruction =
        originCountryCodeAndPackChecks(instructionRequest, deliveryDocuments_gdm, instruction);
    if (Objects.nonNull(instruction)) {
      instructionResponse.setInstruction(instruction);
      instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
      return instructionResponse;
    }

    boolean isDaConFreight = isDAConveyableFreight(deliveryDocuments_gdm);
    if (checkForPlaceOnConveyorOrFloorLine(
        deliveryDocuments_gdm,
        instructionRequest,
        instructionResponse,
        isDaConFreight,
        httpHeaders)) {
      return instructionResponse;
    }
    boolean isOverflowReceivingConveyable =
        (Boolean.TRUE.equals(instructionRequest.isOverflowReceiving()) && isDaConFreight);

    if (isSinglePO(deliveryDocuments_gdm)) {
      validateDocumentLineExists(deliveryDocuments_gdm);
      // if scanned item is belongs to Po consolidation.
      String purchaseRefType =
          deliveryDocuments_gdm.get(0).getDeliveryDocumentLines().get(0).getPurchaseRefType();
      if (!InstructionUtils.isNationalPO(purchaseRefType)) {
        if (isPoConEnabled
            && !(instructionRequest.isManualReceivingEnabled()
                || isOverflowReceivingConveyable
                || ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                    instructionRequest.getFeatureType()))) {
          return getInstructionResponseForPoCon(
              instructionRequest,
              deliveryNumber,
              deliveryDocuments_gdm,
              instructionResponse,
              httpHeaders);
        } else {
          instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
        }
      } else if (isSinglePoLine(deliveryDocuments_gdm.get(0))
          && !InstructionUtils.isCancelledPOOrPOLine(deliveryDocuments_gdm.get(0))) {
        // There is only one PO line. So, auto select and fetch instruction
        DeliveryDocumentLine documentLine_gdm =
            deliveryDocuments_gdm.get(0).getDeliveryDocumentLines().get(0);
        checkIfLineIsRejected(documentLine_gdm);

        // Regulated Item Validation
        boolean regulatedItemValidationCheck =
            configUtils.getConfiguredFeatureFlag(
                getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

        if (isKotlinEnabled && regulatedItemValidationCheck) {

          if (!instructionRequest.isVendorComplianceValidated()
              && !CollectionUtils.isEmpty(documentLine_gdm.getTransportationModes())) {
            if (instructionUtils.isVendorComplianceRequired(documentLine_gdm)) {
              instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
              return instructionResponse;
            }
          }
        } else {
          if (regulatedItemService.isVendorComplianceRequired(documentLine_gdm)
              && !(instructionRequest.isManualReceivingEnabled()
                  || isOverflowReceivingConveyable)) {
            instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
            return instructionResponse;
          }
        }
        instructionRequest.setDeliveryDocuments(deliveryDocuments_gdm);
        if (instructionRequest.isManualReceivingEnabled() || isOverflowReceivingConveyable) {
          return manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
              instructionRequest, httpHeaders);
        } else if (ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
            instructionRequest.getFeatureType())) {
          List<DeliveryDocument> deliveryDocumentList =
              InstructionUtils.filterDAConDeliveryDocumentsForManualReceiving(
                  deliveryDocuments_gdm);
          validateEligibilityForManualReceiving(
              deliveryDocumentList, instructionRequest.getFeatureType());
          instructionRequest.setDeliveryDocuments(deliveryDocumentList);
          return manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
        }

        // PByL Receiving flow (w/o delivery docs) comes to this point, creates instruction with
        // scanned upc
        instructionResponse =
            getUpcReceivingInstructionResponse(
                instructionRequest, deliveryDocuments_gdm, httpHeaders);

      } else {
        markRegulatedItemVerificationRequiredForMultiPoPol(isKotlinEnabled, deliveryDocuments_gdm);
        isSingleItemMultiPoPoLine(
            deliveryDocuments_gdm, instructionRequest, instructionResponse, httpHeaders);
        // More than one PO line. So, send the information back and ask for PO line
        // selection
        instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);

        if (isAutoSelectLineOrSetInstructionCode(isKotlinEnabled, instructionResponse)) {
          try {
            return autoSelectLineAndCreateInstruction(
                deliveryDocuments_gdm, instructionRequest, httpHeaders);
          } catch (ReceivingBadDataException exc) {
            if (isKotlinEnabled
                && ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY.equals(exc.getErrorCode())) {
              return getCCOveragePalletInstructionResponse(
                  instructionRequest, deliveryDocuments_gdm);
            } else throw exc;
          }
        }
      }
    } else {
      markRegulatedItemVerificationRequiredForMultiPoPol(isKotlinEnabled, deliveryDocuments_gdm);
      isSingleItemMultiPoPoLine(
          deliveryDocuments_gdm, instructionRequest, instructionResponse, httpHeaders);
      // More than one PO. So, send the information back and ask for PO/PO Line
      // selection
      instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
      // UI will send isPoManualSelectionEnabled field in request for Imports .In This case,response
      // should have all
      // the POs with one auto select Line each with open qty. This response also includes POs
      // without open QTY(all lines) as well.
      if (instructionRequest.isPoManualSelectionEnabled()
          && Objects.isNull(instructionRequest.getDeliveryDocuments())
          && CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
        return ManualPoSelection(deliveryDocuments_gdm, instructionResponse, instructionRequest);
      }
      if (isAutoSelectLineOrSetInstructionCode(isKotlinEnabled, instructionResponse)) {
        try {
          return autoSelectLineAndCreateInstruction(
              deliveryDocuments_gdm, instructionRequest, httpHeaders);
        } catch (ReceivingBadDataException exc) {
          if (isKotlinEnabled
              && ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY.equals(exc.getErrorCode())) {
            return getCCOveragePalletInstructionResponse(instructionRequest, deliveryDocuments_gdm);
          } else throw exc;
        }
      }
    }
    if (instructionRequest.isManualReceivingEnabled() || isOverflowReceivingConveyable) {
      // If the flow reaches to this point. Then it is a multi PO/POL case for manual receiving
      instructionRequest.setDeliveryDocuments(deliveryDocuments_gdm);
      return manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
          instructionRequest, httpHeaders);
    } else if (ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
        instructionRequest.getFeatureType())) {
      return autoSelectLineAndCreateInstruction(
          deliveryDocuments_gdm, instructionRequest, httpHeaders);
    }
    return instructionResponse;
  }

  private void validateGtin(InstructionRequest instructionRequest) throws ReceivingException {
    if (org.apache.commons.lang3.StringUtils.isNotEmpty(instructionRequest.getUpcNumber())
        && configUtils.isFeatureFlagEnabled(ReceivingConstants.ITEM_CATALOG_CHECK_DIGIT_ENABLED)) {
      boolean isValid = checkSumValidateGtin14(instructionRequest.getUpcNumber());
      if (!isValid) {
        log.error(ReceivingConstants.ITEM_CATALOG_CHECK_DIGIT_ERROR_MESSAGE);
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_CASE_UPC,
            ReceivingConstants.ITEM_CATALOG_CHECK_DIGIT_ERROR_MESSAGE);
      }
    }
  }

  private boolean checkSumValidateGtin14(String upcNumber) {
    return EAN13CheckDigit.EAN13_CHECK_DIGIT.isValid(upcNumber);
  }

  /**
   * For multi po pols, in case of manual po selection screen, There is no place regulated item
   * verification fields are populated In auto select line and create instruction, we have logic for
   * this, In this case we run this method for all pols marking fields wherever necessary,
   *
   * @param isKotlinEnabled
   * @param deliveryDocuments_gdm
   */
  private void markRegulatedItemVerificationRequiredForMultiPoPol(
      boolean isKotlinEnabled, List<DeliveryDocument> deliveryDocuments_gdm) {
    if (isKotlinEnabled
        && tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.REGULATED_ITEM_VALIDATION)) {
      // in this case, mark all the documents with verification required flags as needed
      deliveryDocuments_gdm
          .stream()
          .flatMap(po -> po.getDeliveryDocumentLines().stream())
          .forEach(pol -> instructionUtils.isVendorComplianceRequired(pol));
    }
  }

  /**
   * Wrapper method for create upc receiving instruction will return InstructionResponse based on
   * the various conditions (from previous flows that used this) This method overloads the other
   * one, and replaces supplier with a non-dynamic value which is passed in the arguments
   *
   * @param instructionRequest
   * @param deliveryDocumentsForResponse
   * @param httpHeaders
   * @return
   */
  protected InstructionResponse getUpcReceivingInstructionResponse(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> deliveryDocumentsForResponse,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    return getUpcReceivingInstructionResponse(
        instructionRequest, () -> deliveryDocumentsForResponse, httpHeaders);
  }

  /**
   * Wrapper method for create upc receiving instruction will return InstructionResponse based on
   * the various conditions (from previous flows that used this)
   *
   * <p>This method created to get the response delivery docs using a supplier, which is called
   * while setting, instead of passing in the response docs which are non-dynamic, Used to get
   * delivery docs from flows where response delivery docs set from instruction request delivery
   * docs, which get updated in the create upc instruction method with auto selected document
   *
   * @param instructionRequest
   * @param responseDeliveryDocumentSupplier supplier method to get response delivery docs
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  protected InstructionResponse getUpcReceivingInstructionResponse(
      InstructionRequest instructionRequest,
      Supplier<List<DeliveryDocument>> responseDeliveryDocumentSupplier,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    // TODO set resolution qty in ptag flow based on
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    Instruction upcReceivingInstruction = null;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    upcReceivingInstruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    if (isKotlinEnabled) {
      if (isBlank(upcReceivingInstruction.getGtin())) {
        log.info(
            "Setting the GTIN value in the instruction to the scanned UPC value from the request, if GTIN is Null / Empty / Blank ");
        upcReceivingInstruction.setGtin(instructionRequest.getUpcNumber());
      }
      instructionResponse.setDeliveryDocuments(
          getUpdatedDeliveryDocumentsForAllowableOverage(
              upcReceivingInstruction, responseDeliveryDocumentSupplier.get()));
      instructionResponse.setInstruction(upcReceivingInstruction);
      return instructionResponse;
    }

    instructionResponse.setDeliveryDocuments(responseDeliveryDocumentSupplier.get());
    instructionResponse.setInstruction(upcReceivingInstruction);
    return instructionResponse;
  }

  private static boolean isDAConveyableFreight(List<DeliveryDocument> deliveryDocuments) {
    return !deliveryDocuments.isEmpty()
        && !deliveryDocuments.get(0).getDeliveryDocumentLines().isEmpty()
        && Objects.nonNull(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getIsConveyable())
        && Objects.nonNull(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseRefType())
        && InstructionUtils.isDAConFreight(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getIsConveyable(),
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseRefType(),
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getActiveChannelMethods());
  }

  private InstructionResponse getCCOveragePalletInstructionResponse(
      InstructionRequest instructionRequest, List<DeliveryDocument> deliveryDocuments_gdm) {
    // If overage encountered in auto select for Multi PO, or Multi PO Line:
    // - create CCOveragePallet instruction
    // - populate the delivery documents in response with first PO from GDM with PO Lines having
    // first PO Line
    // - all PO Lines from deliveryDocuments_gdm will have totalReceivedQty, openQty to render in UI
    InstructionResponse overageAlertResponse = new InstructionResponseImplNew();
    Instruction overageAlertInstruction =
        InstructionUtils.getCCOverageAlertInstruction(instructionRequest, deliveryDocuments_gdm);
    overageAlertInstruction.setDeliveryDocument(null);
    List<DeliveryDocument> responseDeliveryDocuments = Arrays.asList(deliveryDocuments_gdm.get(0));
    DeliveryDocumentLine responseDeliveryDocumentLine =
        deliveryDocuments_gdm.get(0).getDeliveryDocumentLines().get(0);
    responseDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(
        ReceivingUtils.isImportPoLineFbqEnabled(
                responseDeliveryDocuments.get(0).getImportInd(), configUtils)
            ? Boolean.FALSE
            : Boolean.TRUE);
    responseDeliveryDocuments
        .get(0)
        .setDeliveryDocumentLines(Collections.singletonList(responseDeliveryDocumentLine));
    overageAlertResponse.setDeliveryDocuments(responseDeliveryDocuments);
    overageAlertResponse.setInstruction(overageAlertInstruction);
    return overageAlertResponse;
  }

  /**
   * Updates fields in response delivery documents (from gdm) with information from delivery doc
   * present in instruction table (received from client in request).
   *
   * @param instruction
   * @param deliveryDocuments4mGdm
   * @return
   */
  protected List<DeliveryDocument> getUpdatedDeliveryDocumentsForAllowableOverage(
      Instruction instruction, List<DeliveryDocument> deliveryDocuments4mGdm) {
    if (!ListUtils.emptyIfNull(deliveryDocuments4mGdm).isEmpty()) {
      // Set the delivery document line selected in response for further calls
      DeliveryDocumentLine documentLineGdm =
          deliveryDocuments4mGdm.get(0).getDeliveryDocumentLines().get(0);
      DeliveryDocument deliveryDocument4mInstruction =
          gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine documentLine4mInstruction =
          deliveryDocument4mInstruction.getDeliveryDocumentLines().get(0);

      documentLineGdm.setTotalReceivedQty(documentLine4mInstruction.getTotalReceivedQty());
      Integer totalReceivedQty = documentLineGdm.getTotalReceivedQty();
      Integer totalOrderQty =
          ReceivingUtils.computeEffectiveTotalQty(
              documentLineGdm, deliveryDocument4mInstruction.getImportInd(), configUtils);
      if (totalReceivedQty >= totalOrderQty) {
        // in case of allowable overage exceeded, with current receipts, set max allowed included
        // flag
        String purchaseReferenceNumber = documentLineGdm.getPurchaseReferenceNumber();
        int purchaseReferenceLineNumber = documentLineGdm.getPurchaseReferenceLineNumber();
        log.info(
            "Received all of the order quantity for PO:{} and POL:{}, setting MaxAllowedOverageQtyIncluded flag to true",
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
        documentLineGdm.setMaxAllowedOverageQtyIncluded(
            ReceivingUtils.isImportPoLineFbqEnabled(
                    deliveryDocument4mInstruction.getImportInd(), configUtils)
                ? Boolean.FALSE
                : Boolean.TRUE);
      }
      if (ReportingConstants.CC_OVERAGE_PALLET.equalsIgnoreCase(instruction.getInstructionCode())) {
        // in case of CC_OVERAGE_PALLET instruction created in upc receiving flow,
        instruction.setDeliveryDocument(null);
      }
    }
    // need to set delivery documents from GDM/ selected delivery documents into response
    return deliveryDocuments4mGdm;
  }

  /**
   * Auto selection of line for multi PO/Line if auto Selection Enabled else update Instruction
   * MANUAL_PO_SELECTION;
   *
   * @param isKotlinEnabled
   * @param instructionResponseCurrent
   * @return
   */
  protected boolean isAutoSelectLineOrSetInstructionCode(
      boolean isKotlinEnabled, InstructionResponse instructionResponseCurrent) {
    if (!isKotlinEnabled) {
      return false;
    }
    if (!configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.IS_AUTO_SELECT_LINE_DISABLED, false)) {
      return true;
    } else {
      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(),
          ReceivingConstants.IS_MANUAL_PO_SELECTION_CODE_ENABLED,
          false)) {
        if (isNull(instructionResponseCurrent.getInstruction())) {
          final Instruction instructionNew = new Instruction();
          instructionNew.setInstructionCode(ReceivingConstants.MANUAL_PO_SELECTION);
          instructionResponseCurrent.setInstruction(instructionNew);
        } else {
          instructionResponseCurrent
              .getInstruction()
              .setInstructionCode(ReceivingConstants.MANUAL_PO_SELECTION);
        }
      }
    }
    return false;
  }

  protected boolean isSingleSku(List<DeliveryDocumentLine> deliveryDocumentLines) {
    Map<Long, List<DeliveryDocumentLine>> distinctItems =
        deliveryDocumentLines.stream().collect(groupingBy(DeliveryDocumentLine::getItemNbr));
    return (distinctItems.size() == 1);
  }

  protected void isSingleItemMultiPoPoLine(
      List<DeliveryDocument> deliveryDocuments4mGDM,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE)) {
      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      deliveryDocuments4mGDM.forEach(
          deliveryDocument ->
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .forEach(documentLine -> deliveryDocumentLines.add(documentLine)));
      if (isSingleSku(deliveryDocumentLines)) {
        instructionRequest.setDeliveryDocuments(deliveryDocuments4mGDM);
        InstructionResponse upcInstructionResponse =
            getUpcReceivingInstructionResponse(
                instructionRequest,
                appConfig.isOverrideServeInstrMethod()
                    ? instructionRequest::getDeliveryDocuments
                    : () -> null,
                httpHeaders);

        instructionResponse.setInstruction(upcInstructionResponse.getInstruction());
        instructionResponse.setDeliveryDocuments(upcInstructionResponse.getDeliveryDocuments());
      } else {
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
      }
    }
  }

  protected boolean checkForPlaceOnConveyorOrFloorLine(
      List<DeliveryDocument> deliveryDocuments4mGDM,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      boolean isDaConFreight,
      HttpHeaders headers)
      throws ReceivingException {
    return false;
  }

  protected InstructionResponse serveNonNationalInstructionRequest(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    if (ReceivingConstants.DSDC_ACTIVITY_NAME.equalsIgnoreCase(
        instructionRequest.getNonNationPo())) {
      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.DSDC_FEATURE_FLAG, getFacilityNum())) {
        instructionError =
            InstructionErrorCode.getErrorValue(ReceivingException.DSDC_FEATURE_FLAGGED_ERROR);
        log.error("DSDC is feature flagged at Facility: " + getFacilityNum());
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(instructionError.getErrorMessage())
                .errorCode(instructionError.getErrorCode())
                .errorHeader(instructionError.getErrorHeader())
                .errorKey(ExceptionCodes.DSDC_FEATURE_FLAGGED_ERROR)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    } else if (ReceivingConstants.POCON_ACTIVITY_NAME.equalsIgnoreCase(
        instructionRequest.getNonNationPo())) {
      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.POCON_FEATURE_FLAG, getFacilityNum())) {
        instructionError =
            InstructionErrorCode.getErrorValue(ReceivingException.POCON_FEATURE_FLAGGED_ERROR);
        log.error("POCON is feature flagged at Facility: " + getFacilityNum());
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(instructionError.getErrorMessage())
                .errorCode(instructionError.getErrorCode())
                .errorHeader(instructionError.getErrorHeader())
                .errorKey(ExceptionCodes.POCON_FEATURE_FLAGGED_ERROR)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }
    return nonNationalPoService.serveNonNationalPoRequest(instructionRequest, httpHeaders);
  }

  /**
   * This method method will check line is rejected or not.
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public static void checkIfLineIsRejected(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    if (InstructionUtils.isRejectedPOLine(deliveryDocumentLine)) {
      GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_LINE_REJECTION_ERROR);

      String errorMessage =
          String.format(
              gdmError.getErrorMessage(), deliveryDocumentLine.getPurchaseReferenceLineNumber());
      throw new ReceivingException(
          errorMessage,
          HttpStatus.INTERNAL_SERVER_ERROR,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
  }

  /**
   * This method is used to fetch delivery document from GDM
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        configUtils.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments_gdm =
        deliveryDocumentsSearchHandler.fetchDeliveryDocument(instructionRequest, httpHeaders);
    validateDocument(deliveryDocuments_gdm);
    return deliveryDocuments_gdm;
  }

  protected void validateDocument(List<DeliveryDocument> deliveryDocuments)
      throws ReceivingException {
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      log.error(gdmError.getErrorMessage());
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
    deliveryDocuments.forEach(
        deliveryDocument -> deliveryValidator.validateDeliveryStatus(deliveryDocument));
  }

  public void validateDocumentLineExists(List<DeliveryDocument> deliveryDocuments)
      throws ReceivingException {
    if (CollectionUtils.isEmpty(deliveryDocuments.get(0).getDeliveryDocumentLines())) {

      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      log.error(gdmError.getErrorMessage());

      throw new ReceivingException(
          gdmError.getErrorMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
  }

  /**
   * This method is used to get instructionresponse which includes delivery documents and
   * instruction in case of Po Con
   *
   * @param instructionRequest
   * @param deliveryNumber
   * @param deliveryDocuments
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  protected InstructionResponse getInstructionResponseForPoCon(
      InstructionRequest instructionRequest,
      long deliveryNumber,
      List<DeliveryDocument> deliveryDocuments,
      InstructionResponse instructionResponse,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    Instruction instruction;
    List<DeliveryDocument> deliveryDocumentsForPoCon =
        getCompletePoForPoCon(deliveryNumber, deliveryDocuments, httpHeaders);

    DeliveryDocumentLine deliveryDocumentLineForPoCon =
        deliveryDocumentsForPoCon.get(0).getDeliveryDocumentLines().get(0);

    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL)) {
      InstructionUtils.validateItemXBlocked(deliveryDocumentLineForPoCon);
    }

    if (isKotlinEnabled) {
      Long totalReceivedQty =
          receiptService.getReceivedQtyByPoAndDeliveryNumber(
              deliveryDocumentsForPoCon.get(0).getPurchaseReferenceNumber(),
              Long.valueOf(instructionRequest.getDeliveryNumber()));
      if (Objects.isNull(deliveryDocumentLineForPoCon.getOpenQty())) {
        log.info("Setting the openQty value in the deliveryDocumentLines, if openQty is Null");
        deliveryDocumentLineForPoCon.setOpenQty(
            (deliveryDocumentLineForPoCon.getTotalOrderQty() - Math.toIntExact(totalReceivedQty)));
      }
      if (Objects.isNull(deliveryDocumentLineForPoCon.getTotalReceivedQty())) {
        log.info(
            "Setting the totalReceivedQty value in the deliveryDocumentLines, if totalReceivedQty is Null");
        deliveryDocumentLineForPoCon.setTotalReceivedQty(Math.toIntExact(totalReceivedQty));
      }
    }
    instructionResponse.setDeliveryDocuments(deliveryDocumentsForPoCon);
    if (InstructionUtils.cancelPOPOLCheckNotRequired(
            deliveryDocumentsForPoCon.get(0), appConfig.isAllowPOCONRcvOnCancelledPOPOL())
        || !POStatus.CNCL
            .name()
            .equalsIgnoreCase(deliveryDocumentsForPoCon.get(0).getPurchaseReferenceStatus())) {
      instructionRequest.setDeliveryDocuments(deliveryDocumentsForPoCon);
      instruction = createInstructionForPoConReceiving(instructionRequest, httpHeaders);
      if (isKotlinEnabled && isBlank(instruction.getGtin())) {
        log.info(
            "Setting the GTIN value in the instruction to the scanned UPC value from the request, if GTIN is Null / Empty / Blank ");
        instruction.setGtin(instructionRequest.getUpcNumber());
      }
      instructionResponse.setInstruction(instruction);
    }
    return instructionResponse;
  }

  /**
   * This method is used to get complete polines for PO in case of PoCon
   *
   * @param httpHeaders
   * @param deliveryNumber
   * @param deliveryDocuments
   * @return
   * @throws ReceivingException
   */
  private List<DeliveryDocument> getCompletePoForPoCon(
      long deliveryNumber, List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders)
      throws ReceivingException {
    String deliveryDocumentResponseString;
    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    deliveryDocumentResponseString =
        deliveryService.getPOInfoFromDelivery(
            deliveryNumber, deliveryDocuments.get(0).getPurchaseReferenceNumber(), httpHeaders);
    DeliveryDtls completeDeliveryDocuments =
        gson.fromJson(deliveryDocumentResponseString, DeliveryDtls.class);

    DeliveryDocument purchaseReferenceInfo =
        completeDeliveryDocuments.getDeliveryDocuments().get(0);

    if (Objects.isNull(purchaseReferenceInfo.getCubeQty())
        || Objects.isNull(purchaseReferenceInfo.getCubeUOM())
        || Objects.isNull(purchaseReferenceInfo.getWeight())
        || Objects.isNull(purchaseReferenceInfo.getWeightUOM())) {

      gdmError = GdmErrorCode.getErrorValue(ReceivingException.MISSING_ITEM_INFO_ERROR);
      log.error("CubeQty, weight and UOM informations are mandatory for POCON request");
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }

    enrichDeliveryStatusAndStateReasonCode(
        completeDeliveryDocuments.getDeliveryDocuments(),
        completeDeliveryDocuments.getDeliveryStatus(),
        completeDeliveryDocuments.getStateReasonCodes());
    return completeDeliveryDocuments.getDeliveryDocuments();
  }

  Boolean isMoveDestBuFeatureFlagEnabled(
      Container consolidatedContainer, HttpHeaders httpHeaders, Instruction instruction) {
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED)
        && Objects.nonNull(consolidatedContainer)
        && Objects.nonNull(consolidatedContainer.getDestination())
        && Objects.nonNull(
            consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
      movePublisher.publishMove(
          InstructionUtils.getMoveQuantity(consolidatedContainer),
          consolidatedContainer.getLocation(),
          httpHeaders,
          instruction.getMove(),
          MoveEvent.CREATE.getMoveEvent(),
          Integer.parseInt(
              consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER)));
      return Boolean.TRUE;
    }
    return Boolean.FALSE;
  }

  protected InstructionResponse createInstructionForAsnReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    // Check if the freight has already been received
    Container container =
        containerPersisterService.getContainerDetails(instructionRequest.getAsnBarcode());

    if (container != null && container.getPublishTs() != null) {
      log.error("The container: {} has already been received", instructionRequest.getAsnBarcode());
      throw new ReceivingException(
          ReceivingException.FREIGHT_ALREADY_RCVD,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    }
    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    // Get container details from IDM for a given asnBarcode
    ShipmentResponseData shipmentResponseData =
        deliveryService.getContainerInfoByAsnBarcode(
            instructionRequest.getAsnBarcode(), httpHeaders);
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.populateCreateContainerRequest(
            instructionRequest, shipmentResponseData, httpHeaders);
    // Get instruction from FDE
    String instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);
    // Get container items and total quantity

    List<ContainerItemResponseData> containerItems = new ArrayList<>();
    int totalQuantity = getContainerItemQty(containerItems, shipmentResponseData);

    // Persist instruction response from FDE
    Instruction instruction =
        InstructionUtils.processInstructionResponseForS2S(
            instructionRequest, fdeCreateContainerResponse, totalQuantity, httpHeaders);
    instruction.setPurchaseReferenceNumber(
        containerItems.get(0).getPurchaseOrder().getPurchaseReferenceNumber());
    if (containerItems.get(0).getPurchaseOrder().getPurchaseReferenceLineNumber() != null) {
      instruction.setPurchaseReferenceLineNumber(
          containerItems.get(0).getPurchaseOrder().getPurchaseReferenceLineNumber());
    }
    instruction = instructionPersisterService.saveInstruction(instruction);
    // Publish CREATE instruction message to WFM
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    // Create and publish Containers
    Container consolidatedContainer =
        containerService.createAndPublishContainersForS2S(
            instruction,
            shipmentResponseData.getContainer(),
            getForwardableHeadersWithRequestOriginator(httpHeaders));
    // publish UPDATE instruction to WFM
    instructionHelperService.publishInstruction(
        instruction,
        InstructionUtils.prepareRequestForASNInstructionUpdate(),
        totalQuantity,
        null,
        InstructionStatus.UPDATED,
        httpHeaders);
    // Publish move
    if (instruction.getMove() != null) {
      if (!isMoveDestBuFeatureFlagEnabled(consolidatedContainer, httpHeaders, instruction)) {
        movePublisher.publishMove(
            totalQuantity,
            instructionRequest.getDoorNumber(),
            httpHeaders,
            instruction.getMove(),
            MoveEvent.CREATE.getMoveEvent());
      }
    }

    // Update instruction with receivedQuantity and complete the instruction
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    instruction.setReceivedQuantity(totalQuantity);
    instruction = instructionPersisterService.saveInstruction(instruction);

    // Persisting print job
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instructionRequest.getAsnBarcode());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(),
        instruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    Map<String, Object> printJob = instruction.getContainer().getCtrLabel();
    // publish COMPLETE instruction to WFM
    instructionHelperService.publishInstruction(
        instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);
    // Post receipts to DCFin
    dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);
    if (!configUtils.isPrintingAndroidComponentEnabled()) {

      return new InstructionResponseImplOld(null, null, instruction, Arrays.asList(printJob));
    }
    return new InstructionResponseImplNew(null, null, instruction, printJob);
  }

  private int getContainerItemQty(
      List<ContainerItemResponseData> containerItems, ShipmentResponseData shipmentResponseData) {
    int totalQuantity = 0;
    if (!CollectionUtils.isEmpty(shipmentResponseData.getContainer().getItems())) {
      for (ContainerItemResponseData containerItem :
          shipmentResponseData.getContainer().getItems()) {
        totalQuantity += containerItem.getItemQuantity();
        containerItems.add(containerItem);
      }
    }
    if (!CollectionUtils.isEmpty(shipmentResponseData.getContainer().getContainers())) {
      for (ContainerResponseData childContainer :
          shipmentResponseData.getContainer().getContainers()) {
        if (!CollectionUtils.isEmpty(childContainer.getItems())) {
          for (ContainerItemResponseData childContainerItem : childContainer.getItems()) {
            totalQuantity += childContainerItem.getItemQuantity();
            containerItems.add(childContainerItem);
          }
        }
      }
    }
    return totalQuantity;
  }

  public InstructionResponse createPByLDockTagInstructionResponse(
      InstructionRequest instructionRequest, HttpHeaders headers) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  /**
   * Create instruction for UPC receiving
   *
   * @param instructionRequest instruction request object from client
   * @param httpHeaders http headers received from client
   * @return Instruction
   * @throws ReceivingException
   */
  protected Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    Instruction instruction = null;
    if (isKotlinEnabled) {
      instruction =
          instructionPersisterService.fetchExistingOpenInstruction(
              deliveryDocument, instructionRequest, httpHeaders);
    } else {
      instruction =
          instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
    }
    if (nonNull(instruction)) return instruction;

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            instructionRequest.getProblemTagId(),
            deliveryDocument,
            instructionRequest.getDeliveryNumber(),
            isKotlinEnabled,
            false);

    OpenQtyResult openQtyResult;
    OpenQtyCalculator qtyCalculator =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.OPEN_QTY_CALCULATOR,
            ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR,
            OpenQtyCalculator.class);

    if (!StringUtils.isEmpty(instructionRequest.getProblemTagId())) {
      openQtyResult =
          qtyCalculator.calculate(
              instructionRequest.getProblemTagId(),
              deliveryDocument.getDeliveryNumber(),
              deliveryDocument,
              deliveryDocumentLine);
    } else {
      openQtyResult =
          qtyCalculator.calculate(
              deliveryDocument.getDeliveryNumber(), deliveryDocument, deliveryDocumentLine);
    }
    int openQty = Math.toIntExact(openQtyResult.getOpenQty());
    int maxReceiveQty = Math.toIntExact(openQtyResult.getMaxReceiveQty());
    int totalReceivedQty = openQtyResult.getTotalReceivedQty();
    deliveryDocumentLine.setOpenQty(openQty);

    if (isKotlinEnabled) {
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty);
      if ((StringUtils.isEmpty(instructionRequest.getProblemTagId())
              && totalReceivedQty >= maxReceiveQty)
          || (tenantSpecificConfigReader.isFeatureFlagEnabled(
                  ReceivingConstants.IS_OVERAGE_CHECK_IN_PROBLEM_RECEIVING_ENABLED)
              && !StringUtils.isEmpty(instructionRequest.getProblemTagId())
              && openQty == 0)) {
        return InstructionUtils.getCCOverageAlertInstruction(
            instructionRequest, instructionRequest.getDeliveryDocuments());
      }
    }

    final Long deliveryNumber = deliveryDocument.getDeliveryNumber();
    final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
    final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    instructionPersisterService.checkIfNewInstructionCanBeCreated(
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        deliveryNumber,
        openQtyResult,
        isKotlinEnabled);

    FdeCreateContainerRequest fdeCreateContainerRequest = null;
    if (configUtils.useFbqInCbr(getFacilityNum())) {
      fdeCreateContainerRequest =
          InstructionUtils.populateCreateContainerRequestWithFbq(instructionRequest, httpHeaders);
    } else {
      fdeCreateContainerRequest =
          InstructionUtils.getFdeCreateContainerRequest(
              instructionRequest, deliveryDocument, httpHeaders);
    }

    // Enrich the PalletTi from local DB if it's available.
    if (configUtils.isDeliveryItemOverrideEnabled(getFacilityNum())) {
      Content content = fdeCreateContainerRequest.getContainer().getContents().get(0);
      Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
          deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
              Long.parseLong(fdeCreateContainerRequest.getDeliveryNumber()), content.getItemNbr());

      if (deliveryItemOverrideOptional.isPresent()) {
        DeliveryItemOverride deliveryItemOverride = deliveryItemOverrideOptional.get();
        // Set PalletTie to the payload of fdeCreateContainerRequest
        content.setPalletTie(deliveryItemOverride.getTempPalletTi());
        // Set PalletTie to the deliveryDocument so that it will be persisted
        deliveryDocumentLine.setPalletTie(deliveryItemOverride.getTempPalletTi());
      }
    }

    instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

    if (isKotlinEnabled) {
      instruction.setLastChangeUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.IS_STORAGE_CHECK_ENABLED)
        && deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()
        && importsInstructionUtils.isStorageTypePo(deliveryDocument)) {
      importsInstructionUtils.getPrimeSlot(instructionRequest, deliveryDocument, httpHeaders);
    }
    // call FDE service to get allocations and create instruction
    instruction =
        getInstructionFromFDE(
            instructionRequest, instruction, fdeCreateContainerRequest, httpHeaders);

    // persist instruction to database
    TenantContext.get().setAtlasRcvCompInsSaveStart(System.currentTimeMillis());
    instruction = instructionPersisterService.saveInstruction(instruction);
    TenantContext.get().setAtlasRcvCompInsSaveEnd(System.currentTimeMillis());

    // publish instruction to WFM
    TenantContext.get().setAtlasRcvWfmPubStart(System.currentTimeMillis());
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);
    TenantContext.get().setAtlasRcvWfmPubEnd(System.currentTimeMillis());

    return instruction;
  }

  protected Instruction getInstructionFromFDE(
      InstructionRequest instructionRequest,
      Instruction instruction,
      FdeCreateContainerRequest fdeCreateContainerRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String instructionResponse = "";
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      log.error(
          String.format(
              ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED, receivingException.getMessage()));
      instructionPersisterService.saveInstruction(instruction);
      throw receivingException;
    }

    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);
    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);
    return instruction;
  }

  public DeliveryDocument fetchSpecificDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments =
        fetchDeliveryDocument(instructionRequest, httpHeaders);

    DeliveryDocument deliveryDocumentRequest = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLineRequest =
        deliveryDocumentRequest.getDeliveryDocumentLines().get(0);

    DeliveryDocument deliveryDocument = null;
    DeliveryDocumentLine documentLine = null;
    for (DeliveryDocument po : deliveryDocuments) {
      for (DeliveryDocumentLine poLine : po.getDeliveryDocumentLines()) {
        if (po.getPurchaseReferenceNumber()
                .equalsIgnoreCase(deliveryDocumentRequest.getPurchaseReferenceNumber())
            && poLine.getPurchaseReferenceLineNumber()
                == deliveryDocumentLineRequest.getPurchaseReferenceLineNumber()) {
          deliveryDocument = po;
          documentLine = poLine;

          break;
        }
      }
    }
    if (Objects.nonNull(deliveryDocument) && Objects.nonNull(documentLine)) {
      deliveryDocument.getDeliveryDocumentLines().clear();
      //
      deliveryDocumentHelper.updateDeliveryDocumentLine(documentLine);
      deliveryDocument.getDeliveryDocumentLines().add(documentLine);
      return deliveryDocument;
    }
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_POL_NOT_FOUND_ERROR);
    String errorMessage =
        String.format(gdmError.getErrorMessage(), instructionRequest.getDeliveryNumber());
    throw new ReceivingException(
        errorMessage, HttpStatus.NOT_FOUND, gdmError.getErrorCode(), gdmError.getErrorHeader());
  }

  public Instruction getDockTagInstructionIfExists(InstructionRequest instructionRequest) {
    return instructionPersisterService.getDockTagInstructionIfExists(instructionRequest);
  }

  private DeliveryDocumentLine mapRequestDeliveryDocumentToFetchDeliverydocument(
      DeliveryDocumentLine deliveryDocumentLineRequest, DeliveryDocumentLine documentLine) {
    documentLine.setTotalOrderQty(deliveryDocumentLineRequest.getTotalOrderQty());
    documentLine.setOpenQty(deliveryDocumentLineRequest.getOpenQty());
    documentLine.setOverageQtyLimit(deliveryDocumentLineRequest.getOverageQtyLimit());
    return documentLine;
  }

  /**
   * This method is a most crucial integral part of receiving where we create necessary containers
   * and create receipts from an instruction.
   *
   * @param instructionId instruction id
   * @param instructionUpdateRequestFromClient request from client
   * @param parentTrackingId
   * @param httpHeaders http headers passed
   * @return updated instruction
   * @throws ReceivingException receiving exception
   */
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    UpdateInstructionHandler updateInstructionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.UPDATE_INSTRUCTION_HANDLER_KEY,
            UpdateInstructionHandler.class);

    return updateInstructionHandler.updateInstruction(
        instructionId, instructionUpdateRequestFromClient, parentTrackingId, httpHeaders);
  }

  /**
   * This method is a most crucial integral part of receiving where we create necessary containers
   * and create receipts from an instruction.
   *
   * @param instructionId instruction id
   * @param receiveInstructionRequestString request from client
   * @param httpHeaders http headers passed
   * @return completed instruction
   * @throws ReceivingException receiving exception
   */
  public InstructionResponse receiveInstruction(
      Long instructionId, String receiveInstructionRequestString, HttpHeaders httpHeaders)
      throws ReceivingException {

    ReceiveInstructionHandler receiveInstructionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_INSTRUCTION_HANDLER_KEY,
            ReceiveInstructionHandler.class);

    ReceiveInstructionRequest receiveInstructionRequest =
        gson.fromJson(receiveInstructionRequestString, ReceiveInstructionRequest.class);

    return receiveInstructionHandler.receiveInstruction(
        instructionId, receiveInstructionRequest, httpHeaders);
  }

  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    ReceiveInstructionHandler receiveInstructionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_INSTRUCTION_HANDLER_KEY,
            ReceiveInstructionHandler.class);

    return receiveInstructionHandler.receiveInstruction(receiveInstructionRequest, httpHeaders);
  }

  public Container getConsolidatedContainerAndPublishContainer(
      Container parentContainer, HttpHeaders httpHeaders, boolean putToRetry)
      throws ReceivingException {
    Container consolidatedContainer = containerService.getContainerIncludingChild(parentContainer);
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.PUBLISH_CONTAINER)) {
      publishConsolidatedContainer(consolidatedContainer, httpHeaders, putToRetry);
    }
    return consolidatedContainer;
  }

  public void publishConsolidatedContainer(
      Container consolidatedContainer, HttpHeaders httpHeaders, boolean putToRetry) {
    Map<String, Object> headersToSend = getForwardableHeadersWithRequestOriginator(httpHeaders);
    headersToSend.put(ReceivingConstants.IDEM_POTENCY_KEY, consolidatedContainer.getTrackingId());
    if (httpHeaders.containsKey(ReceivingConstants.IGNORE_INVENTORY))
      headersToSend.put(
          ReceivingConstants.IGNORE_INVENTORY,
          httpHeaders.get(ReceivingConstants.IGNORE_INVENTORY));
    containerService.publishContainer(consolidatedContainer, headersToSend, putToRetry);
  }

  /**
   * This method is responsible for completing the instruction flow. If already instruction is
   * already completed throws a exception with message.
   *
   * <p>It will completes instruction. It will completes container. It will publishes container. It
   * will publishes move. It will publish instruction to wfm.
   *
   * <p>This method will be exposed outside.
   *
   * @param instructionId
   * @param httpHeaders
   * @return InstructionResponse
   * @throws ReceivingException
   */
  @SuppressWarnings("unchecked")
  public InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    Map<String, Object> instructionContainerMap;
    // Getting instruction from DB.
    Instruction instructionFromDB = instructionPersisterService.getInstructionById(instructionId);
    validateInstructionCompleted(instructionFromDB);

    if (ReceivingConstants.DOCK_TAG.equalsIgnoreCase(instructionFromDB.getActivityName())) {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED)
          && instructionHelperService.validateFloorLineDocktag(instructionFromDB)) {
        instructionHelperService.createAndPublishDockTagInfo(
            instructionFromDB, completeInstructionRequest);
      }
      return completeInstructionForDockTag(instructionFromDB, httpHeaders);
    }

    purchaseReferenceValidator.validatePOConfirmation(
        instructionFromDB.getDeliveryNumber().toString(),
        instructionFromDB.getPurchaseReferenceNumber());

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivingUtils.verifyUser(instructionFromDB, userId, RequestType.COMPLETE);
    try {
      // Get the proper implementation of PutawayService based on tenant
      PutawayService putawayService =
          configUtils.getPutawayServiceByFacility(getFacilityNum().toString());

      instructionFromDB.setCompleteUserId(userId);
      instructionFromDB.setCompleteTs(new Date());

      if (ReceivingConstants.POCON_ACTIVITY_NAME.equalsIgnoreCase(
          instructionFromDB.getActivityName())) {
        Receipt receiptFromDb =
            receiptService.findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPalletQtyIsNull(
                instructionFromDB.getDeliveryNumber(),
                instructionFromDB.getPurchaseReferenceNumber());
        instructionContainerMap =
            instructionPersisterService.completeAndCreatePrintJobForPoCon(
                httpHeaders, instructionFromDB, receiptFromDb);
      } else {
        instructionContainerMap =
            instructionPersisterService.completeAndCreatePrintJob(httpHeaders, instructionFromDB);
      }

      Instruction instruction = (Instruction) instructionContainerMap.get(INSTRUCTION);

      Container parentContainer = (Container) instructionContainerMap.get(CONTAINER);
      // Getting consolidated container and publish Container to Receipt topic.

      Container consolidatedContainer =
          getConsolidatedContainerAndPublishContainer(parentContainer, httpHeaders, Boolean.TRUE);

      // Publishing move.
      if (instruction.getMove() != null && !instruction.getMove().isEmpty()) {
        if (!isMoveDestBuFeatureFlagEnabled(consolidatedContainer, httpHeaders, instruction)) {
          movePublisher.publishMove(
              InstructionUtils.getMoveQuantity(consolidatedContainer),
              consolidatedContainer.getLocation(),
              httpHeaders,
              instruction.getMove(),
              MoveEvent.CREATE.getMoveEvent());
        }
      }
      List<Map<String, Object>> oldPrintJob = null;
      Map<String, Object> printJob = null;

      String rotateDate =
          (!CollectionUtils.isEmpty(consolidatedContainer.getContainerItems())
                  && nonNull(consolidatedContainer.getContainerItems().get(0))
                  && nonNull(consolidatedContainer.getContainerItems().get(0).getRotateDate()))
              ? new SimpleDateFormat("MM/dd/yy")
                  .format(consolidatedContainer.getContainerItems().get(0).getRotateDate())
              : "-";
      if (!configUtils.isPrintingAndroidComponentEnabled()) {
        oldPrintJob =
            ReceivingUtils.getOldPrintJobWithAdditionalAttributes(
                instructionFromDB, rotateDate, configUtils);
      } else {
        if (configUtils.isPoConfirmationFlagEnabled(getFacilityNum())) {
          String printerName =
              completeInstructionRequest != null ? completeInstructionRequest.getPrinterName() : "";
          String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(getFacilityNum());

          printJob =
              InstructionUtils.getPrintJobWithWitronAttributes(
                  instructionFromDB, rotateDate, userId, printerName, dcTimeZone);
        } else {
          printJob =
              ReceivingUtils.getPrintJobWithAdditionalAttributes(
                  instructionFromDB, rotateDate, labelServiceImpl, configUtils);
        }
      }
      printJob = ReceivingUtils.getNewPrintJob(printJob, instruction, configUtils);

      // Publishing instruction. Instruction will be published based on feature flag.
      instructionHelperService.publishInstruction(
          instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);

      // Send putaway request message
      gdcPutawayPublisher.publishMessage(
          consolidatedContainer, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

      // Post receipts to DCFin backed by persistence
      DCFinService managedDcFinService =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(getFacilityNum()),
              ReceivingConstants.DC_FIN_SERVICE,
              DCFinService.class);
      managedDcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);

      if (!configUtils.isPrintingAndroidComponentEnabled()) {
        return new InstructionResponseImplOld(null, null, instruction, oldPrintJob);
      }
      return new InstructionResponseImplNew(null, null, instruction, printJob);
    } catch (Exception e) {
      log.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG)
              .errorCode(ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.COMPLETE_INSTRUCTION_ERROR_MSG)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public InstructionResponse completeInstructionForDockTag(
      Instruction instructionFromDb, HttpHeaders httpHeaders) throws ReceivingException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    try {
      instructionFromDb.setCompleteUserId(userId);
      instructionFromDb.setCompleteTs(new Date());

      Map<String, Object> instructionContainerMap =
          instructionPersisterService.completeAndCreatePrintJob(httpHeaders, instructionFromDb);

      Instruction instruction = (Instruction) instructionContainerMap.get(INSTRUCTION);

      Container parentContainer = (Container) instructionContainerMap.get(CONTAINER);

      return publishAndGetInstructionResponse(parentContainer, instruction, httpHeaders);
    } catch (Exception e) {
      log.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG)
              .errorCode(ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.COMPLETE_INSTRUCTION_ERROR_MSG)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public InstructionResponse publishAndGetInstructionResponse(
      Container parentContainer, Instruction instruction, HttpHeaders httpHeaders)
      throws ReceivingException {
    Set<Container> childContainerList = new HashSet<>();
    parentContainer.setChildContainers(childContainerList);

    // Publish to downstream
    if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.DISABLE_DOCK_TAG_CONTAINER_PUBLISH)) {
      exceptionContainerHandlerFactory
          .exceptionContainerHandler(ContainerException.DOCK_TAG)
          .publishException(parentContainer);
    }

    // Publishing move.
    if (instruction.getMove() != null && !instruction.getMove().isEmpty()) {
      if (!isMoveDestBuFeatureFlagEnabled(parentContainer, httpHeaders, instruction)) {
        movePublisher.publishMove(
            1,
            parentContainer.getLocation(),
            httpHeaders,
            instruction.getMove(),
            MoveEvent.CREATE.getMoveEvent());
      }
    }
    List<Map<String, Object>> oldPrintJob = null;
    Map<String, Object> printJob = null;

    String rotateDate =
        (Boolean.TRUE.equals(instruction.getFirstExpiryFirstOut())
                && !Objects.isNull(parentContainer.getContainerItems().get(0).getRotateDate()))
            ? new SimpleDateFormat("MM/dd/yy")
                .format(parentContainer.getContainerItems().get(0).getRotateDate())
            : "-";
    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      oldPrintJob =
          ReceivingUtils.getOldPrintJobWithAdditionalAttributes(
              instruction, rotateDate, configUtils);
    } else {
      printJob =
          ReceivingUtils.getPrintJobWithAdditionalAttributes(
              instruction, rotateDate, labelServiceImpl, configUtils);
    }

    // Publishing instruction. Instruction will be published all the time.
    instructionHelperService.publishInstruction(
        instruction, null, null, parentContainer, InstructionStatus.COMPLETED, httpHeaders);

    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      return new InstructionResponseImplOld(null, null, instruction, oldPrintJob);
    }
    return new InstructionResponseImplNew(null, null, instruction, printJob);
  }

  public void validateInstructionCompleted(Instruction instruction) throws ReceivingException {
    if (instruction.getCompleteTs() != null) {
      String errorMessage =
          instruction.getReceivedQuantity() == 0
              ? String.format(
                  ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED,
                  instruction.getCompleteUserId())
              : String.format(
                  ReceivingException.COMPLETE_INSTRUCTION_ALREADY_COMPLETE,
                  instruction.getCompleteUserId());
      String errorKey =
          instruction.getReceivedQuantity() == 0
              ? ExceptionCodes.COMPLETE_INSTRUCTION_PALLET_CANCELLED
              : ExceptionCodes.COMPLETE_INSTRUCTION_ALREADY_COMPLETE;
      String errorHeader =
          instruction.getReceivedQuantity() == 0
              ? ReceivingException.ERROR_HEADER_PALLET_CANCELLED
              : ReceivingException.ERROR_HEADER_PALLET_COMPLETED;
      log.error(errorMessage);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(errorMessage)
              .errorCode(ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED)
              .errorHeader(errorHeader)
              .errorKey(errorKey)
              .values(new Object[] {instruction.getCompleteUserId()})
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
  }

  private List<Map<String, Object>> getAdditionalParam(Instruction instruction, String rotateDate)
      throws ReceivingException {

    List<Map<String, Object>> additionalAttributeList = new ArrayList<>();
    int qty = instruction.getReceivedQuantity();
    // Prepare new key/value pair for total case quantity received on pallet
    Map<String, Object> quantityMap = new HashMap<>();
    quantityMap.put("key", "QTY");
    quantityMap.put("value", qty);
    additionalAttributeList.add(quantityMap);

    // add expiry date and lot number for MX MCC
    ContainerItem containerItem = null;
    String lotNumber = null;
    Date expiryDate = null;
    String expiryDateString = null;

    if (Objects.nonNull(
        containerPersisterService.getContainerDetails(instruction.getContainer().getTrackingId())))
      containerItem =
          containerPersisterService
              .getContainerDetails(instruction.getContainer().getTrackingId())
              .getContainerItems()
              .get(0);

    if (Objects.nonNull(containerItem)) {
      lotNumber = containerItem.getLotNumber();
      expiryDate = containerItem.getExpiryDate();
    }

    if (Objects.nonNull(expiryDate)) {
      DateFormat dateFormat = new SimpleDateFormat("MM/dd/yy");
      expiryDateString = dateFormat.format(expiryDate);
    }

    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.SHOW_EXPIRY_DATE_PRINT_LABEL)) {
      Map<String, Object> expiryDateMap = new HashMap<>();
      expiryDateMap.put("key", "EXPDATE");
      expiryDateMap.put(
          "value",
          Objects.nonNull(expiryDateString) ? expiryDateString : ReceivingConstants.EMPTY_STRING);
      additionalAttributeList.add(expiryDateMap);
    }

    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.SHOW_LOT_NUMBER_PRINT_LABEL)) {
      Map<String, Object> lotNumberMap = new HashMap<>();
      lotNumberMap.put("key", "LOTNUM");
      lotNumberMap.put(
          "value", Objects.nonNull(lotNumber) ? lotNumber : ReceivingConstants.EMPTY_STRING);
      additionalAttributeList.add(lotNumberMap);
    }

    if (configUtils.isShowRotateDateOnPrintLabelEnabled(getFacilityNum())
        && !StringUtils.isEmpty(rotateDate)) {
      Map<String, Object> rotateDateMap = new HashMap<>();
      rotateDateMap.put("key", "ROTATEDATE");
      rotateDateMap.put("value", rotateDate);
      additionalAttributeList.add(rotateDateMap);
    }

    // if PO's are non-national,then we need add few properties which are required for label
    if (instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.POCON.toString())
        || instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.DSDC.toString())) {
      String originalChannel = instruction.getOriginalChannel();
      if (!Objects.isNull(originalChannel)) {
        Map<String, Object> originalPoType = new HashMap<>();
        originalPoType.put("key", "CHANNELMETHOD");
        originalPoType.put("value", instruction.getOriginalChannel());
        additionalAttributeList.add(originalPoType);
      }
    }
    return additionalAttributeList;
  }

  public Map<String, Object> getPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB, String rotateDate) throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    // if PO's are non-national,then we need add few properties which are required for label
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate));

    printRequest.put("data", labelData);
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);
    return printJob;
  }

  protected List<Map<String, Object>> getOldPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB, String rotateDate) throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printJob.get("labelData");
    // if PO's are non-national,then we need add few properties which are required for label
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate));
    printJob.put("labelData", labelData);
    printJob.put("data", labelData);
    return Arrays.asList(printJob);
  }

  /**
   * This method is responsible for canceling an instruction
   *
   * @param instructionId
   * @param httpHeaders
   * @return Instruction
   * @throws ReceivingException
   */
  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public InstructionSummary cancelInstruction(Long instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
      if (ReceivingConstants.DOCK_TAG.equalsIgnoreCase(instruction.getActivityName())) {
        return cancelInstructionForDockTag(instruction, httpHeaders);
      }
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
      if (isKotlinEnabled) {
        instructionStateValidator.validate(instruction);
      }
      ReceivingUtils.verifyUser(instruction, userId, RequestType.CANCEL);
      // Complete instruction with received quantity as ZERO
      instruction.setReceivedQuantity(0);
      instruction.setCompleteUserId(userId);
      instruction.setCompleteTs(new Date());
      Instruction cancelledInstruction = instructionRepository.save(instruction);

      // Publish the receipt
      if (cancelledInstruction.getContainer() != null) {

        // Prepare the payload to publish receipt with ZERO quantity
        PublishReceiptsCancelInstruction.ContentsData contentsData =
            new PublishReceiptsCancelInstruction.ContentsData(
                cancelledInstruction.getPurchaseReferenceNumber(),
                cancelledInstruction.getPurchaseReferenceLineNumber(),
                0,
                ReceivingConstants.Uom.EACHES);
        PublishReceiptsCancelInstruction receiptsCancelInstruction =
            new PublishReceiptsCancelInstruction();
        receiptsCancelInstruction.setMessageId(cancelledInstruction.getMessageId());
        receiptsCancelInstruction.setTrackingId(
            cancelledInstruction.getContainer().getTrackingId());
        receiptsCancelInstruction.setDeliveryNumber(cancelledInstruction.getDeliveryNumber());
        receiptsCancelInstruction.setContents(Arrays.asList(contentsData));
        receiptsCancelInstruction.setActivityName(cancelledInstruction.getActivityName());
        ReceivingJMSEvent receivingJMSEvent =
            new ReceivingJMSEvent(
                getForwardableHeadersWithRequestOriginator(httpHeaders),
                new GsonBuilder()
                    .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                    .create()
                    .toJson(receiptsCancelInstruction));
        jmsPublisher.publish(pubReceiptsTopic, receivingJMSEvent, Boolean.TRUE);
      }
      return InstructionUtils.convertToInstructionSummary(cancelledInstruction);

    } catch (ReceivingException re) {
      Object errorMessage = re.getErrorResponse().getErrorMessage();
      String errorCode = re.getErrorResponse().getErrorCode();
      String errorHeader = re.getErrorResponse().getErrorHeader();
      String errorKey = re.getErrorResponse().getErrorKey();
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(
                  !StringUtils.isEmpty(errorMessage)
                      ? errorMessage
                      : ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG)
              .errorCode(
                  !StringUtils.isEmpty(errorCode)
                      ? errorCode
                      : ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE)
              .errorHeader(
                  !StringUtils.isEmpty(errorHeader)
                      ? errorHeader
                      : ReceivingException.CANCEL_INSTRUCTION_ERROR_HEADER)
              .errorKey(
                  !StringUtils.isEmpty(errorKey)
                      ? errorKey
                      : ExceptionCodes.CANCEL_INSTRUCTION_ERROR_MSG)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    } catch (Exception exception) {
      log.error("{} {}", ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE, exception);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG)
              .errorCode(ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.CANCEL_INSTRUCTION_ERROR_MSG)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public void autoCancelInstruction(Integer facilityNumber) throws ReceivingException {
    JsonElement autoCancelInstructionMinutes =
        tenantSpecificConfigReader.getCcmConfigValue(
            facilityNumber.toString(), ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES);
    int cancelInstructionMinutes =
        nonNull(autoCancelInstructionMinutes)
            ? autoCancelInstructionMinutes.getAsInt()
            : ReceivingConstants.DEFAULT_AUTO_CANCEL_INSTRUCTION_MINUTES;

    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, -cancelInstructionMinutes);
    Date fromDate = cal.getTime();
    log.info("AutoCancelInstruction: Before {}", fromDate);

    List<Instruction> instructionList =
        instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                fromDate, facilityNumber);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    for (Instruction instruction : instructionList) {
      if (instruction.getLastChangeUserId() != null) {
        httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, instruction.getLastChangeUserId());
      } else {
        httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, instruction.getCreateUserId());
      }
      cancelInstruction(instruction.getId(), httpHeaders);
    }
  }

  /**
   * This method is responsible for canceling a dock tag instruction
   *
   * @param instruction
   * @param httpHeaders
   * @return Instruction
   */
  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public InstructionSummary cancelInstructionForDockTag(
      Instruction instruction, HttpHeaders httpHeaders) {

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    Instruction cancelledInstruction = instructionRepository.save(instruction);

    // complete dock tag by Id
    tenantSpecificConfigReader
        .getConfiguredInstance(
            getFacilityNum().toString(), ReceivingConstants.DOCK_TAG_SERVICE, DockTagService.class)
        .updateDockTagById(
            cancelledInstruction.getDockTagId(), InstructionStatus.COMPLETED, userId);
    return InstructionUtils.convertToInstructionSummary(cancelledInstruction);
  }

  /**
   * Returns true if has open instruction else false
   *
   * @param deliveryNumber
   * @return true/false
   */
  @Transactional
  @InjectTenantFilter
  public boolean hasOpenInstruction(Long deliveryNumber) {
    return instructionRepository
            .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber)
        > 0;
  }

  /**
   * This method will delete all the instruction which are created for integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public void deleteInstructionList(Long deliveryNumber) throws ReceivingException {

    List<Instruction> instructionList = instructionRepository.findByDeliveryNumber(deliveryNumber);
    if (instructionList == null || instructionList.isEmpty()) {
      throw new ReceivingException(
          ReceivingException.NO_INSTRUCTIONS_FOR_DELIVERY, HttpStatus.NOT_FOUND);
    }
    instructionRepository.deleteAll(instructionList);
  }

  /**
   * This method will fetch instruction along with the delivery document used to create instruction
   *
   * @param messageId message id
   * @param httpHeaders http headers
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse getInstructionByMessageId(String messageId, HttpHeaders httpHeaders)
      throws ReceivingException {
    Instruction instruction = instructionRepository.findByMessageId(messageId);
    InstructionResponse instructionResponse = null;
    if (!Objects.isNull(instruction)) {
      DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      List<DeliveryDocument> deliveryDocuments =
          deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
              instruction.getDeliveryNumber(), instruction.getGtin(), httpHeaders);
      deliveryDocuments = deliveryDocumentHelper.updateDeliveryDocuments(deliveryDocuments);
      deliveryDocuments =
          InstructionUtils.filterDeliveryDocumentByPOPOL(
              deliveryDocuments,
              instruction.getPurchaseReferenceNumber(),
              instruction.getPurchaseReferenceLineNumber());
      instructionResponse = new InstructionResponseImplNew();
      instructionResponse.setInstruction(instruction);
      instructionResponse.setDeliveryDocuments(deliveryDocuments);
    } else {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.NO_INSTRUCTIONS_FOR_MESSAGE_ID)
              .errorKey(ExceptionCodes.INSTRUCTIONS_FOR_MESSAGE_ID)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();
    }
    return instructionResponse;
  }

  /**
   * Method that updates instructions with userId's specified in the {@link
   * TransferInstructionRequest} to the userId in the headers.
   *
   * @param transferInstructionRequest
   * @param httpHeaders
   * @return {@link List<Instruction>} A list of all the instructions for a deliveryNumber
   */
  public List<InstructionSummary> transferInstructions(
      TransferInstructionRequest transferInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<Instruction> allOpenInstructionsByDelivery =
        instructionPersisterService
            .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                transferInstructionRequest.getDeliveryNumber());
    List<Instruction> transferredInstructions = new ArrayList<>();

    List<String> userIds = transferInstructionRequest.getUserIds();
    Long deliveryNumber = transferInstructionRequest.getDeliveryNumber();

    for (Instruction instruction : allOpenInstructionsByDelivery) {
      if (needsToBeTransferred(instruction, userIds)) {
        instruction.setLastChangeTs(new Date());
        instruction.setLastChangeUserId(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        transferredInstructions.add(instruction);
      }
    }
    if (CollectionUtils.isEmpty(transferredInstructions)) {
      log.error(ReceivingException.NO_TRANSFERRABLE_INSTRUCTIONS, userIds, deliveryNumber);

      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(
                  String.format(
                      ReceivingException.NO_TRANSFERRABLE_INSTRUCTIONS, userIds, deliveryNumber))
              .errorCode(ReceivingException.TRANSFER_ERROR_CODE)
              .errorHeader(ReceivingException.TRANSFER_ERROR_HEADER)
              .errorKey(ExceptionCodes.NO_TRANSFERRABLE_INSTRUCTIONS)
              .values(new Object[] {userIds, deliveryNumber})
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
    instructionPersisterService.saveAllInstruction(transferredInstructions);
    return InstructionUtils.convertToInstructionSummaryResponseList(
        instructionPersisterService.findByDeliveryNumberAndInstructionCodeIsNotNull(
            deliveryNumber));
  }

  public Instruction getInstructionById(Long instructionId) throws ReceivingException {
    return instructionPersisterService.getInstructionById(instructionId);
  }

  /**
   * A private method to check if an owner of an instruction matches the userIds in the {@link
   * TransferInstructionRequest}
   *
   * @param instruction
   * @param ownersInstructionsToTransfer
   * @return
   */
  private boolean needsToBeTransferred(
      Instruction instruction, List<String> ownersInstructionsToTransfer) {
    String currentOwner =
        !StringUtils.isEmpty(instruction.getLastChangeUserId())
            ? instruction.getLastChangeUserId()
            : instruction.getCreateUserId();
    return ownersInstructionsToTransfer.contains(currentOwner);
  }

  /**
   * Create instruction for receiving in case of PoCon scenario
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  Instruction createInstructionForPoConReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    Instruction instruction;
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    if (isKotlinEnabled) {
      instruction =
          instructionPersisterService.fetchExistingOpenInstruction(
              deliveryDocument, instructionRequest, httpHeaders);
      if (nonNull(instruction)) return instruction;
    } else {
      instruction = instructionRepository.findByMessageId(instructionRequest.getMessageId());
      TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
      if (instruction != null && instruction.getMessageId() != null) {
        return instruction;
      }
    }

    Integer openQty =
        Objects.isNull(deliveryDocument.getTotalPurchaseReferenceQty())
            ? 0
            : deliveryDocument.getTotalPurchaseReferenceQty();
    instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
    if (isKotlinEnabled) {
      if (isBlank(instruction.getGtin())) {
        log.info(
            "Setting the GTIN value in the instruction to the scanned UPC value from the request, if GTIN is Null / Empty / Blank ");
        instruction.setGtin(instructionRequest.getUpcNumber());
      }
    }
    instruction.setOriginalChannel(
        InstructionUtils.getOriginalChannelMethod(
            deliveryDocument.getDeliveryDocumentLines().get(0).getOriginalChannel()));
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.populateCreateContainerRequestForPoCon(
            instructionRequest, openQty, httpHeaders);
    if (isKotlinEnabled) {
      instruction.setLastChangeUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    String instructionResponse;
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      log.error(
          String.format(
              ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED, receivingException.getMessage()));
      instructionPersisterService.saveInstruction(instruction);
      throw receivingException;
    }

    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);
    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);
    instruction = instructionPersisterService.saveInstruction(instruction);
    // sending already received item count
    Long totalReceivedQty =
        receiptService.getReceivedQtyByPoAndDeliveryNumber(
            deliveryDocument.getPurchaseReferenceNumber(),
            Long.valueOf(instructionRequest.getDeliveryNumber()));
    instruction.setReceivedQuantity(totalReceivedQty.intValue());
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);
    return instruction;
  }

  public WFTResponse getInstructionAndContainerDetailsForWFT(
      String trackingId, String instructionId, HttpHeaders headers) throws ReceivingException {
    WFTResponse wftResponse = new WFTResponse();
    Instruction instruction = null;
    Container containerDetails = null;

    if (Objects.isNull(trackingId) && Objects.isNull(instructionId)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.WFT_MANDATORY_FIELD, ReceivingConstants.WFT_MANDATORY_FIELD);
    }
    if (Objects.nonNull(instructionId) && Objects.nonNull(trackingId)) {

      instruction = getInstructionDetails(Long.parseLong(instructionId));
      containerDetails = getContainerDetails(trackingId);
    }
    if (Objects.nonNull(instructionId) && !StringUtils.isEmpty(instructionId)) {

      instruction = getInstructionDetails(Long.parseLong(instructionId));
    }

    if (Objects.nonNull(trackingId) && !StringUtils.isEmpty(trackingId)) {
      containerDetails = getContainerDetails(trackingId);

      if (containerDetails.getInstructionId() != null) {
        instruction = getInstructionDetails(containerDetails.getInstructionId());
      }
    }
    wftResponse.setInstruction(instruction);
    wftResponse.setContainer(containerDetails);

    return wftResponse;
  }

  private Container getContainerDetails(String trackingId) throws ReceivingException {
    Container containerDetails;
    containerDetails = containerPersisterService.getContainerDetails(trackingId);
    if (containerDetails == null) {
      throw new ReceivingException(
          ReceivingException.MATCHING_CONTAINER_NOT_FOUND, HttpStatus.BAD_REQUEST);
    }
    return containerDetails;
  }

  private Instruction getInstructionDetails(Long instructionId) throws ReceivingException {
    Instruction instruction;
    instruction = instructionPersisterService.getInstructionById(instructionId);
    return instruction;
  }

  /**
   * Publish WORKING delivery event if the delivery is currently in OPN status and not having state
   * reason code as PENDING_PROBLEM or PENDING_DOCK_TAG
   *
   * @param instructionResponse instruction response containing delivery document
   * @param httpHeaders http headers
   */
  @CaptureMethodMetric
  public void publishWorkingIfNeeded(
      InstructionResponse instructionResponse, HttpHeaders httpHeaders) {
    TenantContext.get().setAtlasRcvGdmDelStatPubStart(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
      DeliveryDocument deliveryDocument = instructionResponse.getDeliveryDocuments().get(0);
      if (DeliveryStatus.OPN.equals(deliveryDocument.getDeliveryStatus())
          && (CollectionUtils.isEmpty(deliveryDocument.getStateReasonCodes())
              || isWorkingStatusChangeNeededForOpenDeliveryStatus(deliveryDocument))) {
        if (isGdmSyncStatusUpdateEnabledForRcvStarted()) {
          deliveryService.updateDeliveryStatusToWorking(
              deliveryDocument.getDeliveryNumber(), httpHeaders);
        } else {
          deliveryStatusPublisher.publishDeliveryStatus(
              deliveryDocument.getDeliveryNumber(),
              DeliveryStatus.WORKING.toString(),
              null,
              ReceivingUtils.getForwardablHeader(httpHeaders));
        }
        instructionResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
      }
    }
    TenantContext.get().setAtlasRcvGdmDelStatPubEnd(System.currentTimeMillis());
  }

  private boolean isGdmSyncStatusUpdateEnabledForRcvStarted() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(),
        ReceivingConstants.IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP,
        false);
  }

  /**
   * This method determines if Working status change needed for Open Delivery Status. If the CCM
   * config is configured with List of valid State Reason Codes, we will compare the Valid
   * configured list with the given delivery state reason codes. If there's no match found then this
   * delivery is eligible for Working status change. If there's no CCM configs available we can
   * default to the older logic to verify against PENDING_PROBLEM & PENDING_DOCKTAG state reason
   * codes
   *
   * @param deliveryDocument
   * @return
   */
  private boolean isWorkingStatusChangeNeededForOpenDeliveryStatus(
      DeliveryDocument deliveryDocument) {
    boolean isWorkingStatusChangeNeeded = false;
    if (!CollectionUtils.isEmpty(appConfig.getGdmDeliveryStateReasonCodesForOpenStatus())) {
      List<String> matchedStateReasonCodes =
          appConfig
              .getGdmDeliveryStateReasonCodesForOpenStatus()
              .stream()
              .filter(deliveryDocument.getStateReasonCodes()::contains)
              .collect(Collectors.toList());
      isWorkingStatusChangeNeeded = CollectionUtils.isEmpty(matchedStateReasonCodes);
    } else {
      isWorkingStatusChangeNeeded =
          !(deliveryDocument
                  .getStateReasonCodes()
                  .contains(DeliveryReasonCodeState.PENDING_PROBLEM.name())
              || deliveryDocument
                  .getStateReasonCodes()
                  .contains(DeliveryReasonCodeState.PENDING_DOCK_TAG.name()));
    }
    return isWorkingStatusChangeNeeded;
  }

  public void enrichDeliveryStatusAndStateReasonCode(
      List<DeliveryDocument> deliveryDocuments,
      DeliveryStatus deliveryStatus,
      List<String> stateReasonCode) {
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      deliveryDocument.setDeliveryStatus(deliveryStatus);
      deliveryDocument.setStateReasonCodes(stateReasonCode);
    }
  }

  private boolean isInstructionComplete(Instruction instruction) {
    return ObjectUtils.anyNotNull(instruction.getCompleteTs(), instruction.getCompleteUserId());
  }

  public void transferInstructionsMultiple(
      MultipleTransferInstructionsRequestBody multipleTransferInstructionsRequestBody,
      HttpHeaders httpHeaders) {
    if (CollectionUtils.isEmpty(multipleTransferInstructionsRequestBody.getInstructionId())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_INPUT_INSTRUCTION_IDS,
          ReceivingConstants.INVALID_INPUT_INSTRUCTION_IDS);
    }

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    if (isBlank(userId)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_INPUT_USERID, ReceivingConstants.INVALID_INPUT_USERID);
    }

    List<Instruction> instructionList =
        instructionRepository.findByIdIn(
            multipleTransferInstructionsRequestBody.getInstructionId());
    for (Instruction instruction : instructionList) {
      if (isInstructionComplete(instruction)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_INSTRUCTION_STATE, ReceivingConstants.INVALID_INSTRUCTION_STATE);
      }
    }
    instructionHelperService.transferInstructions(
        multipleTransferInstructionsRequestBody.getInstructionId(), userId);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<InstructionSummary> getInstructionSummaryByDeliveryAndInstructionSetId(
      Long deliveryNumber, Long instructionSetId) {
    if (Objects.isNull(instructionSetId)) {
      return InstructionUtils.convertToInstructionSummaryResponseList(
          instructionRepository.findByDeliveryNumber(deliveryNumber));
    } else {
      return InstructionUtils.convertToInstructionSummaryResponseList(
          instructionRepository.findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(
              deliveryNumber, instructionSetId));
    }
  }

  public void cancelInstructionsMultiple(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders headers) {

    CancelMultipleInstructionRequestHandler cancelMultipleInstructionRequestHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.CANCEL_MULTIPLE_INSTR_REQ_HANDLER,
            CancelMultipleInstructionRequestHandler.class);

    cancelMultipleInstructionRequestHandler.cancelInstructions(
        multipleCancelInstructionsRequestBody, headers);
  }

  public CompleteMultipleInstructionResponse bulkCompleteInstructions(
      BulkCompleteInstructionRequest bulkCompleteInstructionRequest, HttpHeaders headers)
      throws ReceivingException {

    CompleteMultipleInstructionRequestHandler completeMultipleInstructionRequestHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.COMPLETE_MULTIPLE_INSTR_REQ_HANDLER,
            CompleteMultipleInstructionRequestHandler.class);

    return completeMultipleInstructionRequestHandler.complete(
        bulkCompleteInstructionRequest, headers);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(Long deliveryNumber) {
    instructionRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  /**
   * @param instructionId
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse refreshInstruction(Long instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info("Enter refreshInstruction with instructionId: {}", instructionId);
    Optional<Instruction> instruction4mDB = instructionRepository.findById(instructionId);
    if (instruction4mDB.isPresent()) {
      RefreshInstructionHandler refreshInstructionHandler =
          tenantSpecificConfigReader.getConfiguredInstance(
              getFacilityNum().toString(),
              ReceivingConstants.REFRESH_INSTRUCTION_HANDLER_KEY,
              RefreshInstructionHandler.class);

      return refreshInstructionHandler.refreshInstruction(instruction4mDB.get(), httpHeaders);
    } else {
      log.error("Instruction does not exists for instructionId: {}", instructionId);
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_NOT_FOUND,
          String.format(ReceivingConstants.INSTRUCTION_NOT_FOUND, instructionId));
    }
  }

  /**
   * Create instruction for PTAG, use PROBLEM table for resolution info
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return InstructionResponse
   * @throws ReceivingException
   */
  protected InstructionResponse servePtagInstructionRequest(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    FitProblemTagResponse fitProblemTagResponse =
        configUtils
            .getConfiguredInstance(
                getFacilityNum().toString(),
                ReceivingConstants.PROBLEM_SERVICE,
                ProblemService.class)
            .getProblemDetails(instructionRequest.getProblemTagId());

    List<DeliveryDocument> gdmDeliveryDocumentList = null;

    InstructionResponse pTagInstructionResponse = new InstructionResponseImplNew();
    if (problemReceivingHelper.isContainerReceivable(fitProblemTagResponse)) {
      final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);

      Resolution resolution = fitProblemTagResponse.getResolutions().get(0);
      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      GdmPOLineResponse gdmPOLineResponse =
          deliveryService.getPOLineInfoFromGDM(
              instructionRequest.getDeliveryNumber(),
              resolution.getResolutionPoNbr(),
              resolution.getResolutionPoLineNbr(),
              httpHeaders);

      gdmDeliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
      gdmDeliveryDocumentList =
          deliveryDocumentHelper.updateDeliveryDocuments(gdmDeliveryDocumentList);

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL)) {
        InstructionUtils.checkForXBlockedItems(gdmDeliveryDocumentList);
      }

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(),
          ReceivingConstants.ITEM_VARIABLE_WEIGHT_CHECK_ENABLED,
          false)) {
        ReceivingUtils.validateVariableWeightForVariableItem(
            gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
      }

      instructionRequest.setResolutionQty(getMinimumResolutionQty(fitProblemTagResponse));

      if (!CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
        regulatedItemService.updateVendorComplianceItem(
            instructionRequest.getRegulatedItemType(),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()
                .toString());

        instructionRequest.setDeliveryDocuments(gdmDeliveryDocumentList);
      } else {
        instructionRequest.setDeliveryDocuments(gdmDeliveryDocumentList);

        boolean regulatedItemValidationCheck =
            configUtils.getConfiguredFeatureFlag(
                getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

        if (isKotlinEnabled && regulatedItemValidationCheck) {
          DeliveryDocumentLine deliveryDocumentLine =
              instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
          if (!instructionRequest.isVendorComplianceValidated()
              && !CollectionUtils.isEmpty(deliveryDocumentLine.getTransportationModes())) {
            if (instructionUtils.isVendorComplianceRequired(deliveryDocumentLine)) {
              pTagInstructionResponse.setDeliveryDocuments(
                  instructionRequest.getDeliveryDocuments());
              return pTagInstructionResponse;
            }
          }
        }
      }
      pTagInstructionResponse =
          getUpcReceivingInstructionResponse(
              instructionRequest, gdmDeliveryDocumentList, httpHeaders);
    } else {
      log.error("ProblemTagId:[{}] is not ready to receive.", instructionRequest.getProblemTagId());
      throw new ReceivingConflictException(
          PROBLEM_CONFLICT, ReceivingException.PTAG_NOT_READY_TO_RECEIVE);
    }
    return pTagInstructionResponse;
  }

  /**
   * @param deliveryDocumentList
   * @param instructionRequest
   * @param httpHeaders
   * @return InstructionResponse
   * @throws ReceivingException
   */
  protected InstructionResponse autoSelectLineAndCreateInstruction(
      List<DeliveryDocument> deliveryDocumentList,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    boolean isOverflowReceivingConveyable =
        (Boolean.TRUE.equals(instructionRequest.isOverflowReceiving())
            && isDAConveyableFreight(deliveryDocumentList));
    /*    If isKotlinEnabled and if its manual receiving, filter out delivery documents based on conveyable item and purchase reference type
    is DACON. Also check for activeChannelMethod. If list has >1 - continue, if list is empty - throw an error */

    if (isKotlinEnabled
        && (instructionRequest.isManualReceivingEnabled()
            || isOverflowReceivingConveyable
            || ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                instructionRequest.getFeatureType()))) {

      if (ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
          instructionRequest.getFeatureType())) {
        poConCheckForAutoCaseReceive(deliveryDocumentList);
      }
      deliveryDocumentList =
          InstructionUtils.filterDAConDeliveryDocumentsForManualReceiving(deliveryDocumentList);
      validateEligibilityForManualReceiving(
          deliveryDocumentList, instructionRequest.getFeatureType());
    }

    DeliveryDocumentSelector documentSelector =
        documentSelectorProvider.getDocumentSelector(deliveryDocumentList);

    // Auto select the line
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        documentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    // Report an overage if all the lines were exhausted
    if (Objects.isNull(selectedLine)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY,
          ReceivingException.AUTO_SELECT_PO_LINE_NO_OPEN_QTY);
    }

    // Set the selected line for create instruction flow
    DeliveryDocument deliveryDocument = selectedLine.getKey();
    DeliveryDocumentLine deliveryDocumentLine = selectedLine.getValue();
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

    // check for fbq quantity at the line level for import po
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK)) {
      InstructionUtils.filterValidDeliveryDocumentsWithLineLevelFbq(deliveryDocument);
    }

    // Regulated Item Validation
    boolean regulatedItemValidationCheck =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    if (isKotlinEnabled && regulatedItemValidationCheck) {

      List<DeliveryDocument> deliveryDocuments_gdm = instructionRequest.getDeliveryDocuments();

      InstructionResponse instructionResponse = new InstructionResponseImplNew();

      if (!instructionRequest.isVendorComplianceValidated()
          && !CollectionUtils.isEmpty(deliveryDocumentLine.getTransportationModes())) {
        if (instructionUtils.isVendorComplianceRequired(deliveryDocumentLine)) {
          instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
          return instructionResponse;
        }
      }
    }

    if (isKotlinEnabled
        && (instructionRequest.isManualReceivingEnabled() || isOverflowReceivingConveyable)) {
      return manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
          instructionRequest, httpHeaders);
    } else if (isKotlinEnabled
        && deliveryDocumentLine
            .getPurchaseRefType()
            .equals(ReceivingConstants.POCON_ACTIVITY_NAME)) {
      log.info(
          "Selected document line is a POCON, calling the POCON flow to return the instruction: {}",
          instructionRequest);
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      return getInstructionResponseForPoCon(
          instructionRequest,
          Long.parseLong(instructionRequest.getDeliveryNumber()),
          instructionRequest.getDeliveryDocuments(),
          instructionResponse,
          httpHeaders);
    } else if (isKotlinEnabled
        && ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
            instructionRequest.getFeatureType())) {
      return manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
    } else {
      // Create an instruction for selected line
      log.info(
          "Calling createInstructionForUpcReceiving() with selected line: {}", instructionRequest);

      // Send instruction response back to the client
      // in this case, delivery documents sent in instruction response will be same as that
      // present in instruction (as auto selection is done)
      InstructionResponse instructionResponse =
          getUpcReceivingInstructionResponse(
              instructionRequest, Arrays.asList(deliveryDocument), httpHeaders);
      return instructionResponse;
    }
  }

  public void poConCheckForAutoCaseReceive(List<DeliveryDocument> deliveryDocumentList)
      throws ReceivingException {
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentList.forEach(
        deliveryDocument ->
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(documentLine -> deliveryDocumentLines.add(documentLine)));
    boolean isPOCONPresent =
        deliveryDocumentLines
            .stream()
            .allMatch(
                deliveryDocumentLine ->
                    deliveryDocumentLine
                        .getPurchaseRefType()
                        .equals(ReceivingConstants.POCON_ACTIVITY_NAME));
    if (isPOCONPresent) {
      GdmError gdmError = GdmErrorCode.getErrorValue(PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE);
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          BAD_REQUEST,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
  }

  public void validateEligibilityForManualReceiving(
      List<DeliveryDocument> deliveryDocumentList, String featureType) throws ReceivingException {
    if (deliveryDocumentList.isEmpty()
        && !ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(featureType)) {
      GdmError gdmError = GdmErrorCode.getErrorValue(ITEM_NOT_CONVEYABLE_ERROR);
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          BAD_REQUEST,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
    if (deliveryDocumentList.isEmpty()
        && ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(featureType)) {
      GdmError gdmError =
          GdmErrorCode.getErrorValue(ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE);
      throw new ReceivingException(
          gdmError.getErrorMessage(),
          BAD_REQUEST,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
  }

  /**
   * This method is mainly used to receive all pallets in for gdc market where we create necessary
   * containers, create receipts and printJob.
   *
   * @param receiveAllRequestString request from client
   * @param httpHeaders http headers passed
   * @return print job
   * @throws ReceivingException receiving exception
   */
  public ReceiveAllResponse receiveAll(String receiveAllRequestString, HttpHeaders httpHeaders)
      throws ReceivingException {
    log.error("No bean configured for facility number: {}", TenantContext.getFacilityNum());
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private Instruction originCountryCodeChecks(
      List<DeliveryDocument> deliveryDocumentsGdm,
      InstructionRequest instructionRequest,
      Boolean isCountryCodeACKEnabled,
      Boolean ispackAckEnabled) {

    Optional<DeliveryItemOverride> deliveryItemOverrideDetails =
        deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            deliveryDocumentsGdm.get(0).getDeliveryNumber(),
            deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr());

    Instruction instruction = null;
    if (!deliveryItemOverrideDetails.isPresent()) {
      log.info(
          "Delivery: {} and Item: {} combination is not present in DELIVERY_ITEM_OVERRIDE table for facilityNum:{} ",
          deliveryDocumentsGdm.get(0).getDeliveryNumber(),
          deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
          getFacilityNum());
      return InstructionUtils.acknowledgePendingInstruction(
          instructionRequest,
          isCountryCodeACKEnabled,
          Boolean.FALSE,
          Boolean.FALSE,
          ispackAckEnabled,
          Boolean.FALSE);
    }
    Map<String, String> itemMiscInfo = deliveryItemOverrideDetails.get().getItemMiscInfo();
    boolean originCountryCodeAcknowledged =
        Boolean.parseBoolean(itemMiscInfo.get(ReceivingConstants.IS_OCC_ACK));
    boolean originCountryCodeAcknowledgedCoditionalACk =
        Boolean.parseBoolean(itemMiscInfo.get(ReceivingConstants.IS_OCC_CONDITIONAL_ACK));
    boolean packAcknowledged =
        Boolean.parseBoolean(itemMiscInfo.get(ReceivingConstants.IS_PACK_ACK));

    if (isCountryCodeACKEnabled) {
      if (Objects.nonNull(originCountryCodeAcknowledged)
          && (originCountryCodeAcknowledged || originCountryCodeAcknowledgedCoditionalACk)) {
        log.info(
            "item is either OCC Acknowledged or Conditional Acknowledged.So proceeding with regular instruction for item:{}, delivery:{}",
            deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
            deliveryDocumentsGdm.get(0).getDeliveryNumber());
      } else if (!originCountryCodeAcknowledgedCoditionalACk) {
        log.info(
            "item:{} in delivery:{} is Neither OCC Acknowledged Nor Conditional Acknowledged for facilityNum: {}.",
            deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
            deliveryDocumentsGdm.get(0).getDeliveryNumber(),
            getFacilityNum());
        return InstructionUtils.acknowledgePendingInstruction(
            instructionRequest,
            isCountryCodeACKEnabled,
            originCountryCodeAcknowledged,
            originCountryCodeAcknowledgedCoditionalACk,
            ispackAckEnabled,
            packAcknowledged);
      }
    }
    if (ispackAckEnabled) {
      if (Objects.nonNull(packAcknowledged) && packAcknowledged) {
        log.info(
            "item is WHPK/VNPK Acknowledged.So proceeding with regular instruction for item:{}, delivery:{}",
            deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
            deliveryDocumentsGdm.get(0).getDeliveryNumber());
      } else {
        log.info(
            "item:{} in delivery:{} is Not WHPK/VNPK Acknowledged for facilityNum: {}.",
            deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
            deliveryDocumentsGdm.get(0).getDeliveryNumber(),
            getFacilityNum());
        return InstructionUtils.acknowledgePendingInstruction(
            instructionRequest,
            isCountryCodeACKEnabled,
            originCountryCodeAcknowledged,
            originCountryCodeAcknowledgedCoditionalACk,
            ispackAckEnabled,
            packAcknowledged);
      }
    }
    return instruction;
  }

  private Instruction originCountryCodeAndPackChecks(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> deliveryDocumentsGdm,
      Instruction instruction)
      throws ReceivingException {
    Boolean isCountryCodeACKEnabled =
        configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED);
    Boolean ispackAckEnabled =
        configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED);
    if (isCountryCodeACKEnabled || ispackAckEnabled) {
      if (Boolean.FALSE.equals(instructionRequest.isItemValidationDone())) {

        if (isCountryCodeACKEnabled
            && ispackAckEnabled
            && Objects.isNull(
                deliveryDocumentsGdm
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getOriginCountryCode())
            && (Objects.isNull(
                    deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getVendorPack())
                || Objects.isNull(
                    deliveryDocumentsGdm
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getWarehousePack()))) {
          log.info(
              "originCountryCode, VNPK/WHPK fields are missing in GDM response for Delivery: {} Item: {} for facilityNum: {}",
              deliveryDocumentsGdm.get(0).getDeliveryNumber(),
              deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
              getFacilityNum());
          throw new ReceivingException(
              ReceivingConstants.OCC_AND_PACK_SIZE_NOT_FOUND_ERROR_MESSAGE, HttpStatus.NOT_FOUND);
        } else if (isCountryCodeACKEnabled
            && Objects.isNull(
                deliveryDocumentsGdm
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getOriginCountryCode())) {
          log.info(
              "originCountryCode field is missing in GDM response for Delivery: {} Item: {} for facilityNum: {}",
              deliveryDocumentsGdm.get(0).getDeliveryNumber(),
              deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
              getFacilityNum());
          throw new ReceivingException(
              ReceivingConstants.OCC_NOT_FOUND_ERROR_MESSAGE, HttpStatus.NOT_FOUND);
        } else if (ispackAckEnabled
            && (Objects.isNull(
                    deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getVendorPack())
                || Objects.isNull(
                    deliveryDocumentsGdm
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getWarehousePack()))) {
          log.info(
              "VNPK or WHPK fields are missing in GDM response for Delivery: {} Item: {} for facilityNum: {}",
              deliveryDocumentsGdm.get(0).getDeliveryNumber(),
              deliveryDocumentsGdm.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
              getFacilityNum());
          throw new ReceivingException(
              ReceivingConstants.PACK_SIZE_NOT_FOUND_ERROR_MESSAGE, HttpStatus.NOT_FOUND);
        } else
          instruction =
              originCountryCodeChecks(
                  deliveryDocumentsGdm,
                  instructionRequest,
                  isCountryCodeACKEnabled,
                  ispackAckEnabled);
      }
    }
    return instruction;
  }

  /**
   * This method is used to receive packs based on the tracking id mainly for rdc market creating
   * required containers, receipts, print job and audit log updates
   *
   * @param trackingId
   * @param httpHeaders
   * @return receive pack response
   * @throws ReceivingException receiving exception
   */
  public ReceivePackResponse receiveDsdcPackByTrackingId(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {

    ReceivePackHandler receivePackHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_PACK_HANDLER_KEY,
            ReceivePackHandler.class);

    return receivePackHandler.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
  }

  public InstructionResponse ManualPoSelection(
      List<DeliveryDocument> deliveryDocuments_gdm,
      InstructionResponse instructionResponse,
      InstructionRequest instructionRequest) {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    // Prepare the list of PO/LINE
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments_gdm) {
      poNumberList.add(deliveryDocument.getPurchaseReferenceNumber());
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        poLineNumberSet.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    }
    DeliveryDocumentSelector documentSelector =
        documentSelectorProvider.getDocumentSelector(deliveryDocuments_gdm);
    ReceiptsAggregator receivedQtyByDeliveryPoPol =
        documentSelector.getReceivedQtyByPoPol(
            deliveryDocuments_gdm, poNumberList, poLineNumberSet);
    List<DeliveryDocument> noOpenQtyPos = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments_gdm) {
      boolean isOpenQtyPo = false;
      List<DeliveryDocumentLine> deliveryDocumentLines =
          deliveryDocument.getDeliveryDocumentLines();
      for (DeliveryDocumentLine line : deliveryDocumentLines) {
        ImmutablePair<Long, Long> openQtyReceivedQtyPair =
            documentSelector.getOpenQtyTotalReceivedQtyForLineSelection(
                deliveryDocument, line, receivedQtyByDeliveryPoPol, Boolean.FALSE);
        defaultDeliveryDocumentSelector.setFieldsInDocumentLine(line, openQtyReceivedQtyPair);
        if (openQtyReceivedQtyPair.getLeft() > 0) {
          deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(line));
          deliveryDocuments.add(deliveryDocument);
          isOpenQtyPo = true;
          break;
        }
      }
      if (!isOpenQtyPo) {
        deliveryDocuments.add(deliveryDocument);
        noOpenQtyPos.add(deliveryDocument);
        if (noOpenQtyPos.size() == deliveryDocuments_gdm.size()) {
          return getCCOveragePalletInstructionResponse(instructionRequest, deliveryDocuments_gdm);
        }
      }
    }
    instructionResponse.setDeliveryDocuments(deliveryDocuments);
    final Instruction instructionNew = new Instruction();
    instructionNew.setInstructionCode(ReceivingConstants.MANUAL_PO_SELECTION);
    instructionResponse.setInstruction(instructionNew);
    return instructionResponse;
  }
}
