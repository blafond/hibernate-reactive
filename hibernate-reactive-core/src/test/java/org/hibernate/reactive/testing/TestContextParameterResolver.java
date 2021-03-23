/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.testing;

import java.util.HashMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.impl.TestContextImpl;

public class TestContextParameterResolver implements ParameterResolver {

	public TestContextParameterResolver() {
	}

	public void init() {
	}

	@Override
	public boolean supportsParameter(
			ParameterContext parameterContext,
			ExtensionContext extensionContext) throws ParameterResolutionException {
		return parameterContext.getParameter().getType()
				.equals( TestContext.class );
	}

	@Override
	public Object resolveParameter(
			ParameterContext parameterContext,
			ExtensionContext extensionContext) throws ParameterResolutionException {
		return new TestContextImpl( new HashMap<>(), null );
	}
}
