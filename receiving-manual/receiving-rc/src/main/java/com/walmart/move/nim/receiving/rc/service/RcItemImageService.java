package com.walmart.move.nim.receiving.rc.service;

import com.walmart.move.nim.receiving.core.service.BlobFileStorageService;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class RcItemImageService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RcItemImageService.class);
  @Autowired BlobFileStorageService blobFileStorageService;

  public int uploadItemImages(final String workflowId, int imageCount, MultipartFile[] imageFiles) {
    LOGGER.info(
        "Uploading images to workflow {} using {} image files", workflowId, imageFiles.length);
    AtomicInteger successCount = new AtomicInteger(0);
    Arrays.asList(imageFiles)
        .stream()
        .forEach(
            imageFile -> {
              String imageFileName = "image" + (imageCount + successCount.getAndIncrement() + 1);
              String fileName = imageFile.getOriginalFilename();
              if (StringUtils.isNotBlank(fileName) && fileName.contains(".")) {
                int suffixIndex = fileName.lastIndexOf(".");
                imageFileName += (suffixIndex > 0) ? fileName.substring(suffixIndex) : "";
              } else {
                imageFileName += ".jpg"; // TODO: Fetch file extension from CCM?
              }
              LOGGER.info(
                  "Uploading image {} to workflow {} using original image file {}",
                  imageFileName,
                  workflowId,
                  fileName);
              try {
                blobFileStorageService.uploadWithRetry(
                    imageFile.getBytes(),
                    BlobFileStorageService.Container.RETURN_ITEM_IMAGES,
                    workflowId,
                    imageFileName);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    return successCount.get();
  }

  public byte[] downloadItemImage(final String workflowId, final String imageFileName) {
    return blobFileStorageService.downloadWithRetryEnabled(
        BlobFileStorageService.Container.RETURN_ITEM_IMAGES, workflowId, imageFileName);
  }
}
