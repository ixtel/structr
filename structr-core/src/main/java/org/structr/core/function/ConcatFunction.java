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
package org.structr.core.function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class ConcatFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_CONCAT = "Usage: ${concat(values...)}. Example: ${concat(this.firstName, this.lastName)}";

	@Override
	public String getName() {
		return "concat()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		final List list = new ArrayList();
		for (final Object source : sources) {

			// collection can contain nulls..
			if (source != null) {

				if (source instanceof Collection) {

					list.addAll((Collection)source);

				} else if (source.getClass().isArray()) {

					list.addAll(Arrays.asList((Object[])source));

				} else {

					list.add(source);
				}
			}
		}

		return StringUtils.join(list, "");
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return ERROR_MESSAGE_CONCAT;
	}

	@Override
	public String shortDescription() {
		return "Concatenates all its parameters to a single string with the given separator";
	}

}