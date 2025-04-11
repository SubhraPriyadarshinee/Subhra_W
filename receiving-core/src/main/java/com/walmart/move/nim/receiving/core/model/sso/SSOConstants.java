package com.walmart.move.nim.receiving.core.model.sso;

public class SSOConstants {

  // System Token Scopes
  public static final String OPENID_TOKEN_SCOPE = "openid";

  // Standard response types
  public static final String OPENID_RESTYPE_CODE = "code";

  // Grant Type Constants
  public static final String AUTHORIZATION_CODE_GRANT = "authorization_code";

  public static final String TOKEN_TYPE_PARAM = "token_type";
  public static final String EXPIRES_IN_PARAM = "expires_in";

  public static final String CLIENT_ID_PARAM_NAME = "client_id";
  public static final String RESPONSE_TYPE_PARAM_NAME = "response_type";
  public static final String REDIRECT_URI_PARAM_NAME = "redirect_uri";
  public static final String LOGOUT_REDIRECT_URI_PARAM_NAME = "post_logout_redirect_uri";
  public static final String SCOPE_PARAM_NAME = "scope";
  public static final String NONCE_PARAM_NAME = "nonce";
  public static final String STATE_PARAM_NAME = "state";
  public static final String TOKEN_PARAM_NAME = "token";

  // URL constants
  public static String AUTHORIZE_END_POINT = "/authorize";
  public static String TOKEN_END_POINT = "/token";
  public static String CLAIMS_SET_END_POINT = "/token/introspect";
  public static String LOGOUT_END_POINT = "/ppidp/logout";

  // Data for custom claims
  public static final String UA_LOGIN_ID = "loginId";
  public static final String UA_NAME = "username";
  public static final String UA_ACTIVE = "active";

  // Exception messages
  public static final String IDP_SSO_UNAUTHORISED = "Unable to authorize user. Exception - %s";
  public static final String IDP_SSO_SERVICE_DOWN = "Unable to reach IDP SSO service user";
}
