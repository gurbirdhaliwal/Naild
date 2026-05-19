package com.knappi.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApplicationTests {

	@Test
	void applicationClassIsPresent() {
		assertThat(SecurityApplication.class).isNotNull();
	}

}
