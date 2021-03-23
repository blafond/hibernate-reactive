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

import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.stage.Stage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.Assertions;

public class J5_SimpleElementCollectionTest extends BaseReactiveJupiter {

	@BeforeAll
	protected static void beforeAll( ) {
		getConfiguration().addAnnotatedClass( Person.class );
	}

	@Test
	public void persistWithMutinyAPI(Vertx vertx, VertxTestContext context) {
		vertx.runOnContext( event -> {
			Mutiny.Session session = openMutinySession();

			Person johnny = new Person( 999, "Johnny English", Arrays.asList( "888", "555" ) );

			test (
					context,
					session.persist( johnny )
							.call( session::flush )
							.chain( () -> openMutinySession().find( Person.class, johnny.getId() ) )
							.invoke( found -> assertPhones( found, "888", "555" ) )
			);
		} );
	}

	@Test
	public void findEntityWithElementCollectionWithStageAPI(Vertx vertx, VertxTestContext context) {
		vertx.runOnContext( event -> {
			Stage.Session session = openStageSession();

			List<String> phones = Arrays.asList( "999-999-9999", "111-111-1111", "123-456-7890" );
			Person thePerson = new Person( 7242000, "Claude", phones );

			test (
					context,
					session.persist( thePerson )
							.thenCompose( v -> session.flush() )
							.thenCompose( v -> openStageSession().find( Person.class, thePerson.getId() ) )
							.thenAccept( found -> assertPhones( found, "999-999-9999", "111-111-1111", "123-456-7890" ) )
			);
		} );
	}

	@Test
	public void findEntityWithElementCollectionWithMutinyAPI( Vertx vertx, VertxTestContext context ) {
		vertx.runOnContext( event -> {
			Mutiny.Session session = openMutinySession();

			List<String> phones = Arrays.asList( "999-999-9999", "111-111-1111", "123-456-7890" );
			Person thePerson = new Person( 8242000, "Claude", phones );

			test (
					context,
					session.persist( thePerson )
							.call( session::flush )
							.chain( () -> openMutinySession().find( Person.class, thePerson.getId() )
									.invoke( found -> assertPhones( found, "999-999-9999", "111-111-1111", "123-456-7890" ) )
							) );
		} );
	}

	/**
	 * Utility method to check the content of the collection of elements.
	 * It sorts the expected and actual phones before comparing.
	 */
	private static void assertPhones(Person person, String... expectedPhones) {
		Assertions.assertThat( person ).isNotNull();
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
			sb.append( ", ").append( phones );
			return sb.toString();
		}
	}
}
