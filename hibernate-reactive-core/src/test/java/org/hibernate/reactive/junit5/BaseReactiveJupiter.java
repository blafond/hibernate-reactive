/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.junit5;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.reactive.common.AutoCloseable;
import org.hibernate.reactive.containers.DatabaseConfiguration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.pool.ReactiveConnection;
import org.hibernate.reactive.pool.ReactiveConnectionPool;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.reactive.provider.service.ReactiveGenerationTarget;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.vertx.VertxInstance;
import org.hibernate.tool.schema.spi.SchemaManagementTool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;


import org.jboss.logging.Logger;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import static org.hibernate.reactive.containers.DatabaseConfiguration.dbType;

@ExtendWith({ VertxExtension.class, TestContextParameterResolver.class })
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public abstract class BaseReactiveJupiter {

	private AutoCloseable session;
	private ReactiveConnection connection;
	private org.hibernate.SessionFactory sessionFactory;
	private ReactiveConnectionPool poolProvider;

	protected static void test(VertxTestContext context, CompletionStage<?> work) {
		// this will be added to TestContext in the next vert.x release
		Checkpoint ckPoint = context.checkpoint();
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
				context.completed();
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
					context.completed();
				},
				throwable -> context.failNow( throwable )
		);
	}

	private static boolean doneTablespace;

	protected Configuration constructConfiguration() {
		Configuration configuration = new Configuration();
		configuration.setProperty( Settings.HBM2DDL_AUTO, "create" );
		configuration.setProperty( Settings.URL, DatabaseConfiguration.getJdbcUrl() );
		if ( DatabaseConfiguration.dbType() == DatabaseConfiguration.DBType.DB2 && !doneTablespace ) {
			configuration.setProperty(Settings.HBM2DDL_IMPORT_FILES, "/db2.sql");
			doneTablespace = true;
		}
		//Use JAVA_TOOL_OPTIONS='-Dhibernate.show_sql=true'
		configuration.setProperty( Settings.SHOW_SQL, System.getProperty(Settings.SHOW_SQL, "false") );
		configuration.setProperty( Settings.FORMAT_SQL, System.getProperty(Settings.FORMAT_SQL, "false") );
		configuration.setProperty( Settings.HIGHLIGHT_SQL, System.getProperty(Settings.HIGHLIGHT_SQL, "true") );
		return configuration;
	}



	@BeforeEach
	public void before(Vertx vertx, VertxTestContext context) {
		Configuration configuration = constructConfiguration();
		StandardServiceRegistryBuilder builder = new ReactiveServiceRegistryBuilder()
				.addService( VertxInstance.class, (VertxInstance) () -> vertx )
				.applySettings( configuration.getProperties() );
		addServices( builder );
		StandardServiceRegistry registry = builder.build();
		configureServices( registry );

		// schema generation is a blocking operation and so it causes an
		// exception when run on the Vert.x event loop. So call it using
		// Vertx.executeBlocking()
		Checkpoint ckPoint = context.checkpoint();
		vertx.<SessionFactory>executeBlocking(
				p -> p.complete( configuration.buildSessionFactory( registry ) ),
				r -> {
					if ( r.failed() ) {
						context.failNow( r.cause() );
					}
					else {
						sessionFactory = r.result();
						poolProvider = registry.getService( ReactiveConnectionPool.class );
						context.completed();
						ckPoint.flag();
					}
				}
		);
	}

	protected void addServices(StandardServiceRegistryBuilder builder) {}

	/*
	 * MySQL doesn't implement 'drop table cascade constraints'.
	 *
	 * The reason this is a problem in our test suite is that we
	 * have lots of different schemas for the "same" table: Pig, Author, Book.
	 * A user would surely only have one schema for each table.
	 */
	protected void configureServices(StandardServiceRegistry registry) {
		if ( dbType() == DatabaseConfiguration.DBType.MYSQL ) {
			registry.getService( ConnectionProvider.class ); //force the NoJdbcConnectionProvider to load first
			registry.getService( SchemaManagementTool.class )
					.setCustomDatabaseGenerationTarget( new ReactiveGenerationTarget( registry) {
						@Override
						public void prepare() {
							super.prepare();
							accept("set foreign_key_checks = 0");
						}
						@Override
						public void release() {
							accept("set foreign_key_checks = 1");
							super.release();
						}
					} );
		}
	}

	@AfterEach
	public void after(VertxTestContext context) {
		if ( session != null && session.isOpen() ) {
			session.close();
			session = null;
		}
		if ( connection != null ) {
			try {
				connection.close();
			}
			catch (Exception e) {}
			finally {
				connection = null;
			}
		}

		if ( sessionFactory != null ) {
			sessionFactory.close();
		}
	}

	protected Stage.SessionFactory getSessionFactory()  {
		return sessionFactory.unwrap( Stage.SessionFactory.class );
	}

	/**
	 * Close the existing open session and create a new {@link Stage.Session}
	 *
	 * @return a new Stage.Session
	 */
	protected Stage.Session openSession() {
		if ( session != null && session.isOpen() ) {
			session.close();
		}
		Stage.Session newSession = getSessionFactory().openSession();
		this.session = newSession;
		return newSession;
	}

	protected CompletionStage<ReactiveConnection> connection() {
		return poolProvider.getConnection().thenApply( c -> connection = c );
	}

	/**
	 * Close the existing open session and create a new {@link Mutiny.Session}
	 *
	 * @return a new Mutiny.Session
	 */
	protected Mutiny.Session openMutinySession() {
		if ( session != null ) {
			session.close();
		}
		Mutiny.Session newSession = getMutinySessionFactory().openSession();
		this.session = newSession;
		return newSession;
	}

	protected Mutiny.SessionFactory getMutinySessionFactory() {
		return sessionFactory.unwrap( Mutiny.SessionFactory.class );
	}

}
