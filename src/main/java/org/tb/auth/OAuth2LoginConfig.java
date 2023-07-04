//package org.tb.auth;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.client.registration.ClientRegistration;
//import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
//import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
//import org.springframework.security.oauth2.core.AuthorizationGrantType;
//import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
//import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
//
//@Configuration
//public class OAuth2LoginConfig {
//  @Value("${AZURE_AD_CLIENT_ID}")
//  private String azureAdClientId;
//  @Value("${AZURE_AD_CLIENT_SECRET}")
//  private String azureAdClientSecret;
//  @Value("${AZURE_AD_TENANT_ID}")
//  private String azureAdTenantId;
//  @Bean
//  public ClientRegistrationRepository clientRegistrationRepository() {
//      return new InMemoryClientRegistrationRepository(
//          ClientRegistration
//              .withRegistrationId("azure")
//              .clientId(azureAdClientId)
//              .clientSecret(azureAdClientSecret)
//              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//              .redirectUri("/login/oauth2/code/*")
//              .scope("openid", "profile", "email", "address", "phone")
//              .authorizationUri("https://login.microsoftonline.com/"+azureAdTenantId+"/oauth2/v2.0")
//              .tokenUri("https://login.microsoftonline.com/"+azureAdTenantId+"/oauth2/v2.0/token")
//              .userNameAttributeName(IdTokenClaimNames.SUB)
//              .clientName("salat")
//              .build()
//      );
//    }
//
//  // AZURE_AD_CLIENT_ID=865b95c4-f360-4ca2-ba20-4fe28e3ea4f6;
//  // AZURE_AD_CLIENT_SECRET=y~u8Q~pMkw0TJgppB1zNcDnG2dWs3vv5SH.u9bQs;
//  // AZURE_AD_TENANT_ID=0af2c34e-4e40-42d9-89ab-e095b8151308;
//  // spring.security.oauth2.client.provider.azure.issuer-uri=https://login.microsoftonline.com/0af2c34e-4e40-42d9-89ab-e095b8151308/v2.0
//
//}