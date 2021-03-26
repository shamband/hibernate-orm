/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.orm.test.collection.delayedOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityExistsException;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.Hibernate;
import org.hibernate.collection.internal.AbstractPersistentCollection;
import org.hibernate.type.CollectionType;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.logger.Triggerable;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.LoggingInspections;
import org.hibernate.testing.orm.junit.LoggingInspectionsScope;
import org.hibernate.testing.orm.junit.MessageKeyWatcher;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests merge of detached PersistentBag
 *
 * @author Gail Badner
 */
@DomainModel(
		annotatedClasses = {
				DetachedBagDelayedOperationTest.Parent.class,
				DetachedBagDelayedOperationTest.Child.class
		}
)
@SessionFactory
@LoggingInspections(
		messages = {
				@LoggingInspections.Message(
						messageKey = "HHH000494",
						loggers = @org.hibernate.testing.orm.junit.Logger( loggerNameClass = CollectionType.class )
				),
				@LoggingInspections.Message(
						messageKey = "HHH000495",
						loggers = @org.hibernate.testing.orm.junit.Logger( loggerNameClass = AbstractPersistentCollection.class )
				),
				@LoggingInspections.Message(
						messageKey = "HHH000496",
						loggers = @org.hibernate.testing.orm.junit.Logger( loggerNameClass = AbstractPersistentCollection.class )
				),
				@LoggingInspections.Message(
						messageKey = "HHH000498",
						loggers = @org.hibernate.testing.orm.junit.Logger( loggerNameClass = AbstractPersistentCollection.class )
				)
		}
)
public class DetachedBagDelayedOperationTest {

	@BeforeEach
	public void setup(SessionFactoryScope scope) {
		Parent parent = new Parent();
		parent.id = 1L;
		Child child1 = new Child( "Sherman" );
		Child child2 = new Child( "Yogi" );
		parent.addChild( child1 );
		parent.addChild( child2 );

		scope.inTransaction(
				session -> {

					session.persist( child1 );
					session.persist( child2 );
					session.persist( parent );
				}
		);
	}

