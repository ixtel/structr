/**
 * Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
 *
 * This file is part of structr <http://structr.org>.
 *
 * structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.rest.test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import static org.hamcrest.Matchers.equalTo;
import org.structr.rest.common.StructrRestTest;

/**
 *
 * @author alex
 */
public class GroupPropertyTest extends StructrRestTest{
	
	public void test01GroupProperty(){
		
		/*
			{
			 "name": null,
			 "gP1": {
				"sP": "string",
				"iP": 1337
			 },
			 "gP2": null,
			 "id": "d96113452c1b4034b6b1a81f616313af",
			 "type": "TestGroupPropOne"
			}
		 */
		String test011 = createEntity("/test_group_prop_one","{gP1:{sP:text,iP:1337},gP2:{dblP:13.37,dP:01.01.2013}}");
		
		/*
			{
			 "name": null,
			 "gP1": {
				"sP": "string",
				"iP": 1337,
				"lP": null,
				"dblP": 0.1337,
				"bP": true
			 },
			 "gP2": {
				"eP": null
			 },
			 "id": "43c0c0873b7143bdb245afe8ec523bdf",
			 "type": "TestGroupPropTwo"
			}
		 */
		String test021 = createEntity("/test_group_prop_two", "{gP1:{sP:text,iP:1337,dblP:0.1337,bP:true},gP2:{ep:two}}");
		
		String test031 = createEntity("/test_group_prop_three","{gP:{sP:text,iP:1337,gpNode:",test011,"}}");
		String test032 = createEntity("/test_group_prop_three","{ggP:{igP:{gpNode:",test021,",isP:Alex}}}");
		
		// test011 check
		RestAssured
		    
			.given()
				.contentType("application/json; charset=UTF-8")
//				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			
			.expect()
				.statusCode(200)

				.body("result_count", equalTo(1))
				.body("result.id", equalTo(test011))
				.body("result.gP1.sP",equalTo("text"))
				.body("result.gP2.dblP",equalTo(13.37f))

			.when()
				.get(concat("/test_group_prop_one/"+test011));
		
		// test021 check
		RestAssured
		    
			.given()
				.contentType("application/json; charset=UTF-8")
//				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			
			.expect()
				.statusCode(200)

				.body("result_count", equalTo(1))
				.body("result.id", equalTo(test021))
				.body("result.gP1.dblP",equalTo(0.1337f))
				.body("result.gP1.bP",equalTo(true))

			.when()
				.get(concat("/test_group_prop_two/"+test021));
		
		// test031 check
		// Node in groupProperty
		RestAssured
		    
			.given()
				.contentType("application/json; charset=UTF-8")
//				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			
			.expect()
				.statusCode(200)

				.body("result_count", equalTo(1))
				.body("result.id", equalTo(test031))
				.body("result.gP.gpNode.id",equalTo(test011))

			.when()
				.get(concat("/test_group_prop_three/"+test031));
		
		// test032 check
		// Node in GroupProperty in GroupProperty
		RestAssured
		    
			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			
			.expect()
				.statusCode(200)

				.body("result_count", equalTo(1))
				.body("result.id", equalTo(test032))
				.body("result.ggP.igP.gpNode.id",equalTo(test021))

			.when()
				.get(concat("/test_group_prop_three/"+test032));
	}
	
	private String concat(String... parts) {

		StringBuilder buf = new StringBuilder();
		
		for (String part : parts) {
			buf.append(part);
		}
		
		return buf.toString();
	}
	
	private String createEntity(String resource, String... body) {
		
		StringBuilder buf = new StringBuilder();
		
		for (String part : body) {
			buf.append(part);
		}
		
		return getUuidFromLocation(RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(buf.toString())
			.expect().statusCode(201).when().post(resource).getHeader("Location"));
	}
}
