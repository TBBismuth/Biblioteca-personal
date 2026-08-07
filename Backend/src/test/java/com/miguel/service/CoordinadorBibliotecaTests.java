package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class CoordinadorBibliotecaTests {
	@Test
	void serializaOperacionesSobreLaBiblioteca() throws Exception {
		CoordinadorBiblioteca coordinador = new CoordinadorBiblioteca();
		CountDownLatch primeraDentro = new CountDownLatch(1);
		CountDownLatch liberarPrimera = new CountDownLatch(1);
		AtomicBoolean segundaDentro = new AtomicBoolean(false);

		Thread primera = Thread.startVirtualThread(() -> coordinador.ejecutarExclusivo(() -> {
			primeraDentro.countDown();
			try {
				liberarPrimera.await();
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(error);
			}
			return null;
		}));
		assertThat(primeraDentro.await(2, TimeUnit.SECONDS)).isTrue();

		Thread segunda = Thread.startVirtualThread(() -> coordinador.ejecutarExclusivo(() -> {
			segundaDentro.set(true);
			return null;
		}));
		Thread.sleep(50);
		assertThat(segundaDentro).isFalse();

		liberarPrimera.countDown();
		primera.join();
		segunda.join();
		assertThat(segundaDentro).isTrue();
	}
}
