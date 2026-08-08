package com.miguel.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "biblioteca.managed-process", havingValue = "true")
public class ManagedProcessShutdown implements SmartLifecycle {
	private static final Logger LOGGER = LoggerFactory.getLogger(ManagedProcessShutdown.class);
	private final ConfigurableApplicationContext applicationContext;
	private volatile boolean running;

	public ManagedProcessShutdown(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void start() {
		running = true;
		Thread.ofPlatform()
				.daemon(true)
				.name("biblioteca-managed-shutdown")
				.start(this::esperarOrdenDeCierre);
	}

	private void esperarOrdenDeCierre() {
		try (var reader = new BufferedReader(
				new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			String orden = reader.readLine();
			if (running && "shutdown".equals(orden)) {
				applicationContext.close();
			}
		} catch (Exception error) {
			if (running) {
				LOGGER.warn("No se pudo escuchar la orden de cierre del proceso administrado", error);
			}
		}
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
