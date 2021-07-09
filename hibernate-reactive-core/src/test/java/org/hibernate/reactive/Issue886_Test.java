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

import org.junit.After;
import org.junit.Test;

import io.vertx.ext.unit.TestContext;

public class Issue886_Test extends BaseReactiveTest {

	@Override
	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		configuration.addAnnotatedClass( SampleEntity.class );
		configuration.addAnnotatedClass( SampleJoinEntity.class );
		return configuration;
	}

	@After
	public void cleanDb(TestContext context) {
		test( context, deleteEntities( "SampleEntity", "SampleJoinEntity" ) );
	}

	@Test
	public void testInsertAndSelect(TestContext context) {
		SampleEntity sampleEntity = new SampleEntity();
		sampleEntity.sampleField = "test";
		SampleJoinEntity sampleJoinEntity = new SampleJoinEntity();
		sampleJoinEntity.sampleEntity = sampleEntity;

		test(
				context,
				getMutinySessionFactory()
						.withTransaction( (s, t) -> s.persistAll( sampleEntity, sampleJoinEntity ) )
						.chain( this::openMutinySession )
						.chain( s -> s.find( SampleJoinEntity.class, sampleJoinEntity.id )
								.call( (entity) -> s.fetch( entity.sampleEntity ) ) )
						.chain( this::openMutinySession )
						.chain( s2 -> s2.find( SampleJoinEntity.class, sampleJoinEntity.id ) )
						.chain( result -> {
							SampleEntity sampleEntityFromDatabase = result.sampleEntity;
							sampleEntityFromDatabase.sampleField = "updatedTest";
							return getMutinySessionFactory().withStatelessSession(
									s3 -> s3.withTransaction(
											tx -> s3.update( sampleEntityFromDatabase )
									) );
						} )
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
	}

	@Entity(name = "SampleJoinEntity")
	@Table(name = "sample_join_entities")
	public static class SampleJoinEntity implements Serializable {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		public Long id;

		@ManyToOne(fetch = FetchType.EAGER)
		@JoinColumn(name = "sample_entity_id", referencedColumnName = "id")
		public SampleEntity sampleEntity;
	}
}
