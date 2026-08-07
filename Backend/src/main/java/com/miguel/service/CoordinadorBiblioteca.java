package com.miguel.service;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class CoordinadorBiblioteca {
	private final ReentrantLock bloqueo = new ReentrantLock(true);

	public <T> T ejecutarExclusivo(Supplier<T> operacion) {
		bloqueo.lock();
		try {
			return operacion.get();
		} finally {
			bloqueo.unlock();
		}
	}
}
