package com.walmart.move.nim.receiving.rc.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.service.BlobFileStorageService;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import java.text.ParseException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcItemImageServiceTest {

  @InjectMocks private RcItemImageService rcItemImageService;

  @Mock BlobFileStorageService blobFileStorageService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  private RestResponseExceptionHandler restResponseExceptionHandler;

  private MockMvc mockMvc;

  private Gson gson;

  @BeforeClass
  public void init() throws ParseException {
    gson = new GsonBuilder().setDateFormat(RcConstants.UTC_DATE_FORMAT).create();
    MockitoAnnotations.initMocks(this);
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(blobFileStorageService);
  }

  @Test
  public void uploadItemImagesTest() {

    final String fileName = "test.txt";
    final byte[] content = "Hello World".getBytes();
    MockMultipartFile mockMultipartFile =
        new MockMultipartFile("content", fileName, "text/plain", content);
    MultipartFile multipartFile =
        new MockMultipartFile("sourceFile1.tmp", "Hello World".getBytes());
    MultipartFile[] imageFiles = {multipartFile};
    rcItemImageService.uploadItemImages(fileName, 0, imageFiles);
    doNothing()
        .when(blobFileStorageService)
        .uploadWithRetry(
            content, BlobFileStorageService.Container.RETURN_ITEM_IMAGES, fileName, "image1.jpg");
    verify(blobFileStorageService, times(1))
        .uploadWithRetry(
            content, BlobFileStorageService.Container.RETURN_ITEM_IMAGES, fileName, "image1.jpg");
  }

  @Test
  public void uploadItemImagesExceptionTest() {

    final String fileName = "test.txt";
    final byte[] content = "Hello World".getBytes();
    MockMultipartFile mockMultipartFile =
        new MockMultipartFile("content", fileName, "text/plain", content);
    MultipartFile multipartFile =
        new MockMultipartFile("source.File1.tmp", "file.Name", null, "Hello World".getBytes());
    MultipartFile[] imageFiles = {multipartFile};
    rcItemImageService.uploadItemImages(fileName, 0, imageFiles);
    doThrow(new RuntimeException())
        .when(blobFileStorageService)
        .uploadWithRetry(
            content, BlobFileStorageService.Container.RETURN_ITEM_IMAGES, fileName, "image1.Name");
    verify(blobFileStorageService, times(1))
        .uploadWithRetry(
            content, BlobFileStorageService.Container.RETURN_ITEM_IMAGES, fileName, "image1.Name");
  }

  @Test
  public void downloadItemImageTest() {

    final String workFlowId = "1234567";
    final String fileName = "image1.Name";
    final byte[] content = "Hello World".getBytes();
    MultipartFile multipartFile =
        new MockMultipartFile("source.File1.tmp", "file.Name", null, "Hello World".getBytes());
    MultipartFile[] imageFiles = {multipartFile};
    rcItemImageService.downloadItemImage(workFlowId, fileName);
    when(blobFileStorageService.downloadWithRetryEnabled(
            BlobFileStorageService.Container.RETURN_ITEM_IMAGES, workFlowId, fileName))
        .thenReturn(content);
    verify(blobFileStorageService, times(1))
        .downloadWithRetryEnabled(any(), anyString(), anyString());
  }
}
