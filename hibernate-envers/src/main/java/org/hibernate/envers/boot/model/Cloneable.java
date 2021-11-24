/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.boot.model;

/**
 * Contract for an object that is cloneable.
 *
 * @author Chris Cranford
 */
public interface Cloneable<T> {
	/**
	 * Creates a new, deep-copied instance of this object
	 * @return a deep-copy clone of the referenced object
	 */
	T deepCopy();
}
