package com.notare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Tests for NotareApplication. */
@SpringBootTest(properties = "notare.transcription.engine=stub")
class NotareApplicationTests {

	@Test
	void contextLoads() {
	}

}
