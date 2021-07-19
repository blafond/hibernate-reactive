/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive;


import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.cfg.Configuration;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.reactive.provider.Settings;

import org.junit.After;
import org.junit.Test;

import io.vertx.ext.unit.TestContext;
import org.assertj.core.api.Assertions;

public class Issue886_Test extends BaseReactiveTest {

	@Override
	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		configuration.addAnnotatedClass( SampleEntity.class );
		configuration.addAnnotatedClass( SampleJoinEntity.class );

		configuration.setProperty( Settings.SHOW_SQL, System.getProperty(Settings.SHOW_SQL, "true") );
		configuration.setProperty( Settings.FORMAT_SQL, System.getProperty(Settings.FORMAT_SQL, "false") );
		configuration.setProperty( Settings.HIGHLIGHT_SQL, System.getProperty(Settings.HIGHLIGHT_SQL, "true") );
		return configuration;
	}

	@After
	public void cleanDb(TestContext context) {
		test( context, deleteEntities( "SampleEntity", "SampleJoinEntity" ) );
	}

	@Test
	public void testONE(TestContext context) {
		final String ORIGINAL_FIELD = "I am the ORIGINAL field value";
		final String UPDATED_FIELD = "I am the UPDATED field value";

		SampleEntity sampleEntity = new SampleEntity();
		sampleEntity.sampleField = ORIGINAL_FIELD;
		SampleJoinEntity sampleJoinEntity = new SampleJoinEntity();
		sampleJoinEntity.sampleEntity = sampleEntity;

		test( context, getMutinySessionFactory().withTransaction( (s, t) -> s
				.persist( sampleEntity ) // PERSIST primary entity
				.call( s::flush )
				.chain( () -> s.find( SampleEntity.class, sampleEntity.id ) )
				.invoke( found -> context.assertEquals( sampleEntity, found ) ) )
				.chain( () -> getMutinySessionFactory().withTransaction( (session, transaction) -> session
						.persist( sampleJoinEntity ) // PERSIST associated/joined entity
						.call( session::flush )
						.chain( () -> session.find( SampleJoinEntity.class, sampleJoinEntity.id ) )
						.invoke( found -> context.assertEquals( sampleJoinEntity, found )) )
				)
				.chain( result -> getMutinySessionFactory().withStatelessTransaction(
						(s, t) -> {
							context.assertFalse( result instanceof HibernateProxy );
							result.sampleEntity.sampleField = UPDATED_FIELD;
							return s.withTransaction( tx -> s.update( result.sampleEntity )
									.chain( () -> s.refresh( result.sampleEntity )));
						} )
				)
				.chain( () -> getMutinySessionFactory()
						.withSession( session -> session.find(
								SampleEntity.class, sampleEntity.id )
								.chain( se -> session.fetch( se ) )
								.invoke( result -> {
									context.assertFalse( result instanceof HibernateProxy );
									Assertions.assertThat( result ).isNotInstanceOf( HibernateProxy.class );
									context.assertEquals( UPDATED_FIELD, result.sampleField );
								} )
						)
				)
				.chain( () -> getMutinySessionFactory()
						.withSession( session -> session.find(
								SampleJoinEntity.class, sampleJoinEntity.id )
								.chain( () -> session.fetch( sampleJoinEntity) )
								.invoke( found -> context.assertEquals( sampleJoinEntity, found )
								) )
						)

		);
	}

	@Entity(name = "SampleEntity")
	@Table(name = "sample_entities")
	public static class SampleEntity implements Serializable {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		public Long id;

		@Column(name = "sample_field")
		public String sampleField;

		public void setSampleField(String sampleField) {
			this.sampleField = sampleField;
		}

		public String getSampleField() {
			return this.sampleField;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " ID: " + id + " sampleField: " + sampleField;
		}

	}

	@Entity(name = "SampleJoinEntity")
	@Table(name = "sample_join_entities")
	public static class SampleJoinEntity implements Serializable {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		public Long id;

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "sample_entity_id", referencedColumnName = "id")
		public SampleEntity sampleEntity;

		public SampleEntity getSampleEntity() {
			return this.sampleEntity;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " ID: " + id + " sampleEntity: " + sampleEntity;
		}
	}
}
