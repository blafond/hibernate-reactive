/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.testing;

import java.util.concurrent.CompletionStage;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
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

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import static org.hibernate.reactive.containers.DatabaseConfiguration.dbType;

@ExtendWith({ VertxExtension.class, TestContextParameterResolver.class})
public class SessionFactoryManager {

	private SessionFactory baseSessionFactory;
	private Stage.SessionFactory stageSessionFactory;
	private Mutiny.SessionFactory muntinySessionFactory;
	private ReactiveConnectionPool poolProvider;
	private AutoCloseable session;
	private Configuration configuration;
	private ReactiveConnection connection;

	public SessionFactoryManager() {

	}

	public void start(Vertx vertx, VertxTestContext context) {
		configuration = constructConfiguration();
		StandardServiceRegistryBuilder builder = new ReactiveServiceRegistryBuilder()
				.addService( VertxInstance.class, (VertxInstance) () -> vertx )
				.applySettings( configuration.getProperties() );
		addServices( builder );
		StandardServiceRegistry registry = builder.build();
		configureServices( registry );

		// schema generation is a blocking operation and so it causes an
		// exception when run on the Vert.x event loop. So call it using
		// Vertx.executeBlocking()
		vertx.<SessionFactory>executeBlocking(
				p -> p.complete( configuration.buildSessionFactory( registry ) ),
				r -> {
					if ( r.failed() ) {
						context.failNow( r.cause() );
					}
					else {
						baseSessionFactory = r.result();
						poolProvider = registry.getService( ReactiveConnectionPool.class );
						context.completeNow();
					}
				}
		);
	}

	protected void addServices(StandardServiceRegistryBuilder builder) {}

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

	public Configuration getConfiguration() {
		return configuration;
	}

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

	private void resetSessionFactory() {
		closeSessionFactory();
		stageSessionFactory = getStageSessionFactory();
	}

	protected Stage.SessionFactory getStageSessionFactory()  {
		if( stageSessionFactory == null ) {
			stageSessionFactory =  baseSessionFactory.unwrap( Stage.SessionFactory.class );
		}
		return stageSessionFactory;
	}


	protected Mutiny.SessionFactory getMutinySessionFactory()  {
		if( muntinySessionFactory == null ) {
			muntinySessionFactory =  baseSessionFactory.unwrap( Mutiny.SessionFactory.class );
		}

		return muntinySessionFactory;
	}

	private void closeSessionFactory() {
		if( stageSessionFactory != null ) {
			stageSessionFactory.close();
		}

		if( muntinySessionFactory != null ) {
			muntinySessionFactory.close();
		}
	}

	public void stop() {
		closeSessionFactory();
	}

	public CompletionStage<ReactiveConnection> getConnection() {
		return poolProvider.getConnection().thenApply( c -> this.connection = c );
	}

	/**
	 * Close the existing open session and create a new {@link Stage.Session}
	 *
	 * @return a new Stage.Session
	 */
	public Stage.Session openStageSession() {
		if ( session != null && session.isOpen() ) {
			session.close();
		}
		Stage.Session newSession = getStageSessionFactory().openSession();
		this.session = (AutoCloseable) newSession;
		return (Stage.Session)session;
	}

	/**
	 * Close the existing open session and create a new {@link Stage.Session}
	 *
	 * @return a new Stage.Session
	 */
	public Mutiny.Session openMutinySession() {
		if ( session != null && session.isOpen() ) {
			session.close();
		}
		Mutiny.Session newSession = getMutinySessionFactory().openSession();
		this.session = (AutoCloseable) newSession;
		return (Mutiny.Session)session;
	}
}
