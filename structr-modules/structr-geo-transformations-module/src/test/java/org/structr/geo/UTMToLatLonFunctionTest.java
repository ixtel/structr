/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.geo;

import org.junit.Assert;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;

/**
 *
 */
public class UTMToLatLonFunctionTest extends StructrTest {

	public void testUTMLatLonRoundTrip() {

		final LatLonToUTMFunction latLonUtm = new LatLonToUTMFunction();
		final UTMToLatLonFunction utmLatLon = new UTMToLatLonFunction();
		final String sourceUTM              = "32U 439596 5967780";

		try {
			final GraphObjectMap result1 = (GraphObjectMap)utmLatLon.apply(null, null, new Object[] { sourceUTM });
			final String result2         = (String)latLonUtm.apply(null, null, new Object[] { result1.getProperty(UTMToLatLonFunction.latitudeProperty), result1.getProperty(UTMToLatLonFunction.longitudeProperty) } );

			Assert.assertEquals("Invalid UTM to lat/lon roundtrip result", sourceUTM, result2);

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	public void testLatLonUTMRoundtrip() {

		final LatLonToUTMFunction latLonUtm = new LatLonToUTMFunction();
		final UTMToLatLonFunction utmLatLon = new UTMToLatLonFunction();
		final double latitude               = 51.319997116243364;
		final double longitude              = 7.49998773689121;

		try {
			final String result1         = (String)latLonUtm.apply(null, null, new Object[] { latitude, longitude } );
			final GraphObjectMap result2 = (GraphObjectMap)utmLatLon.apply(null, null, new Object[] { result1 } );

			Assert.assertEquals("Invalid UTM to lat/lon roundtrip result", (Double)latitude,  result2.getProperty(UTMToLatLonFunction.latitudeProperty));
			Assert.assertEquals("Invalid UTM to lat/lon roundtrip result", (Double)longitude, result2.getProperty(UTMToLatLonFunction.longitudeProperty));

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	public void testUTMToLatLon() {

		final UTMToLatLonFunction func = new UTMToLatLonFunction();

		try {

			final Object result6 = func.apply(null, null, new Object[] { "32 N 439596 5967780" });
			Assert.assertEquals("Invalid UTM conversion result", 53.85499997165232, get(result6, 0));
			Assert.assertEquals("Invalid UTM conversion result", 8.081674915658844, get(result6, 1));

			final Object result7 = func.apply(null, null, new Object[] { "32U 395473 5686479" });
			Assert.assertEquals("Invalid UTM conversion result", 51.319997116243364, get(result7, 0));
			Assert.assertEquals("Invalid UTM conversion result", 7.49998773689121, get(result7, 1));

			final Object result8 = func.apply(null, null, new Object[] { "32 395473 5686479" });
			Assert.assertEquals("Invalid UTM conversion result", 51.319997116243364, get(result8, 0));
			Assert.assertEquals("Invalid UTM conversion result", 7.49998773689121, get(result8, 1));

			final Object result9 = func.apply(null, null, new Object[] { "32H 395473 5686479" });
			Assert.assertEquals("Invalid UTM conversion result", -38.96442577579118, get(result9, 0));
			Assert.assertEquals("Invalid UTM conversion result", 7.793498600057568, get(result9, 1));

			final Object result10 = func.apply(null, null, new Object[] { "3U 395473 5686479" });
			Assert.assertEquals("Invalid UTM conversion result", 51.319997116243364, get(result10, 0));
			Assert.assertEquals("Invalid UTM conversion result", -166.5000122631088, get(result10, 1));

			final Object result11 = func.apply(null, null, new Object[] { "3 395473 5686479" });
			Assert.assertEquals("Invalid UTM conversion result", 51.319997116243364, get(result11, 0));
			Assert.assertEquals("Invalid UTM conversion result", -166.5000122631088, get(result11, 1));

			final Object result12 = func.apply(null, null, new Object[] { "3H 541926 5949631" });
			Assert.assertEquals("Invalid UTM conversion result", -36.59789213337618, get(result12, 0));
			Assert.assertEquals("Invalid UTM conversion result", -164.5312529421211, get(result12, 1));



		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}

	}

	private Object get(final Object map, final int index) {

		if (map instanceof GraphObjectMap) {

			switch (index) {

				case 0:
					return ((GraphObjectMap)map).getProperty(UTMToLatLonFunction.latitudeProperty);

				case 1:
					return ((GraphObjectMap)map).getProperty(UTMToLatLonFunction.longitudeProperty);
			}
		}

		return null;
	}
}