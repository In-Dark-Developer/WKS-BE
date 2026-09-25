package com.darkness.wks.common.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    // 64바이트(512비트) — HS256 최소 요건(32바이트)은 물론 아래 HS384 테스트가 요구하는 48바이트도 넉넉히 넘는다.
    // 서로 다른 값 두 개를 준비해 "다른 키로 검증 실패"를 확인한다.
    private static final String SECRET = "s".repeat(64);
    private static final String OTHER_SECRET = "o".repeat(64);

    private JwtProvider provider(String secret, int ttlDays) {
        return new JwtProvider(new AuthProperties(null, true, null, new AuthProperties.Jwt(secret, ttlDays)));
    }

    @Test
    void 발급한_토큰을_검증하면_같은_memberId_를_돌려준다() {
        JwtProvider provider = provider(SECRET, 30);

        String token = provider.issue(42L);

        assertThat(provider.verify(token)).contains(42L);
    }

    @Test
    void 서명이_다르면_검증에_실패한다() {
        JwtProvider signer = provider(SECRET, 30);
        JwtProvider verifier = provider(OTHER_SECRET, 30);

        String token = signer.issue(42L);

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtProvider provider = provider(SECRET, -1); // 발급 시점이 이미 만료 시각보다 뒤

        String token = provider.issue(42L);

        assertThat(provider.verify(token)).isEmpty();
    }

    @Test
    void 위조된_토큰은_검증에_실패한다() {
        JwtProvider provider = provider(SECRET, 30);
        String token = provider.issue(42L);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(provider.verify(tampered)).isEmpty();
    }

    @Test
    void null_이거나_빈_토큰은_검증에_실패한다() {
        JwtProvider provider = provider(SECRET, 30);

        assertThat(provider.verify(null)).isEmpty();
        assertThat(provider.verify("")).isEmpty();
        assertThat(provider.verify("not-a-jwt")).isEmpty();
    }

    @Test
    void alg_이_HS256_이_아니면_같은_키로_서명해도_검증에_실패한다() throws Exception {
        JwtProvider provider = provider(SECRET, 30);
        // 헤더를 HS384 로 바꿔 같은 비밀키로 서명한다 — MACSigner 는 헤더의 alg 를 그대로 믿고 서명하므로
        // 이 토큰 자체는 유효한 HS384 서명이다. verify() 는 헤더의 alg 를 신뢰하지 않고 HS256 만 받는다.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));

        assertThat(provider.verify(jwt.serialize())).isEmpty();
    }

    @Test
    void secret_이_없으면_구성되지_않은_것으로_본다() {
        JwtProvider provider = provider(null, 30);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.verify("anything")).isEmpty();
        assertThatThrownBy(() -> provider.issue(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secret_이_32바이트_미만이면_기동을_막는다() {
        assertThatThrownBy(() -> provider("too-short", 30))
                .isInstanceOf(IllegalStateException.class);
    }
}
