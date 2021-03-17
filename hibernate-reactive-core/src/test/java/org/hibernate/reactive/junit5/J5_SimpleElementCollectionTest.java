/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.junit5;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.stage.Stage;

import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.ext.unit.TestContext;
import org.assertj.core.api.Assertions;

@ExtendWith(PersonParameterResolver.class)
public class J5_SimpleElementCollectionTest  extends BaseReactiveJupiter {

	private Person thePerson;

	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		configuration.addAnnotatedClass( Person.class );
		return configuration;
	}

	@Before
	public void populateDb(TestContext context) {
		List<String> phones = Arrays.asList( "999-999-9999", "111-111-1111", "123-456-7890" );
		thePerson = new Person( 7242000, "Claude", phones );

		Mutiny.Session session = openMutinySession();
		test( context, session.persist( thePerson ).call( session::flush ) );
	}

	@Test
	public void findEntityWithElementCollectionWithStageAPI(TestContext context) {
		Stage.Session session = openSession();

		test (
				context,
				session.find( Person.class, thePerson.getId() )
						.thenAccept( found -> assertPhones( context, found, "999-999-9999", "111-111-1111", "123-456-7890" ) )
		);
	}

	/**
	 * Utility method to check the content of the collection of elements.
	 * It sorts the expected and actual phones before comparing.
	 */
	private static void assertPhones(TestContext context, Person person, String... expectedPhones) {
		context.assertNotNull( person );
		String[] sortedExpected = Arrays.stream( expectedPhones ).sorted()
				.sorted( String.CASE_INSENSITIVE_ORDER )
				.collect( Collectors.toList() )
				.toArray( new String[expectedPhones.length] );
		List<String> sortedActual = person.getPhones().stream()
				.sorted( String.CASE_INSENSITIVE_ORDER )
				.collect( Collectors.toList() );
		Assertions.assertThat( sortedActual )
				.containsExactly( sortedExpected );
	}

	@Entity(name = "Person")
	@Table(name = "Person")
	static class Person {
		@Id
		private Integer id;
		private String name;

		@ElementCollection(fetch = FetchType.EAGER)
		private List<String> phones;

		public Person() {
		}

		public Person(Integer id, String name, List<String> phones) {
			this.id = id;
			this.name = name;
			this.phones = phones;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setPhones(List<String> phones) {
			this.phones = phones;
		}

		public List<String> getPhones() {
			return phones;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			Person person = (Person) o;
			return Objects.equals( name, person.name );
		}

		@Override
		public int hashCode() {
			return Objects.hash( name );
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append( id );
			sb.append( ", " ).append( name );
			sb.append( ", " ).append( phones );
			return sb.toString();
		}
	}
}
