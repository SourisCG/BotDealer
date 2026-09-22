/*
 * BotDealer - Copyright (C) 2026 Sebastián García - GPL-3.0 (see LICENSE).
 */
package souris.botdealer.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON mapper used by the Discord REST calls and by the settings export/import.
 *
 * <p>Defined explicitly instead of relying on {@code JacksonAutoConfiguration}: Spring
 * Boot 4 is modular and only auto-configures Jackson when a JSON starter is present.
 * Owning the mapper also lets us keep lenient parsing (Discord adds fields often) and
 * ISO-8601 timestamps for the exported configuration.</p>
 */
@Configuration
public class JacksonConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();
	}
}
