/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.testing;

import org.hibernate.internal.CoreMessageLogger;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.jboss.logging.Logger;

public class SessionFactoryManagerExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
		AfterEachCallback {
	CoreMessageLogger LOG = Logger.getMessageLogger(
			CoreMessageLogger.class,
			SessionFactoryManagerExtension.class.getName()
	);
	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		LOG.info(" --- === >>> CALLING: " + "afterAll(ExtensionContext context)");
	}

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		LOG.info(" --- === >>> CALLING: " + "beforeAll(ExtensionContext context)");
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		LOG.info(" --- === >>> CALLING: " + "afterEach(ExtensionContext context)");
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		LOG.info(" --- === >>> CALLING: " + "beforeEach(ExtensionContext context)");
	}
}
