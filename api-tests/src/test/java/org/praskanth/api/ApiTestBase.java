package org.praskanth.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import static io.restassured.RestAssured.given;

/**
 * Shared set-up for every API test.
 *
 * These tests talk to the REAL running system through the API gateway (docker compose up), exactly like the React
 * frontend does. Nothing is mocked. Settings come from environment variables so the same tests can run against
 * local, DEV or STAGING:
 *
 *   API_BASE_URL    default http://localhost:2323
 *   ADMIN_EMAIL     the BOOTSTRAP_ADMIN_EMAIL you set in .env
 *   ADMIN_PASSWORD  the BOOTSTRAP_ADMIN_PASSWORD you set in .env
 */
public abstract class ApiTestBase {

    protected static final String BASE_URL = env("API_BASE_URL", "http://localhost:2323");
    protected static final String ADMIN_EMAIL = firstNonBlank(env("ADMIN_EMAIL", ""), dotEnv("BOOTSTRAP_ADMIN_EMAIL"));
    protected static final String ADMIN_PASSWORD = firstNonBlank(env("ADMIN_PASSWORD", ""), dotEnv("BOOTSTRAP_ADMIN_PASSWORD"));

    private static String adminToken; // login once, reuse in every test class

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.baseURI = BASE_URL;
        // Print request + response only when a test fails, so a red test explains itself.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /** Request with a JSON body type and no login: a visitor who is not signed in. */
    protected static RequestSpecification anonymous() {
        return given().spec(new RequestSpecBuilder().setContentType(ContentType.JSON).setAccept(ContentType.JSON).build());
    }

    /** Request signed in as the admin. */
    protected static RequestSpecification asAdmin() {
        return anonymous().header("Authorization", "Bearer " + adminToken());
    }

    protected static synchronized String adminToken() {
        if (adminToken == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(!ADMIN_EMAIL.isBlank() && !ADMIN_PASSWORD.isBlank(),
                    "Set ADMIN_EMAIL and ADMIN_PASSWORD (your BOOTSTRAP_ADMIN_* values) to run the signed-in tests");
            adminToken = anonymous()
                    .body(java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                    .post("/api/v1/auth/login")
                    .then().statusCode(200)
                    .extract().path("data.token");
        }
        return adminToken;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /**
     * Fallback so you do not have to repeat the admin login: read BOOTSTRAP_ADMIN_* from the project's .env file
     * (the same file docker compose uses). Looks in the current folder and its parent. Surrounding quotes and a
     * stray trailing ';' are removed. Returns "" when the file or the key is not there.
     */
    private static String dotEnv(String key) {
        for (String dir : new String[] {".", ".."}) {
            java.nio.file.Path file = java.nio.file.Path.of(dir, ".env");
            if (!java.nio.file.Files.isRegularFile(file)) {
                continue;
            }
            try {
                for (String line : java.nio.file.Files.readAllLines(file)) {
                    String text = line.strip();
                    if (text.startsWith("#") || !text.startsWith(key + "=")) {
                        continue;
                    }
                    String value = text.substring(key.length() + 1).strip();
                    while (value.endsWith(";")) {
                        value = value.substring(0, value.length() - 1).strip();
                    }
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            } catch (java.io.IOException ignored) {
                // unreadable file: behave as if it was not there
            }
        }
        return "";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
