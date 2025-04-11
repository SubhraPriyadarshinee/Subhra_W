package com.walmart.move.nim.receiving.core.common;

import com.ibm.mq.jms.MQConnectionFactory;
import io.strati.messaging.jms.config.NativeConfig;
import io.strati.messaging.jms.exception.AxonException;
import io.strati.messaging.jms.handler.IBMMQNativeConfigHandler;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.jms.ConnectionFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class ExtendedIBMMQNativeConfigHandler extends IBMMQNativeConfigHandler {

  private String keyStorePath = null;
  private String keyStorePassword = null;
  private String secretKey = null;

  public ExtendedIBMMQNativeConfigHandler(
      String keyStorePath, String keyStorePassword, String secretKey) {
    this.keyStorePassword = keyStorePassword;
    this.keyStorePath = keyStorePath;
    this.secretKey = secretKey;
  }

  @Override
  public void handle(
      NativeConfig nativeConfig, ConnectionFactory nativeConnectionFactory, String brokerUrl)
      throws AxonException {
    super.handle(nativeConfig, nativeConnectionFactory, brokerUrl);
    MQConnectionFactory factory = (MQConnectionFactory) nativeConnectionFactory;
    factory.setSSLSocketFactory(getSSLSocketFactory());
  }

  public SSLSocketFactory getSSLSocketFactory() {
    try {
      KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      try (InputStream in = new FileInputStream(keyStorePath)) {
        keystore.load(in, SecurityUtil.decryptValue(secretKey, keyStorePassword).toCharArray());
      }
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(
          keystore, SecurityUtil.decryptValue(secretKey, keyStorePassword).toCharArray());

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keystore);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          keyManagerFactory.getKeyManagers(),
          trustManagerFactory.getTrustManagers(),
          new SecureRandom());

      return sslContext.getSocketFactory();

    } catch (Exception e) {
      throw new RuntimeException("SSLSocketFactory creation failed");
    }
  }
}
