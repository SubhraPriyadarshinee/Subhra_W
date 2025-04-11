package com.walmart.move.nim.receiving.core.model.sso;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@XmlRootElement(name = "tokenRequest")
@JsonRootName("tokenRequest")
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponseDTO {

  private static final String CONS_JSON_ACCESS_TOKEN = "access_token";
  private static final String CONS_JSON_ID_TOKEN = "id_token";
  private static final String CONS_JSON_REFRESH_TOKEN = "refresh_token";
  private String accessToken;
  private String idToken;
  private String refreshToken;
  private String tokenType;
  private String scope;
  private long expiresIn;

  @XmlElement(name = CONS_JSON_ACCESS_TOKEN)
  @JsonGetter(CONS_JSON_ACCESS_TOKEN)
  public String getAccessToken() {
    return accessToken;
  }

  @JsonSetter(CONS_JSON_ACCESS_TOKEN)
  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  @XmlElement(name = CONS_JSON_ID_TOKEN)
  @JsonGetter(CONS_JSON_ID_TOKEN)
  public String getIdToken() {
    return idToken;
  }

  @JsonSetter(CONS_JSON_ID_TOKEN)
  public void setIdToken(String idToken) {
    this.idToken = idToken;
  }

  @XmlElement(name = CONS_JSON_REFRESH_TOKEN)
  @JsonGetter(CONS_JSON_REFRESH_TOKEN)
  public String getRefreshToken() {
    return refreshToken;
  }

  @JsonSetter(CONS_JSON_REFRESH_TOKEN)
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  @XmlElement(name = SSOConstants.TOKEN_TYPE_PARAM)
  @JsonGetter(SSOConstants.TOKEN_TYPE_PARAM)
  public String getTokenType() {
    return tokenType;
  }

  @JsonSetter(SSOConstants.TOKEN_TYPE_PARAM)
  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  @XmlElement(name = SSOConstants.SCOPE_PARAM_NAME)
  @JsonGetter(SSOConstants.SCOPE_PARAM_NAME)
  public String getScope() {
    return scope;
  }

  @JsonSetter(SSOConstants.SCOPE_PARAM_NAME)
  public void setScope(String scope) {
    this.scope = scope;
  }

  @XmlElement(name = SSOConstants.EXPIRES_IN_PARAM)
  @JsonGetter(SSOConstants.EXPIRES_IN_PARAM)
  public long getExpiresIn() {
    return expiresIn;
  }

  @JsonSetter(SSOConstants.EXPIRES_IN_PARAM)
  public void setExpiresIn(long expiresIn) {
    this.expiresIn = expiresIn;
  }
}
