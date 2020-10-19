/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.hibernate.cfg.Configuration;

import org.junit.Test;

import io.vertx.ext.unit.TestContext;

import static org.hibernate.reactive.util.impl.CompletionStages.completedFuture;
import static org.hibernate.reactive.util.impl.CompletionStages.voidFuture;

public class LazyManyToManyTest extends BaseReactiveTest {

	@Override
	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		configuration.addAnnotatedClass( LazyManyToManyTest.Group.class );
		configuration.addAnnotatedClass( LazyManyToManyTest.User.class );
		return configuration;
	}

	@Test
	public void persistTwoUsersForGroup(TestContext context) {
		final Group groupAdmin = new Group("Administrator Group", 1000);
		final Group groupGuest = new Group("Guest Group", 2000);

		final User user1 = new User("Tom", "tomcat", "tom@codejava.net", 1111);
		final User user2 = new User("Mary", "mary", "mary@codejava.net", 2222);

		groupAdmin.addUser(user1);
		groupAdmin.addUser(user2);

		groupGuest.addUser(user1);

		user1.addGroup(groupAdmin);
		user2.addGroup(groupAdmin);
		user1.addGroup(groupGuest);

		test(
				context,
				completedFuture( openSession() )
						.thenCompose( s -> voidFuture()
								.thenCompose( v -> s.persist( groupAdmin ) )
								.thenCompose( v -> s.persist( groupGuest ) )
								.thenCompose( v -> s.persist( user1 ) )
								.thenCompose( v -> s.persist( user2 ) )
								.thenCompose( v -> s.flush() )
						)
						.thenApply( v -> openSession() )
						.thenCompose( s -> s.find( LazyManyToManyTest.Group.class, groupAdmin.getId() ) )
						.thenAccept( optionalGroup -> {
							context.assertNotNull( optionalGroup );
							context.assertEquals( 2, optionalGroup.getUsers().size() );
							context.assertTrue( optionalGroup.getUsers().contains( user1 ) );
							context.assertTrue( optionalGroup.getUsers().contains( user2 ) );
						} )
		);
	}

	@Entity
	@Table(name = "GROUPS")
	public class Group {
		private Integer id;
		private String name;

		@ManyToMany(cascade = CascadeType.ALL, mappedBy="users", fetch=FetchType.LAZY)
		@JoinTable(
				name = "USERS_GROUPS",
				joinColumns = @JoinColumn(name = "GROUP_ID"),
				inverseJoinColumns = @JoinColumn(name = "USER_ID")
		)
		private Set<User> users = new HashSet<User>();

		public Group(String name, Integer id) {
			this.name = name;
			this.id = id;
		}

		public void addUser(User user) {
			this.users.add(user);
		}

		public void setId(Integer id) {
			this.id = id;
		}

		@Id
		@GeneratedValue
		@Column(name = "GROUP_ID")
		public Integer getId() {
			return id;
		}

		public void setUsers(Set<User> users) {
			this.users.clear();
			this.users = users;
		}

		public Set<User> getUsers() {
			return users;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Entity
	@Table(name = "USERS")
	public class User {
		private Integer id;
		private String username;
		private String password;
		private String email;

		@ManyToMany(mappedBy="groups", fetch=FetchType.LAZY)
		private final Set<Group> groups = new HashSet<Group>();

		public User(String username, String password, String email, Integer id) {
			this.username = username;
			this.password = password;
			this.email = email;
			this.id = id;
		}

		public void addGroup(Group group) {
			this.groups.add(group);
		}

		public void setId(Integer id) {
			this.id = id;
		}

		@Id
		@GeneratedValue
		@Column(name = "USER_ID")
		public Integer getId() {
			return id;
		}

		public Set<Group> getGroups() {
			return groups;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}
	}
}
