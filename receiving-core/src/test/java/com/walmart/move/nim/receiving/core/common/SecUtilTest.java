package com.walmart.move.nim.receiving.core.common;

import static org.testng.Assert.assertEquals;

import java.security.Key;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class SecUtilTest extends AbstractTestNGSpringContextTests {

  public String secretKey = "AtlasReceivngKey";

  @Test
  public void testDecryption() throws Exception {
    String msgToTest = "how are you";

    Key aesKey = new SecretKeySpec(secretKey.getBytes(), "AES");
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, aesKey);
    byte[] encrytped = cipher.doFinal(msgToTest.getBytes());
    String enCodedEncryptedValue = Base64.getEncoder().encodeToString(encrytped);
    String decrypted = SecurityUtil.decryptValue(secretKey, enCodedEncryptedValue);

    assertEquals(msgToTest, decrypted);
  }
}
