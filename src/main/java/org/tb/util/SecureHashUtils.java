package org.tb.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.tb.exception.LogicException;

@Slf4j
public class SecureHashUtils {

    private static final int COMPLEXITY = 10;

    private SecureHashUtils() {}

    public static String encodePassword(String password) {
        PasswordEncoder encoder = new BCryptPasswordEncoder(COMPLEXITY);
        return encoder.encode(password);
    }

    public static boolean passwordMatches(String enteredPassword, String hashedPassword) {
        PasswordEncoder encoder = new BCryptPasswordEncoder(COMPLEXITY);
        return encoder.matches(enteredPassword, hashedPassword);
    }

    public static boolean legacyPasswordMatches(String enteredPassword, String md5HashedPassword) {
        return makeMD5(enteredPassword).equals(md5HashedPassword);
    }

    /**
     * Makes a md5-hash for a given string.
     *
     * @return the md5-hash of the given string
     * @deprecated Use {@link #encodePassword(String)} and {@link #passwordMatches(String, String)} instead
     */
    @Deprecated
    public static String makeMD5(String text) {
        MessageDigest md;
        byte[] encryptMsg;

        try {
            md = MessageDigest.getInstance("MD5");        // getting a 'MD5-Instance'
            encryptMsg = md.digest(text.getBytes());    // solving the MD5-Hash
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 not supported!", e);
            throw new LogicException("MD5 not supported", e);
        }

        String swap = "";        // swap-string for the result
        String byteStr;    // swap-string for current hex-value of byte
        StringBuilder strBuf = new StringBuilder();

        for (int i = 0; i <= encryptMsg.length - 1; i++) {

            byteStr = Integer.toHexString(encryptMsg[i]); // swap-string for current hex-value of byte

            switch (byteStr.length()) {
                case 1: // if hex-number length is 1, add a '0' before
                    swap = "0" + Integer.toHexString(encryptMsg[i]);
                    break;

                case 2: // correct hex-letter
                    swap = Integer.toHexString(encryptMsg[i]);
                    break;

                case 8: // get the correct substring
                    swap = (Integer.toHexString(encryptMsg[i])).substring(6, 8);
                    break;
            }
            strBuf.append(swap); // appending swap to get complete hash-key
        }
        return strBuf.toString(); // returns the MD5-Hash
    }

}
