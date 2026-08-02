package com.erp.system.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationCodeServiceTest {

    @Test
    void generatesReadableRandomCodes() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String code = InvitationCodeService.Impl.generateCode();
            assertThat(code).matches("XF-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}");
            generated.add(code);
        }
        assertThat(generated).hasSize(100);
    }

    @Test
    void nullExpirationMeansPermanent() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(InvitationCodeService.Impl.isExpired(null, now)).isFalse();
        assertThat(InvitationCodeService.Impl.isExpired(now.minusSeconds(1), now)).isTrue();
        assertThat(InvitationCodeService.Impl.isExpired(now.plusSeconds(1), now)).isFalse();
    }
}
