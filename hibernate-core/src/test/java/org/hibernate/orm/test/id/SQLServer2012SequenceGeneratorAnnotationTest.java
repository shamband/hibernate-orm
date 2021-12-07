/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

import org.hibernate.dialect.SQLServerDialect;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


@DomainModel(
		annotatedClasses = SQLServer2012SequenceGeneratorAnnotationTest.Person.class
)
@SessionFactory
public class SQLServer2012SequenceGeneratorAnnotationTest {

	@AfterEach
	public void tearDown(SessionFactoryScope scope){
		scope.inTransaction(
				session ->
					session.createQuery( "delete from Person" ).executeUpdate()
		);
	}

	/**
	 * SQL server requires that sequence be initialized to something other than the minimum value for the type
	 * (e.g., Long.MIN_VALUE). For generator = "sequence", the initial value must be provided as a parameter.
	 * For this test, the sequence is initialized to 10.
	 */
	@Test
	@TestForIssue(jiraKey = "HHH-8814")
	@RequiresDialect(value = SQLServerDialect.class, majorVersion = 11)
	public void testStartOfSequence(SessionFactoryScope scope) {
		final Person person = scope.fromTransaction( session -> {
			final Person _person = new Person();
			session.persist(_person);
			return _person;
		} );

		assertEquals(10, person.getId());
	}

	@Entity(name = "Person")
	public static class Person {

		@Id
		@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq")
		@SequenceGenerator(initialValue = 10, name = "seq")
		private long id;

		public long getId() {
			return id;
		}

		public void setId(final long id) {
			this.id = id;
		}

	}

}
