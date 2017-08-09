package it.infn.mw.iam.api.tokens;

import static it.infn.mw.iam.api.tokens.Constants.ACCESS_TOKENS_ENDPOINT;

import it.infn.mw.iam.api.account.authority.ErrorDTO;
import it.infn.mw.iam.api.tokens.exception.TokenNotFoundException;
import it.infn.mw.iam.api.tokens.model.AccessToken;
import it.infn.mw.iam.api.tokens.model.TokensListResponse;
import it.infn.mw.iam.api.tokens.service.TokenService;
import it.infn.mw.iam.api.tokens.service.paging.TokensPageRequest;
import it.infn.mw.iam.core.user.exception.IamAccountException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@Transactional
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(ACCESS_TOKENS_ENDPOINT)
public class AccessTokensController extends TokensControllerSupport {

  public static final Logger log = LoggerFactory.getLogger(AccessTokensController.class);

  @Autowired
  private TokenService<AccessToken> tokenService;

  @RequestMapping(method = RequestMethod.GET, produces = CONTENT_TYPE)
  public MappingJacksonValue listAccessTokens(
      @RequestParam(value = "count", required = false) Integer count,
      @RequestParam(value = "startIndex", required = false) Integer startIndex,
      @RequestParam(value = "userId", required = false) String userId,
      @RequestParam(value = "clientId", required = false) String clientId,
      @RequestParam(value = "attributes", required = false) final String attributes,
      HttpServletRequest request) {

    TokensPageRequest pr = buildTokensPageRequest(count, startIndex);
    TokensListResponse<AccessToken> results = getFilteredList(pr, userId, clientId);
    return filterAttributes(results, attributes);
  }

  private TokensListResponse<AccessToken> getFilteredList(TokensPageRequest pageRequest,
      String userId, String clientId) {

    Optional<String> user = Optional.ofNullable(userId);
    Optional<String> client = Optional.ofNullable(clientId);

    if (user.isPresent() && client.isPresent()) {
      return tokenService.getTokensForClientAndUser(user.get(), client.get(), pageRequest);
    }
    if (user.isPresent()) {
      return tokenService.getTokensForUser(user.get(), pageRequest);
    }
    if (client.isPresent()) {
      return tokenService.getTokensForClient(client.get(), pageRequest);
    }
    return tokenService.getAllTokens(pageRequest);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{id}", produces = CONTENT_TYPE)
  public AccessToken getAccessToken(@PathVariable("id") Long id, HttpServletRequest request) {

    log.debug("GET {}", request.getRequestURI());

    AccessToken at = tokenService.getTokenById(id);

    log.info("GET {} {}", request.getRequestURI(), HttpStatus.OK);
    return at;
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/{id}")
  public void revokeAccessToken(@PathVariable("id") Long id, HttpServletRequest request,
      HttpServletResponse response) {

    log.debug("DELETE {}", request.getRequestURI());

    tokenService.revokeTokenById(id);

    response.setStatus(HttpStatus.NO_CONTENT.value());
    log.info("DELETE {} {}", request.getRequestURI(), HttpStatus.NO_CONTENT);
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  @ExceptionHandler(TokenNotFoundException.class)
  public ErrorDTO tokenNotFoundError(HttpServletRequest req, Exception ex) {

    log.info("DELETE {} {}", req.getRequestURI(), HttpStatus.NOT_FOUND);
    return ErrorDTO.fromString(ex.getMessage());
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(IamAccountException.class)
  public ErrorDTO accountNotFoundError(HttpServletRequest req, Exception ex) {

    return ErrorDTO.fromString(ex.getMessage());
  }
}