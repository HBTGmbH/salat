package org.tb.user;

import static org.tb.common.util.SecureHashUtils.encodePassword;
import static org.tb.common.util.SecureHashUtils.passwordMatches;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.passay.CharacterData;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.springframework.stereotype.Service;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class UserAccessTokenService {

  private final UserAccessTokenRepository userAccessTokenRepository;
  private final EmployeeRepository employeeRepository;

  public DecryptedUserAccessToken generateToken(long employeeId, LocalDateTime validUntil, String comment) {
    var employee = employeeRepository.findById(employeeId).orElseThrow();
    var tokenId = generateTokenId(employee.getSign());
    var secret = generateTokenSecret();
    var token = new UserAccessToken();
    token.setTokenId(tokenId);
    token.setTokenSecretEncrypted(encodePassword(secret));
    token.setEmployee(employee);
    token.setComment(comment);
    token.setValidUntil(validUntil);
    userAccessTokenRepository.save(token);
    return new DecryptedUserAccessToken(token.getId(), tokenId, secret, validUntil, comment, employeeId);
  }

  public List<UserAccessToken> getTokens(long employeeId) {
    return userAccessTokenRepository.findAllByEmployeeIdOrderById(employeeId);
  }

  public Optional<Employee> authenticate(String tokenId, String tokenSecret) {
    return userAccessTokenRepository
        .findByTokenId(tokenId)
        .filter(t -> passwordMatches(tokenSecret, t.getTokenSecretEncrypted()))
        .map(UserAccessToken::getEmployee);
  }

  public boolean delete(long employeeId, long userAccessTokenId) {
    return userAccessTokenRepository
        .findById(userAccessTokenId)
        .filter(t -> t.getEmployee().getId().equals(employeeId))
        .map(t -> {
          userAccessTokenRepository.delete(t);
          return true;
        })
        .orElse(false);
  }

  private String generateTokenSecret() {
    PasswordGenerator gen = new PasswordGenerator();
    CharacterData lowerCaseChars = EnglishCharacterData.LowerCase;
    CharacterRule lowerCaseRule = new CharacterRule(lowerCaseChars);
    lowerCaseRule.setNumberOfCharacters(15);

    CharacterData upperCaseChars = EnglishCharacterData.UpperCase;
    CharacterRule upperCaseRule = new CharacterRule(upperCaseChars);
    upperCaseRule.setNumberOfCharacters(15);

    CharacterData digitChars = EnglishCharacterData.Digit;
    CharacterRule digitRule = new CharacterRule(digitChars);
    digitRule.setNumberOfCharacters(8);

    CharacterData specialChars = EnglishCharacterData.Special;
    CharacterRule spacialRule = new CharacterRule(digitChars);
    spacialRule.setNumberOfCharacters(2);

    String password = gen.generatePassword(40, lowerCaseRule, upperCaseRule, digitRule, spacialRule);
    return password;
  }

  private String generateTokenId(String employeeSign) {
    PasswordGenerator gen = new PasswordGenerator();
    CharacterData lowerCaseChars = EnglishCharacterData.LowerCase;
    CharacterRule lowerCaseRule = new CharacterRule(lowerCaseChars);
    lowerCaseRule.setNumberOfCharacters(10);

    CharacterData upperCaseChars = EnglishCharacterData.UpperCase;
    CharacterRule upperCaseRule = new CharacterRule(upperCaseChars);
    upperCaseRule.setNumberOfCharacters(10);

    String password = gen.generatePassword(20, lowerCaseRule, upperCaseRule);
    return employeeSign + "." + password;
  }

}
