/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate;

/**
 * Indicates a request against an unknown fetch profile name.
 *
 * @author Steve Ebersole
 *
 * @see org.hibernate.annotations.FetchProfile
 * @see Session#enableFetchProfile(String)
 */
public class UnknownProfileException extends HibernateException {
	private final String name;

	/**
	 * Constructs an {@code UnknownProfileException} for the given name.
	 *
	 * @param name The profile name that was unknown.
	 */
	public UnknownProfileException(String name) {
		super( "No fetch profile named '" + name + "'" );
		this.name = name;
	}

	/**
	 * The unknown fetch profile name.
	 *
	 * @return The unknown fetch profile name.
	 */
	public String getName() {
		return name;
	}
}
