package com.darkness.wks.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * 로그인 토큰 발급·검증. HS256 고정, 클레임은 sub(memberId)·iat·exp 뿐이다.
 * 서버 쪽 토큰 폐기·기기 관리는 하지 않는다(2026-09-21 결정 유지) — 로그아웃은 프론트가 토큰을 지우는
 * 것으로 처리하고(2026-09-23), 지우지 않은 사본은 만료(기본 15일)까지 그대로 유효하다.
 */
@Component
public class JwtProvider {

    private final AuthProperties.Jwt config;
    // secret 이 비어 있으면(로그인 미설정) 둘 다 null — issue()/verify() 는 그 앞에 isConfigured() 로 걸러진다.
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtProvider(AuthProperties properties) {
        this.config = properties.jwt();
        String secret = config == null ? null : config.secret();
        if (secret == null || secret.isBlank()) {
            this.signer = null;
            this.verifier = null;
            return;
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            // 값은 있는데 못 쓰는 경우(HS256 은 32바이트 미만이면 거부)라 기동을 막는다 — 빈 값(미설정)과는 다르다.
            throw new IllegalStateException(
                    "JWT_SECRET 이 올바르지 않다 (HS256 은 최소 32바이트 필요): " + e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return signer != null;
    }

    public String issue(long memberId) {
        if (signer == null) {
            throw new IllegalStateException("JWT_SECRET 미설정 — 호출 전에 isConfigured() 를 확인할 것");
        }
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(Long.toString(memberId))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(config.ttlDays(), ChronoUnit.DAYS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명 실패", e);
        }
        return jwt.serialize();
    }

    /**
     * 유효하면 memberId, 아니면 empty. 서명 불일치·만료·형식 오류·sub 파싱 실패를 구분해 응답하지 않는다
     * (이유를 알려주면 토큰 위조 시도에 힌트를 준다) — 그래서 모든 실패를 한 catch 로 묶는다.
     */
    public Optional<Long> verify(String token) {
        if (verifier == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // 헤더의 alg 를 신뢰하지 않고 HS256 으로 고정한다. MACVerifier 도 HMAC 계열만 받지만 명시한다.
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            if (expiresAt == null || expiresAt.before(new Date())) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
