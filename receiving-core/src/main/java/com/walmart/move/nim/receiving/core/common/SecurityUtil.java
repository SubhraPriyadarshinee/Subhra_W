package com.walmart.move.nim.receiving.core.common;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtil {
  private SecurityUtil() {}

  private static final String ENCRYPTION_ALGO = "AES";

  public static String decryptValue(String secretKey, String encodedValue)
      throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          BadPaddingException, IllegalBlockSizeException {

    byte[] encValue = Base64.getDecoder().decode(encodedValue);
    Key aesKey = new SecretKeySpec(secretKey.getBytes(), ENCRYPTION_ALGO);
    Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
    cipher.init(Cipher.DECRYPT_MODE, aesKey);
    return new String(cipher.doFinal(encValue));
  }

  public static String encryptValue(String secretKey, String value)
      throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          BadPaddingException, IllegalBlockSizeException {

    Key aesKey = new SecretKeySpec(secretKey.getBytes(), ENCRYPTION_ALGO);
    Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
    cipher.init(Cipher.ENCRYPT_MODE, aesKey);
    byte[] byteValue = cipher.doFinal(value.getBytes());
    byte[] encValue = Base64.getEncoder().encode(byteValue);
    return new String(encValue);
  }
}
