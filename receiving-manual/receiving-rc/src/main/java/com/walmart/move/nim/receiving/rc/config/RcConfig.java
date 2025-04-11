package com.walmart.move.nim.receiving.rc.config;

import com.walmart.move.nim.receiving.core.service.BlobFileStorageService;
import com.walmart.move.nim.receiving.rc.service.*;
import com.walmart.move.nim.receiving.rc.transformer.ProductCategoryGroupTransformer;
import com.walmart.move.nim.receiving.rc.transformer.ReceivingWorkflowTransformer;
import com.walmart.move.nim.receiving.rc.util.OrderLinesEnrichmentUtil;
import com.walmart.move.nim.receiving.rc.util.ReceivingWorkflowUtil;
import com.walmart.move.nim.receiving.rc.validator.ReceivingWorkflowValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ConditionalOnExpression("${enable.rc.app:false}")
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.rc.repositories")
public class RcConfig {
  @Bean(ReceivingConstants.RC_CONTAINER_SERVICE)
  public RcContainerService rcContainerService() {
    return new RcContainerService();
  }

  @Bean(ReceivingConstants.RC_PACKAGE_TRACKER_SERVICE)
  public RcPackageTrackerService rcPackageTrackerService() {
    return new RcPackageTrackerService();
  }

  @Bean(ReceivingConstants.RC_WORKFLOW_SERVICE)
  public RcWorkflowService rcWorkflowService() {
    return new RcWorkflowService();
  }

  @Bean(ReceivingConstants.RECEIVING_WORKFLOW_TRANSFORMER)
  public ReceivingWorkflowTransformer receivingWorkflowTransformer() {
    return new ReceivingWorkflowTransformer();
  }

  @Bean(ReceivingConstants.RECEIVING_WORKFLOW_UTIL)
  public ReceivingWorkflowUtil receivingWorkflowUtil() {
    return new ReceivingWorkflowUtil();
  }

  @Bean(ReceivingConstants.RECEIVING_WORKFLOW_VALIDATOR)
  public ReceivingWorkflowValidator receivingWorkflowValidator() {
    return new ReceivingWorkflowValidator();
  }

  @Bean(ReceivingConstants.ORDER_LINES_ENRICHMENT_UTIL)
  public OrderLinesEnrichmentUtil orderLinesEnrichmentUtil() {
    return new OrderLinesEnrichmentUtil();
  }

  @Bean(ReceivingConstants.BLOB_FILE_STORAGE_SERVICE)
  public BlobFileStorageService blobFileStorageService() {
    return new BlobFileStorageService();
  }

  @Bean(ReceivingConstants.RC_PRODUCT_CATEGORY_GROUP_SERVICE)
  public RcProductCategoryGroupService rcProductCategoryGroupService() {
    return new RcProductCategoryGroupService();
  }

  @Bean(ReceivingConstants.PRODUCT_CATEGORY_GROUP_TRANSFORMER)
  public ProductCategoryGroupTransformer productCategoryGroupTransformer() {
    return new ProductCategoryGroupTransformer();
  }

  @Bean(ReceivingConstants.RC_ITEM_IMAGE_SERVICE)
  public RcItemImageService rcItemImageService() {
    return new RcItemImageService();
  }
}
