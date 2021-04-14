/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.testing.DatabaseSelectionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.unit.TestContext;

import static org.hibernate.reactive.containers.DatabaseConfiguration.DBType.POSTGRESQL;

public class Issue702Test extends BaseReactiveTest {

	@Rule
	public DatabaseSelectionRule rule = DatabaseSelectionRule.runOnlyFor( POSTGRESQL );

	private Campaign theCampaign;

	private SessionFactory ormFactory;

	@Override
	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		configuration.addAnnotatedClass( Campaign.class );
		configuration.addAnnotatedClass( ExecutionDate.class );
		configuration.addAnnotatedClass( Schedule.class );

		configuration.setProperty( AvailableSettings.STATEMENT_BATCH_SIZE, "5");
		return configuration;
	}

	@Before
	public void prepareOrmFactory() {
		Configuration configuration = constructConfiguration();
		configuration.setProperty( Settings.DRIVER, "org.postgresql.Driver" );
		configuration.setProperty( Settings.DIALECT, "org.hibernate.dialect.PostgreSQL95Dialect" );

		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder()
				.applySettings( configuration.getProperties() );

		StandardServiceRegistry registry = builder.build();
		ormFactory = configuration.buildSessionFactory( registry );
	}

	@Before
	public void populateDb(TestContext context) {
		theCampaign = new Campaign();
		theCampaign.setSchedule( new ExecutionDate(OffsetDateTime.now(), "ALPHA") );

		Mutiny.Session session = openMutinySession();
		test( context, session.persist( theCampaign ).call( session::flush ) );
	}

	@After
	public void cleanDB(TestContext context) {
		System.out.println("  ========= CLEAN DB ==================================");
		test( context, deleteEntities( "Campaign", "Schedule" ) );
	}

	@Test
	public void testUpdateExecutionDate(TestContext context) {
		System.out.println("  ========= START REACTIVE TEST ==================================");
		Mutiny.Session session = openMutinySession();
		ExecutionDate execDate = (ExecutionDate)theCampaign.getSchedule();
		test(context,
			 session.find( Campaign.class, theCampaign.getId() )
					 .invoke( foundCampaign -> {
						 System.out.println("  ========= SETTING NEW SCHEDULE ==================================");
						 foundCampaign.setSchedule( new ExecutionDate(OffsetDateTime.now(), "BETA") );
					 } )
					 .call( () -> {
						 System.out.println("  ========= FLUSHING NEW SCHEDULE ==================================");
					 	 return session.flush();
					 } )
//					 .chain( () -> openMutinySession().find( Campaign.class, theCampaign.getId() ); )
//					 .invoke( updatedCampaign -> {
//							 Assertions.assertThat( updatedCampaign ).isNotNull();
//							 Assertions.assertThat( updatedCampaign.getSchedule() ).isNotNull();
//							 Assertions.assertThat(
//									( updatedCampaign.getSchedule() ).getCodeName() ).isNotEqualTo( execDate.getCodeName() );
//						 } )
		);
	}

	@Test
	public void testUpdateExecutionDateWithORM(TestContext context) {
		System.out.println("  ========= START ORM TEST ==================================");
		Arrays.asList()
		Session session = ormFactory.openSession();
		session.beginTransaction();
		final Campaign campaign = session.getReference( Campaign.class, theCampaign.getId() );
		campaign.setSchedule( new ExecutionDate( OffsetDateTime.now(), "BETA" ) );
		session.getTransaction().commit();
//		session.close();

//		session = ormFactory.openSession();
		session.clear();
		session.beginTransaction();

//		campaign = session.load( Campaign.class, theCam				paign.getId() );
		campaign.setSchedule( new ExecutionDate( OffsetDateTime.now(), "OMEGA" ) );
		session.getTransaction().commit();
//		session.close();

//		session = ormFactory.openSession();
		session.clear();
		session.beginTransaction();
//		Campaign campaign3 = session.load( Campaign.class, theCampaign.getId() );
		campaign.setSchedule( new ExecutionDate( OffsetDateTime.now(), "RHO" ) );
		session.getTransaction().commit();
//		session.close();

		System.out.println("  ========= VERIFY NEW SCHEDULE ==================================");
		Stage.Session stageSession = openSession();
		test( context, stageSession.find( Campaign.class, theCampaign.getId() )
				.thenAccept( entityFound -> context.assertEquals(
						campaign.getSchedule().getCodeName(),
						entityFound.getSchedule().getCodeName()
				) )
		);
	}

	@Entity (name="Campaign")
	public static class Campaign implements Serializable {

		@Id @GeneratedValue
		private Integer id;

		@OneToOne(mappedBy = "campaign",  cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
		@JsonIgnore
		private Schedule schedule;

		public Campaign() {
		}

		// Getters and setters
		public void setSchedule(Schedule schedule) {
			this.schedule = schedule;
			if( schedule != null ) {
				this.schedule.setCampaign( this );
			}
		}

		public Schedule getSchedule() {
			return this.schedule;
		}

		public Integer getId() {
			return id;
		}
	}

	@Entity (name="Schedule")
	@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
	@DiscriminatorColumn(name = "schedule_type", discriminatorType = DiscriminatorType.STRING)
	public static abstract class Schedule implements Serializable {
		@Id
		@Column(name = "id")
		private String id = UUID.randomUUID().toString();

		@Column(name = "code_name")
		private String code_name;

		@OneToOne
		@JoinColumn(name = "campaign_id")
		@JsonIgnore
		private Campaign campaign;

		// Getters and setters
		public String getId() {
			return id;
		}

		public void setCampaign(Campaign campaign) {
			this.campaign = campaign;
		}

		public Campaign getCampaign() {
			return campaign;
		}

		public void setCodeName(String code_name) {
			this.code_name = code_name;
		}

		public String getCodeName() {
			return code_name;
		}
	}

	@Entity (name="ExecutionDate")
	@DiscriminatorValue("EXECUTION_DATE")
	public static class ExecutionDate extends Schedule {

		@Column(name = "start_date")
		private OffsetDateTime start;

		public ExecutionDate() {
		}

		public ExecutionDate( OffsetDateTime start, String code_name ) {
			this.start = start;
			setCodeName( code_name );
		}

		// Getters and setters

		public Schedule setStart(OffsetDateTime start) {
			this.start = start;
			return null;
		}

		public OffsetDateTime getStart() {
			return start;
		}
	}

}
