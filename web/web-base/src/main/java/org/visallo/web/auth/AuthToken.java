package org.visallo.web.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

public class AuthToken {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String userId;
    private final SecretKey jwtKey;
    private final Date expiration;
    private final String tokenId;

    public AuthToken(String userId, SecretKey macKey, Date expiration) {
        this(AuthToken.generateTokenId(), userId, macKey, expiration);
    }

    private AuthToken(String tokenId, String userId, SecretKey macKey, Date expiration) {
        this.tokenId = tokenId;
        this.userId = userId;
        this.jwtKey = macKey;
        this.expiration = expiration;
    }

    public static SecretKey generateKey(String keyPassword, String keySalt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(keyPassword.toCharArray(), keySalt.getBytes(), 10000, 256);
        return factory.generateSecret(spec);
    }

    public static AuthToken parse(String token, SecretKey macKey) throws AuthTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(macKey);

            if (signedJWT.verify(verifier)) {
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                return new AuthToken(claims.getJWTID(), claims.getSubject(), macKey, claims.getExpirationTime());
            } else {
                throw new AuthTokenException("JWT signature verification failed");
            }
        } catch (Exception e) {
            throw new AuthTokenException(e);
        }
    }

    public String serialize() throws AuthTokenException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(tokenId)
                .subject(userId)
                .expirationTime(expiration)
                .build();

        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            JWSSigner signer = new MACSigner(jwtKey);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (Exception e) {
            throw new AuthTokenException(e);
        }
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getUserId() {
        return userId;
    }

    public Date getExpiration() {
        return expiration;
    }

    public boolean isExpired(int toleranceInSeconds) {
        Calendar expirationWithTolerance = Calendar.getInstance();
        expirationWithTolerance.setTime(expiration);
        expirationWithTolerance.add(Calendar.SECOND, toleranceInSeconds);
        return expirationWithTolerance.getTime().before(new Date());
    }

    private static String generateTokenId() {
        byte[] randomBytes = new byte[128];
        SECURE_RANDOM.nextBytes(randomBytes);

        ByteBuffer currentTimeBuffer = ByteBuffer.allocate(Long.BYTES);
        currentTimeBuffer.putLong(System.currentTimeMillis());

        return Base64.getEncoder().encodeToString(randomBytes)
                + "@" + Base64.getEncoder().encodeToString(currentTimeBuffer.array());
    }
}
