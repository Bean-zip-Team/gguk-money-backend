package com.ggukmoney.beanzip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEnvironmentSmokeTest {

    @Test
    void junitPlatformLoadsTestClasses() {
        assertThat(TestEnvironmentSmokeTest.class.getName())
                .isEqualTo("com.ggukmoney.beanzip.TestEnvironmentSmokeTest");
    }
}
