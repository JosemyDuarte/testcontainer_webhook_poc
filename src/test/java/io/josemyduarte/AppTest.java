package io.josemyduarte;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.vavr.control.Try;
import java.util.Optional;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;


public class AppTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppTest.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LOCALHOST = "http://localhost:";
    private static final int NGROK_EXPOSED_PORT = 4040;
    private static final int WEBSERVER_EXPOSED_PORT = 8081;
    private static final String NETWORK_WEB_ALIAS = "web";
    private static final String NETWORK_NGROK_ALIAS = "ngrok";
    private static final String WEBSERVER_URL = NETWORK_WEB_ALIAS + ":" + WEBSERVER_EXPOSED_PORT;

    @ClassRule
    public static final Network NETWORK = Network.newNetwork();

    @ClassRule
    public static final GenericContainer<?> webserver = new GenericContainer<>(
            new ImageFromDockerfile("webhook-server", true)
                    .withFileFromClasspath(".", "webhook-server/")
    )
            .withExposedPorts(WEBSERVER_EXPOSED_PORT)
            .withEnv("PORT", String.valueOf(WEBSERVER_EXPOSED_PORT))
            .withNetwork(NETWORK)
            .withNetworkAliases(NETWORK_WEB_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("webhook-server")));

    @ClassRule
    public static final GenericContainer<?> ngrok = new GenericContainer<>(
            new ImageFromDockerfile("webhook-ngrok", false)
                    .withFileFromClasspath(".", "webhook-ngrok/")
    )
            .withExposedPorts(NGROK_EXPOSED_PORT)
            .withNetwork(NETWORK)
            .withNetworkAliases(NETWORK_NGROK_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("webhook-ngrok")))
            .withCommand("ngrok http " + WEBSERVER_URL)
            .waitingFor(new LogMessageWaitStrategy().withRegEx(
                    "^.*addr=.* url=http.*")); //Wait for ngrok public url to be available

    @Test
    public void givingARequestMadeToServer_whenRetrievingLastRequest_thenSameBodyIsReturned() throws Exception {
        final String emptyLastRequest = fetchLastRequest();
        assertNull(emptyLastRequest);

        final String requestBody = "{\"greet\":\"hi\"}";
        makeARequestToServerWith(requestBody);

        final String nonEmptyLastRequest = fetchLastRequest();
        assertNotNull(nonEmptyLastRequest);

        assertTrue(nonEmptyLastRequest.contains(requestBody));
    }

    private void makeARequestToServerWith(final String requestBody) throws Exception {
        LOGGER.info("Making a request simulating a webhook notification...");
        final HttpClient httpClient = HttpClients.createDefault();
        final HttpPost httpPost = new HttpPost(getTunnelPublicUrl() + "/registerRequest");
        httpPost.setEntity(new StringEntity(requestBody));
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        final HttpResponse response = httpClient.execute(httpPost);
        final HttpEntity entity = response.getEntity();
        final String responseString = EntityUtils.toString(entity, "UTF-8");
        LOGGER.info(responseString);
    }

    private String fetchLastRequest() throws Exception {
        LOGGER.info("Retrieving last request made to our server...");
        return generateRequest(
                LOCALHOST + webserver.getMappedPort(WEBSERVER_EXPOSED_PORT) + "/lastRequest")
                .orElse(null);
    }

    private String getTunnelPublicUrl() throws Exception {
        final String publicUrl = generateRequest(
                LOCALHOST + ngrok.getMappedPort(NGROK_EXPOSED_PORT) + "/api/tunnels")
                .flatMap(nonNullResponse -> Try.of(() -> OBJECT_MAPPER.readTree(nonNullResponse)
                                                                      .path("tunnels")
                                                                      .get(0)
                                                                      .path("public_url")
                                                                      .asText())
                                               .toJavaOptional())
                .orElseThrow(() -> new RuntimeException("No public URL found for Ngrok"));

        LOGGER.info("Public URL = [{}]", publicUrl);

        return publicUrl;
    }

    private Optional<String> generateRequest(final String requestUrl)
            throws Exception {
        final CloseableHttpClient client = HttpClients.createDefault();
        final HttpGet httpGet = new HttpGet(requestUrl);
        httpGet.setHeader("accept", "application/json");
        final CloseableHttpResponse httpResponse = client.execute(httpGet);
        final HttpEntity entity = httpResponse.getEntity();
        return Optional.ofNullable(entity)
                       .flatMap(nonNullEntity -> Try.of(() ->
                               EntityUtils.toString(nonNullEntity, "UTF-8")).toJavaOptional());
    }

}
