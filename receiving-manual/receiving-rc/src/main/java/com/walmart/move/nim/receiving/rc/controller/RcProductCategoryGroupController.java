package com.walmart.move.nim.receiving.rc.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.validators.ProductCategoryGroupCSVFileValidator;
import com.walmart.move.nim.receiving.core.service.BlobFileStorageService;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcProductCategoryGroupImportStatsResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcProductCategoryGroupResponse;
import com.walmart.move.nim.receiving.rc.service.RcProductCategoryGroupService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_PRODUCT_CATEGORY_GROUP_URI)
@Validated
@Tag(
    name = "Return center product category group controller",
    description = "Return Center Product Category Group")
public class RcProductCategoryGroupController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RcProductCategoryGroupController.class);
  @Autowired private BlobFileStorageService blobFileStorageService;
  @Autowired private RcProductCategoryGroupService rcProductCategoryGroupService;

  @GetMapping(path = RcConstants.PRODUCT_TYPE_URI)
  @Operation(
      summary = "This will return product category group details by product type",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetProductCategoryGroupByProductTypeTimed",
      level1 = "uwms-receiving",
      level2 = "rcProductCategoryGroupController",
      level3 = "getProductCategoryGroupByProductType")
  @ExceptionCounted(
      name = "rcGetProductCategoryGroupByProductTypeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcProductCategoryGroupController",
      level3 = "getProductCategoryGroupByProductType")
  @TimeTracing(
      component = AppComponent.RC,
      type = Type.REST,
      flow = "GetProductCategoryGroupByProductType")
  public ResponseEntity<RcProductCategoryGroupResponse> getProductCategoryGroupByProductType(
      @PathVariable(value = "productType") @NotEmpty String productType) {
    RcProductCategoryGroupResponse receivingPCGroup =
        rcProductCategoryGroupService.getProductCategoryGroupByProductType(
            UriUtils.decode(productType, StandardCharsets.UTF_8));
    return new ResponseEntity<>(receivingPCGroup, HttpStatus.OK);
  }

  @PostMapping
  @Operation(
      summary = "This will import a csv file into product category group storage",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcImportProductCategoryGroupsTimed",
      level1 = "uwms-receiving",
      level2 = "rcProductCategoryGroupController",
      level3 = "importProductCategoryGroups")
  @ExceptionCounted(
      name = "rcImportProductCategoryGroupsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcProductCategoryGroupController",
      level3 = "importProductCategoryGroups")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "ImportProductCategoryGroups")
  public ResponseEntity<RcProductCategoryGroupImportStatsResponse> importCSVFile(
      @RequestParam("csvFile") MultipartFile csvFile, RedirectAttributes redirectAttributes) {
    LOGGER.info(
        "Import csvFile into ProductCategoryGroup request received {}",
        csvFile.getOriginalFilename());
    ProductCategoryGroupCSVFileValidator.validateProductCategoryGroupCSSVFile(csvFile);
    blobFileStorageService.upload(
        csvFile,
        BlobFileStorageService.Container.DB_IMPORTS,
        BLOB_PATH_PRODUCT_CATEGORY_GROUP,
        getTimestampedFileName(csvFile));
    RcProductCategoryGroupImportStatsResponse importStats =
        rcProductCategoryGroupService.importProductCategoryGroups(csvFile);
    return new ResponseEntity<>(importStats, HttpStatus.OK);
  }

  private String getTimestampedFileName(MultipartFile file) {
    final String suffix = new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss_SSS").format(new Date());
    String fileName =
        file.getOriginalFilename().substring(0, file.getOriginalFilename().length() - 4);
    return fileName + suffix + ".csv";
  }

  static final String BLOB_PATH_PRODUCT_CATEGORY_GROUP = "product_category_group";
}
