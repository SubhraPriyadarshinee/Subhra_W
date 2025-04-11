package com.walmart.move.nim.receiving.core.common.validators;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class ProductCategoryGroupCSVFileValidator {
  public static void validateProductCategoryGroupCSSVFile(MultipartFile csvFile) {
    try {
      InputStream csvFileInputStream = csvFile.getInputStream();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(csvFileInputStream, StandardCharsets.UTF_8));
      String[] HEADERS = {"l0", "l1", "l2", "l3", "product_type", "group"};

      CSVFormat csvFormat =
          CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build();

      Iterable<CSVRecord> records = csvFormat.parse(reader);
      int row = 1;
      for (CSVRecord record : records) {
        String productType = record.get("product_type");
        String group = record.get("group");
        if (StringUtils.isEmpty(productType) || StringUtils.isEmpty(group)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_REQUEST,
              String.format(
                  ReceivingConstants.INVALID_PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_REQUEST, row));
        }
        row++;
      }

    } catch (IOException ioe) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_REQUEST,
          String.format(ReceivingConstants.PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_UNKNOWN_ERROR));
    }
  }
}
