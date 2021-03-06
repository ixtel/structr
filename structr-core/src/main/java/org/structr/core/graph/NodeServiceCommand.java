/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.graph;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.Predicate;
import org.structr.api.service.Command;
import org.structr.common.Filter;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;

/**
 * Abstract base class for all graph service commands.
 *
 *
 */
public abstract class NodeServiceCommand extends Command {

	private static final Logger logger                        = LoggerFactory.getLogger(NodeServiceCommand.class.getName());
	private static final ArrayBlockingQueue<String> uuidQueue = new ArrayBlockingQueue<>(100000);

	protected SecurityContext securityContext = null;

	@Override
	public Class getServiceClass()	{
		return(NodeService.class);
	}

	@Override
	public void initialized() {
		this.securityContext = (SecurityContext)getArgument("securityContext");
	}

	/**
	 * Executes the given operation on all nodes in the given list.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param iterator the iterator that provides the nodes to operate on
	 * @param commitCount
	 * @param description
	 * @param operation the operation to execute
	 * @return the number of nodes processed
	 */
	public static <T> long bulkGraphOperation(final SecurityContext securityContext, final Iterator<T> iterator, final long commitCount, String description, final BulkGraphOperation<T> operation) {
		return bulkGraphOperation(securityContext, iterator, commitCount, description, operation, true);
	}
	/**
	 * Executes the given operation on all nodes in the given list.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param iterator the iterator that provides the nodes to operate on
	 * @param commitCount
	 * @param description
	 * @param operation the operation to execute
	 * @param validation
	 * @return the number of nodes processed
	 */
	public static <T> long bulkGraphOperation(final SecurityContext securityContext, final Iterator<T> iterator, final long commitCount, String description, final BulkGraphOperation<T> operation, boolean validation) {

		final Predicate<Long> condition = operation.getCondition();
		final App app                   = StructrApp.getInstance(securityContext);
		final boolean doValidation      = operation.doValidation();
		final boolean doCallbacks       = operation.doCallbacks();
		final boolean doNotifications   = operation.doNotifications();
		long objectCount                = 0L;
		boolean active                  = true;

		while (active) {

			active = false;

			try (final Tx tx = app.tx(doValidation, doCallbacks, doNotifications)) {

				while (iterator.hasNext() && (condition == null || condition.accept(objectCount))) {

					T node = iterator.next();
					active = true;

					try {

						operation.handleGraphObject(securityContext, node);

					} catch (Throwable t) {

						operation.handleThrowable(securityContext, t, node);
					}

					// commit transaction after commitCount
					if ((++objectCount % commitCount) == 0) {
						break;
					}
				}

				tx.success();

			} catch (Throwable t) {

				// bulk transaction failed, what to do?
				operation.handleTransactionFailure(securityContext, t);
			}

			if (description != null) {
				logger.info("{}: {} objects processed", new Object[] { description, objectCount } );
			}
		}

		return objectCount;
	}

	/**
	 * Executes the given transaction until the stop condition evaluates to
	 * <b>true</b>.
	 *
	 * @param securityContext
	 * @param commitCount the number of executions after which the transaction is committed
	 * @param transaction the operation to execute
	 * @param stopCondition
	 * @throws FrameworkException
	 */
	public static void bulkTransaction(final SecurityContext securityContext, final long commitCount, final StructrTransaction transaction, final Predicate<Long> stopCondition) throws FrameworkException {

		final App app                = StructrApp.getInstance(securityContext);
		final AtomicLong objectCount = new AtomicLong(0L);

		if (stopCondition instanceof Filter) {
			((Filter)stopCondition).setSecurityContext(securityContext);
		}

		while (!stopCondition.accept(objectCount.get())) {

			try (final Tx tx = app.tx()) {

				long loopCount = 0;

				while (loopCount++ < commitCount && !stopCondition.accept(objectCount.get())) {

					transaction.execute();
					objectCount.incrementAndGet();
				}

				tx.success();
			}
		}
	}

	public static String getNextUuid() {

		String uuid = null;

		do {

			uuid = uuidQueue.poll();

		} while (uuid == null);

		return uuid;
	}

	// create uuid producer that fills the queue
	static {

		Thread uuidProducer = new Thread(new Runnable() {

			@Override
			public void run() {

				// please do not stop :)
				while (true) {

					try {
						while (true) {

							uuidQueue.put(StringUtils.replace(UUID.randomUUID().toString(), "-", ""));
						}

					} catch (Throwable t) {	}
				}
			}

		}, "UuidProducerThread");

		uuidProducer.setDaemon(true);
		uuidProducer.start();
	}
}
