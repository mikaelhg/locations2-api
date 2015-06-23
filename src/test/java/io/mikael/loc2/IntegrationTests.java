package io.mikael.loc2;

import com.jayway.restassured.RestAssured;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@WebAppConfiguration
@IntegrationTest({"server.port:0", "spring.cache.type:none"})
@DirtiesContext
public class IntegrationTests {

    @Value("${local.server.port}")
    private int port;

    @Before
    public void setUp() {
        RestAssured.port = port;
    }

    @Autowired
    private EdgeService es;

    @Test
    public void serviceWorks() {
        final Map<String, String> finland = es.fetchDataForIp("91.229.137.36");
        assertEquals(finland.get("country_code"), "FI");
        assertEquals(finland.get("continent"), "EU");
    }

    @Test
    public void current() throws Exception {
        given().
                header("X-Forwarded-For", "91.229.137.37").
        when().
                get("/address/current").
        then().
                statusCode(HttpStatus.SC_OK).
                body("country_code", is("FI")).
                body("continent", is("EU"));
    }

    @Test
    public void specificIp() throws Exception {
        when().
                get("/address/{ip}", "91.229.137.38").
        then().
                statusCode(HttpStatus.SC_OK).
                body("country_code", is("FI")).
                body("continent", is("EU"));
    }

}