	@AfterEach
	public void cleanup(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete from Child" ).executeUpdate();
					session.createQuery( "delete from Parent" ).executeUpdate();
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-11209")
	public void testMergeDetachedCollectionWithQueuedOperations(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		final Parent pOriginal = scope.fromTransaction(
				session -> {
					Parent p = session.get( Parent.class, 1L );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					// initialize
					Hibernate.initialize( p.getChildren() );
					assertTrue( Hibernate.isInitialized( p.getChildren() ) );
					return p;
				}
		);

		final MessageKeyWatcher opMergedWatcher = loggingScope.getWatcher( "HHH000494", CollectionType.class );
		final MessageKeyWatcher opAttachedWatcher = loggingScope.getWatcher( "HHH000495", AbstractPersistentCollection.class );
		final MessageKeyWatcher opDetachedWatcher = loggingScope.getWatcher( "HHH000496", AbstractPersistentCollection.class );
		final MessageKeyWatcher opRollbackWatcher = loggingScope.getWatcher( "HHH000498", AbstractPersistentCollection.class );

		final Parent pWithQueuedOperations = scope.fromTransaction(
				session -> {
					Parent p = (Parent) session.merge( pOriginal );
					Child c = new Child( "Zeke" );
					c.setParent( p );
					session.persist( c );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					p.getChildren().add( c );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					assertTrue( ( (AbstractPersistentCollection) p.getChildren() ).hasQueuedOperations() );

					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					session.detach( p );

					assertTrue( opDetachedWatcher.wasTriggered() );
					assertEquals(
							"HHH000496: Detaching an uninitialized collection with queued operations from a session: [org.hibernate.orm.test.collection.delayedOperation.DetachedBagDelayedOperationTest$Parent.children#1]",
							opDetachedWatcher.getFirstTriggeredMessage()
					);
					opDetachedWatcher.reset();

					// Make sure nothing else got triggered
					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					return p;
				}
		);

		assertFalse( opMergedWatcher.wasTriggered() );
		assertFalse( opAttachedWatcher.wasTriggered() );
		assertFalse( opDetachedWatcher.wasTriggered() );
		assertFalse( opRollbackWatcher.wasTriggered() );

		assertTrue( ( (AbstractPersistentCollection) pWithQueuedOperations.getChildren() ).hasQueuedOperations() );

		// Merge detached Parent with uninitialized collection with queued operations
		scope.inTransaction(
				session -> {

					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					assertFalse( opMergedWatcher.wasTriggered() );
					Parent p = (Parent) session.merge( pWithQueuedOperations );
					assertTrue( opMergedWatcher.wasTriggered() );
					assertEquals(
							"HHH000494: Attempt to merge an uninitialized collection with queued operations; queued operations will be ignored: [org.hibernate.orm.test.collection.delayedOperation.DetachedBagDelayedOperationTest$Parent.children#1]",
							opMergedWatcher.getFirstTriggeredMessage()
					);
					opMergedWatcher.reset();

					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					assertFalse( ( (AbstractPersistentCollection) p.getChildren() ).hasQueuedOperations() );

					// When initialized, p.children will not include the new Child ("Zeke"),
					// because that Child was flushed without a parent before being detached
					// along with its parent.
					Hibernate.initialize( p.getChildren() );
					final Set<String> childNames = new HashSet<>(
							Arrays.asList( "Yogi", "Sherman" )
					);
					assertEquals( childNames.size(), p.getChildren().size() );
					for ( Child child : p.getChildren() ) {
						childNames.remove( child.getName() );
					}
					assertEquals( 0, childNames.size() );
				}
		);

		assertFalse( opMergedWatcher.wasTriggered() );
		assertFalse( opAttachedWatcher.wasTriggered() );
		assertFalse( opDetachedWatcher.wasTriggered() );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-11209")
	public void testSaveOrUpdateDetachedCollectionWithQueuedOperations(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		final MessageKeyWatcher opMergedWatcher = loggingScope.getWatcher( "HHH000494", CollectionType.class );
		final MessageKeyWatcher opAttachedWatcher = loggingScope.getWatcher( "HHH000495", AbstractPersistentCollection.class );
		final MessageKeyWatcher opDetachedWatcher = loggingScope.getWatcher( "HHH000496", AbstractPersistentCollection.class );
		final MessageKeyWatcher opRollbackWatcher = loggingScope.getWatcher( "HHH000498", AbstractPersistentCollection.class );

		final Parent pOriginal = scope.fromTransaction(
				session -> {
					Parent p = session.get( Parent.class, 1L );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					// initialize
					Hibernate.initialize( p.getChildren() );
					assertTrue( Hibernate.isInitialized( p.getChildren() ) );
					return p;
				}
		);
		final Parent pAfterDetachWithQueuedOperations = scope.fromTransaction(
				session -> {
					Parent p = (Parent) session.merge( pOriginal );
					Child c = new Child( "Zeke" );
					c.setParent( p );
					session.persist( c );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					p.getChildren().add( c );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					assertTrue( ( (AbstractPersistentCollection) p.getChildren() ).hasQueuedOperations() );

					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					session.detach( p );
					assertTrue( opDetachedWatcher.wasTriggered() );
					assertEquals(
							"HHH000496: Detaching an uninitialized collection with queued operations from a session: [org.hibernate.orm.test.collection.delayedOperation.DetachedBagDelayedOperationTest$Parent.children#1]",
							opDetachedWatcher.getFirstTriggeredMessage()
					);
					opDetachedWatcher.reset();

					// Make sure nothing else got triggered
					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					return p;
				}
		);

		assertFalse( opMergedWatcher.wasTriggered() );
		assertFalse( opAttachedWatcher.wasTriggered() );
		assertFalse( opDetachedWatcher.wasTriggered() );

		assertTrue( ( (AbstractPersistentCollection) pAfterDetachWithQueuedOperations.getChildren() ).hasQueuedOperations() );

		// Save detached Parent with uninitialized collection with queued operations
		scope.inTransaction(
				session -> {

					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					assertFalse( opAttachedWatcher.wasTriggered() );
					session.saveOrUpdate( pAfterDetachWithQueuedOperations );
					assertTrue( opAttachedWatcher.wasTriggered() );
					assertEquals(
							"HHH000495: Attaching an uninitialized collection with queued operations to a session: [org.hibernate.orm.test.collection.delayedOperation.DetachedBagDelayedOperationTest$Parent.children#1]",
							opAttachedWatcher.getFirstTriggeredMessage()
					);
					opAttachedWatcher.reset();

					// Make sure nothing else got triggered
					assertFalse( opMergedWatcher.wasTriggered() );
					assertFalse( opAttachedWatcher.wasTriggered() );
					assertFalse( opDetachedWatcher.wasTriggered() );
					assertFalse( opRollbackWatcher.wasTriggered() );

					assertFalse( Hibernate.isInitialized( pAfterDetachWithQueuedOperations.getChildren() ) );
					assertTrue( ( (AbstractPersistentCollection) pAfterDetachWithQueuedOperations.getChildren() ).hasQueuedOperations() );

					// Queued operations will be executed when the collection is initialized,
					// After initialization, the collection will contain the Child that was added as a
					// queued operation before being detached above.
					Hibernate.initialize( pAfterDetachWithQueuedOperations.getChildren() );
					final Set<String> childNames = new HashSet<>(
							Arrays.asList( "Yogi", "Sherman", "Zeke" )
					);
					assertEquals( childNames.size(), pAfterDetachWithQueuedOperations.getChildren().size() );
					for ( Child child : pAfterDetachWithQueuedOperations.getChildren() ) {
						childNames.remove( child.getName() );
					}
					assertEquals( 0, childNames.size() );
				}
		);

		assertFalse( opMergedWatcher.wasTriggered() );
		assertFalse( opAttachedWatcher.wasTriggered() );
		assertFalse( opDetachedWatcher.wasTriggered() );
		assertFalse( opRollbackWatcher.wasTriggered() );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-11209")
	public void testCollectionWithQueuedOperationsOnRollback(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		final MessageKeyWatcher opMergedWatcher = loggingScope.getWatcher( "HHH000494", CollectionType.class );
		final MessageKeyWatcher opAttachedWatcher = loggingScope.getWatcher( "HHH000495", AbstractPersistentCollection.class );
		final MessageKeyWatcher opDetachedWatcher = loggingScope.getWatcher( "HHH000496", AbstractPersistentCollection.class );
		final MessageKeyWatcher opRollbackWatcher = loggingScope.getWatcher( "HHH000498", AbstractPersistentCollection.class );

		final Parent pOriginal = scope.fromTransaction(
				session -> {
					Parent p = session.get( Parent.class, 1L );
					assertFalse( Hibernate.isInitialized( p.getChildren() ) );
					// initialize
					Hibernate.initialize( p.getChildren() );
					assertTrue( Hibernate.isInitialized( p.getChildren() ) );
					return p;
				}
		);
		try {
			scope.inTransaction(
					session -> {
						Parent p = (Parent) session.merge( pOriginal );
						Child c = new Child( "Zeke" );
						c.setParent( p );
						session.persist( c );
						assertFalse( Hibernate.isInitialized( p.getChildren() ) );
						p.getChildren().add( c );
						assertFalse( Hibernate.isInitialized( p.getChildren() ) );
						assertTrue( ( (AbstractPersistentCollection) p.getChildren() ).hasQueuedOperations() );

						assertFalse( opMergedWatcher.wasTriggered() );
						assertFalse( opAttachedWatcher.wasTriggered() );
						assertFalse( opDetachedWatcher.wasTriggered() );
						assertFalse( opRollbackWatcher.wasTriggered() );

						// save a new Parent with the same ID to throw an exception.

						Parent pDup = new Parent();
						pDup.id = 1L;
						session.persist( pDup );
					}
			);
			fail( "should have thrown EntityExistsException" );
		}
		catch (EntityExistsException expected) {
		}

		assertTrue( opRollbackWatcher.wasTriggered() );
		opRollbackWatcher.reset();

		assertFalse( opMergedWatcher.wasTriggered() );
		assertFalse( opAttachedWatcher.wasTriggered() );
		assertFalse( opDetachedWatcher.wasTriggered() );
		assertFalse( opRollbackWatcher.wasTriggered() );
	}

	@Entity(name = "Parent")
	public static class Parent {

		@Id
		private Long id;

		// Don't need extra-lazy to delay add operations to a bag.
		@OneToMany(mappedBy = "parent", cascade = CascadeType.DETACH)
		private List<Child> children;

		public Parent() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public List<Child> getChildren() {
			return children;
		}

		public void addChild(Child child) {
			if ( children == null ) {
				children = new ArrayList<>();
			}
			children.add( child );
			child.setParent( this );
		}
	}

	@Entity(name = "Child")
	public static class Child {

		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private Long id;

		@Column(nullable = false)
		private String name;

		@ManyToOne
		private Parent parent;

		public Child() {
		}

		public Child(String name) {
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Parent getParent() {
			return parent;
		}

		public void setParent(Parent parent) {
			this.parent = parent;
		}

		@Override
		public String toString() {
			return "Child{" +
					"id=" + id +
					", name='" + name + '\'' +
					'}';
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}

			Child child = (Child) o;

			return name.equals( child.name );

		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}
	}

}
