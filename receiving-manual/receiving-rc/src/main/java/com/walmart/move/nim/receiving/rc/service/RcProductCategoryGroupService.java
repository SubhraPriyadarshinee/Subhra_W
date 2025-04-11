package com.walmart.move.nim.receiving.rc.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.rc.contants.ProductDetails;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.entity.ProductCategoryGroup;
import com.walmart.move.nim.receiving.rc.entity.ProductCategoryGroupPK;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcProductCategoryGroupImportStatsResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcProductCategoryGroupResponse;
import com.walmart.move.nim.receiving.rc.repositories.ProductCategoryGroupRepository;
import com.walmart.move.nim.receiving.rc.transformer.ProductCategoryGroupTransformer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

public class RcProductCategoryGroupService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RcProductCategoryGroupService.class);

  @Autowired private ProductCategoryGroupRepository productCategoryGroupRepository;
  @Autowired private ProductCategoryGroupTransformer productCategoryGroupTransformer;

  @Transactional(readOnly = true)
  public RcProductCategoryGroupResponse getProductCategoryGroupByProductType(String productType) {
    LOGGER.info("Fetching product category group details for productType: {}", productType);
    ProductCategoryGroup productCategoryGroup;
    try {
      productCategoryGroup =
          productCategoryGroupRepository.getProductCategoryGroupByProductType(productType);
      if (Objects.isNull(productCategoryGroup)) {
        String errorDescription =
            String.format(
                ExceptionDescriptionConstants
                    .PRODUCT_CATEGORY_GROUP_NOT_FOUND_FOR_PRODUCT_TYPE_ERROR_MSG,
                productType);
        LOGGER.warn(errorDescription);

        // Category C remains the default choice.
        productCategoryGroup =
            ProductCategoryGroup.builder()
                .id(ProductCategoryGroupPK.builder().build())
                .group(RcConstants.CATEGORY_C)
                .createTs(new Timestamp(System.currentTimeMillis()))
                .createUser("sysadmin")
                .lastChangedTs(new Timestamp(System.currentTimeMillis()))
                .lastChangedUser("sysadmin")
                .build();
      }
    } catch (Exception e) {
      LOGGER.error("Exception while fetching product category group: ", e);
      productCategoryGroup =
          ProductCategoryGroup.builder()
              .id(ProductCategoryGroupPK.builder().build())
              .group(RcConstants.CATEGORY_C)
              .createTs(new Timestamp(System.currentTimeMillis()))
              .createUser("sysadmin")
              .lastChangedTs(new Timestamp(System.currentTimeMillis()))
              .lastChangedUser("sysadmin")
              .build();
    }
    LOGGER.info(
        "Product category group details for productType: {} is {}",
        productType,
        productCategoryGroup.getGroup());
    return productCategoryGroupTransformer.transformProductCategoryGroupEntityToDTO(
        productCategoryGroup);
  }

  @Transactional
  public RcProductCategoryGroupImportStatsResponse importProductCategoryGroups(
      MultipartFile csvFile) {
    AtomicInteger totalCount = new AtomicInteger();
    try {
      InputStream csvFileInputStream = csvFile.getInputStream();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(csvFileInputStream, StandardCharsets.UTF_8));
      String[] HEADERS = RcConstants.PRODUCT_DETAILS_HEADERS;

      CSVFormat csvFormat =
          CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build();

      Iterable<CSVRecord> records = csvFormat.parse(reader);
      ExecutorService executor = Executors.newFixedThreadPool(8);
      records.forEach(
          record ->
              executor.submit(
                  () -> {
                    ProductCategoryGroup row =
                        ProductCategoryGroup.builder()
                            .id(
                                ProductCategoryGroupPK.builder()
                                    .l0(record.get(ProductDetails.L0.value))
                                    .l1(record.get(ProductDetails.L1.value))
                                    .l2(record.get(ProductDetails.L2.value))
                                    .l3(record.get(ProductDetails.L3.value))
                                    .productType(record.get(ProductDetails.PRODUCT_TYPE.value))
                                    .build())
                            .group(record.get(ProductDetails.GROUP.value))
                            .createUser("-Imported-")
                            .build();
                    productCategoryGroupRepository.save(row);
                    totalCount.getAndIncrement();
                  }));
      executor.shutdown();
      while (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
        LOGGER.info("Imported {} ProductCategoryGroup rows", totalCount.get());
      }
    } catch (IOException | InterruptedException ioe) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.PRODUCT_CATEGORY_GROUP_UNABLE_TO_IMPORT_ERROR_MSG);
      LOGGER.error(errorDescription);
      Thread.currentThread().interrupt();
    }
    return RcProductCategoryGroupImportStatsResponse.builder()
        .totalProductCategoryGroupCount(totalCount.get())
        .build();
  }
}
