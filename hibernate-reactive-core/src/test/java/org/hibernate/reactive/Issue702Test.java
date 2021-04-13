/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive;

import java.io.Serializable;
import java.time.OffsetDateTime;
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

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.unit.TestContext;
import org.assertj.core.api.Assertions;

public class Issue702Test extends BaseReactiveTest {
	private Campaign theCampaign;

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
	public void populateDb(TestContext context) {
		theCampaign = new Campaign();
		theCampaign.setSchedule( new ExecutionDate(OffsetDateTime.now() ));

		Mutiny.Session session = openMutinySession();
		test( context, session.persist( theCampaign ).call( session::flush ) );
	}

	@After
	public void cleanDB(TestContext context) {
		test( context, deleteEntities( "Campaign", "Schedule" ) );
	}

	@Test
	public void testUpdateExecutionDate(TestContext context) {
		Mutiny.Session session = openMutinySession();
		ExecutionDate execDate = (ExecutionDate)theCampaign.getSchedule();
		test(context,
			 session.find( Campaign.class, theCampaign.getId() )
					 .invoke( foundCampaign -> {
					 	ExecutionDate ed = new ExecutionDate(OffsetDateTime.now());
					 	foundCampaign.setSchedule( new ExecutionDate(OffsetDateTime.now() ) );
					 } )
					 .call( session::flush )
					 .chain( () -> openMutinySession().find( Campaign.class, theCampaign.getId() ) )
					 .invoke( updatedCampaign -> {
							 Assertions.assertThat( updatedCampaign ).isNotNull();
							 Assertions.assertThat( updatedCampaign.getSchedule() ).isNotNull();
							 Assertions.assertThat(
									( (ExecutionDate) updatedCampaign.getSchedule() ).getStart() ).isNotEqualTo( execDate.getStart() );
						 } )
		);
	}

	@Entity (name="Campaign")
	public static class Campaign implements Serializable {

		@Id @GeneratedValue
		private Integer id;

		@OneToOne(mappedBy = "campaign", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
		@JsonIgnore
		private Schedule schedule;

		public Campaign() {
		}

		// Getters and setters
		public void setSchedule(Schedule schedule) {
			this.schedule = schedule;
			this.schedule.setCampaign( this );
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
	}

	@Entity (name="ExecutionDate")
	@DiscriminatorValue("EXECUTION_DATE")
	public static class ExecutionDate extends Schedule {

		@Column(name = "start_date")
		private OffsetDateTime start;

		public ExecutionDate() {
		}

		public ExecutionDate( OffsetDateTime start ) {
			this.start = start;
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
