package it.infn.mw.iam.test.registration;

import static it.infn.mw.iam.authn.ExternalAuthenticationHandlerSupport.EXT_AUTH_ERROR_KEY;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.transaction.Transactional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import it.infn.mw.iam.IamLoginService;
import it.infn.mw.iam.authn.saml.util.Saml2Attribute;
import it.infn.mw.iam.persistence.model.IamAccount;
import it.infn.mw.iam.persistence.model.IamSamlId;
import it.infn.mw.iam.persistence.repository.IamAccountRepository;
import it.infn.mw.iam.registration.PersistentUUIDTokenGenerator;
import it.infn.mw.iam.registration.RegistrationRequestDto;
import it.infn.mw.iam.test.ext_authn.saml.SamlExternalAuthenticationTestSupport;
import it.infn.mw.iam.test.ext_authn.saml.SamlTestConfig;
import it.infn.mw.iam.test.util.WithMockSAMLUser;
import it.infn.mw.iam.test.util.saml.SamlUtils;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {IamLoginService.class, SamlTestConfig.class})
@WebAppConfiguration
@Transactional
public class SamlExtAuthRegistrationTests extends SamlExternalAuthenticationTestSupport {

  @Autowired
  private IamAccountRepository iamAccountRepo;

  @Autowired
  private PersistentUUIDTokenGenerator generator;

  @Test
  @WithMockSAMLUser(issuer = DEFAULT_IDP_ID, subject = T1_EPUID)
  public void externalSamlRegistrationCreatesDisabledAccount() throws Throwable {

    String username = "test-saml-ext-reg";

    String email = username + "@example.org";
    RegistrationRequestDto request = new RegistrationRequestDto();
    request.setGivenname("Test");
    request.setFamilyname("Saml User");
    request.setEmail(email);
    request.setUsername(username);
    request.setNotes("Some short notes...");

    byte[] requestBytes = mvc
      .perform(post("/registration/create").contentType(MediaType.APPLICATION_JSON_UTF8)
        .content(objectMapper.writeValueAsBytes(request)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsByteArray();

    request = objectMapper.readValue(requestBytes, RegistrationRequestDto.class);
    String token = generator.getLastToken();

    // If the user tries to authenticate with his external account, he's redirected to the
    // login page with an account disabled error
    MockHttpSession session = (MockHttpSession) mvc.perform(get(samlLoginUrl()))
      .andExpect(status().isOk())
      .andReturn()
      .getRequest()
      .getSession();

    AuthnRequest authnRequest = getAuthnRequestFromSession(session);
    Response r = buildTest1Response(authnRequest);

    session = (MockHttpSession) mvc
      .perform(post(authnRequest.getAssertionConsumerServiceURL())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("SAMLResponse", SamlUtils.signAndSerializeToBase64(r))
        .session(session))
      .andExpect(status().isFound())
      .andExpect(redirectedUrlPattern("/login**"))
      .andExpect(request().sessionAttribute(EXT_AUTH_ERROR_KEY, notNullValue()))
      .andReturn()
      .getRequest()
      .getSession();

    assertThat(session.getAttribute(EXT_AUTH_ERROR_KEY), instanceOf(DisabledException.class));

    DisabledException err = (DisabledException) session.getAttribute(EXT_AUTH_ERROR_KEY);
    assertThat(err.getMessage(),
        startsWith("Your registration request to indigo-dc was submitted successfully"));

    // the same happens after having confirmed the request
    mvc.perform(get("/registration/confirm/{token}", token)).andExpect(status().isOk());

    session = (MockHttpSession) mvc.perform(get(samlLoginUrl()))
      .andExpect(status().isOk())
      .andReturn()
      .getRequest()
      .getSession();

    authnRequest = getAuthnRequestFromSession(session);
    r = buildTest1Response(authnRequest);
    session = (MockHttpSession) mvc
      .perform(post(authnRequest.getAssertionConsumerServiceURL())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("SAMLResponse", SamlUtils.signAndSerializeToBase64(r))
        .session(session))
      .andExpect(status().isFound())
      .andExpect(redirectedUrlPattern("/login**"))
      .andExpect(request().sessionAttribute(EXT_AUTH_ERROR_KEY, notNullValue()))
      .andReturn()
      .getRequest()
      .getSession();

    assertThat(session.getAttribute(EXT_AUTH_ERROR_KEY), instanceOf(DisabledException.class));
    err = (DisabledException) session.getAttribute(EXT_AUTH_ERROR_KEY);
    assertThat(err.getMessage(), startsWith(
        "Your registration request to indigo-dc was submitted and confirmed successfully"));

    IamSamlId id = new IamSamlId(DEFAULT_IDP_ID, Saml2Attribute.epuid.getAttributeName(), T1_EPUID);

    IamAccount account = iamAccountRepo.findBySamlId(id)
      .orElseThrow(() -> new AssertionError("Expected account not found"));

    iamAccountRepo.delete(account);
  }
}
