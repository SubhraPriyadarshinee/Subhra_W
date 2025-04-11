package com.walmart.move.nim.receiving.core.common;

public class AzureBlobConstants {

  public static final String BLOB_ENDPOINT = "https://%s.blob.core.windows.net";
  public static final String BLOB_NOT_FOUND = "BlobNotFound";
  public static final String CONTAINER_NOT_FOUND = "ContainerNotFound";

  public static final String UPLOAD_ERROR =
      "Error occurred while uploading blob data, container {}, blob {}";
  public static final String UPLOAD_ERROR_MSG = "Blob upload data error=%s";

  public static final String DOWNLOAD_ERROR =
      "Error occurred while downloading blob data, container {}, blob {}";
  public static final String DOWNLOAD_ERROR_MSG = "Blob upload data error=%s";
  public static final String URL_DOWNLOAD_ERROR =
      "Error occurred while downloading blob data by url {}";

  public static final String DELETE_ERROR =
      "Error occurred while deleting blob, container {}, blob {}";
  public static final String DELETE_ERROR_MSG = "Blob delete error=%s";
}
