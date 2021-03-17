/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class PersonParameterResolver implements ParameterResolver {

	public PersonParameterResolver() {
	}

	public void init() {
	}

	@Override
	public boolean supportsParameter(
			ParameterContext parameterContext,
			ExtensionContext extensionContext) throws ParameterResolutionException {
		return parameterContext.getParameter().getType()
				.equals( J5_SimpleElementCollectionTest.Person.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext,
								   ExtensionContext extensionContext) throws ParameterResolutionException {
		return new J5_SimpleElementCollectionTest.Person();
	}
}
