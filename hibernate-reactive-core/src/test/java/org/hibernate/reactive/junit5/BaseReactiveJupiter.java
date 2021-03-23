/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.junit5;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.pool.ReactiveConnection;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.testing.SessionFactoryManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;


import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({ VertxExtension.class})
@Timeout(value = 10, timeUnit = TimeUnit.MINUTES)
public abstract class BaseReactiveJupiter {

	private final static SessionFactoryManager factoryManager = new SessionFactoryManager();

	protected static void test(VertxTestContext context, CompletionStage<?> work) {
		// this will be added to TestContext in the next vert.x release
		work.whenComplete( (res, err) -> {
			if ( res instanceof Stage.Session ) {
				Stage.Session s = (Stage.Session) res;
				if ( s.isOpen() ) {
					s.close();
				}
			}
			if ( err != null ) {
				context.failNow( err );
			} else {
				context.completeNow();
			}
		} );
	}

	protected static void test(VertxTestContext context, Uni<?> uni) {
		Checkpoint ckPoint = context.checkpoint();
		uni.subscribe().with(
				res -> {
					if ( res instanceof Mutiny.Session) {
						Mutiny.Session session = (Mutiny.Session) res;
						if ( session.isOpen() ) {
							session.close();
						}
					}
					context.completeNow();
				},
				throwable -> context.failNow( throwable )
		);
	}

	@BeforeAll
	public static void startSessionFactoryManager(Vertx vertx, VertxTestContext context) {
		factoryManager.start(vertx, context);
	}

	@AfterAll
	public static void stopSessionFactoryManager() {
		factoryManager.stop();
	}

	public static Configuration getConfiguration( ) {
		return factoryManager.getConfiguration();
	}

	protected static CompletionStage<ReactiveConnection> connection() {
		return factoryManager.getConnection();
	}

	protected static void addServices(StandardServiceRegistryBuilder builder) {}

	/**
	 * Close the existing open session and create a new {@link Stage.Session}
	 *
	 * @return a new Stage.Session
	 */
	protected Stage.Session openStageSession() {
		return factoryManager.openStageSession();
	}

	/**
	 * Close the existing open session and create a new {@link Stage.Session}
	 *
	 * @return a new Stage.Session
	 */
	protected Mutiny.Session openMutinySession() {
		return factoryManager.openMutinySession();
	}

}
