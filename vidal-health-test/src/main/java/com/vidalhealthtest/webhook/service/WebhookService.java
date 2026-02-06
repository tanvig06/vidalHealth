package com.vidalhealthtest.webhook.service;

import com.vidalhealthtest.webhook.dto.SolutionRequest;
import com.vidalhealthtest.webhook.dto.WebhookRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WebhookService implements CommandLineRunner {

    private final RestTemplate client = new RestTemplate();
    private static final String SERVICE_ENDPOINT = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Processing Webhook Sequence...");

        WebhookRequest initialPayload = new WebhookRequest("John Doe", "REG12374", "john@example.com");
        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WebhookRequest> requestEntity = new HttpEntity<>(initialPayload, reqHeaders);

        try {
            System.out.println("Connecting to: External Service Provider");
            ResponseEntity<Map> apiResponse = client.postForEntity(SERVICE_ENDPOINT, requestEntity, Map.class);

            if (apiResponse.getStatusCode().is2xxSuccessful() && apiResponse.getBody() != null) {
                Map<String, Object> responseBody = apiResponse.getBody();
                String targetUrl = (String) responseBody.getOrDefault("webhookUrl", responseBody.get("webhook"));
                String authToken = (String) responseBody.get("accessToken");

                System.out.println("Webhook Target: Received Successfully");

                if (targetUrl != null && authToken != null) {
                    String finalResult = constructSqlSolution();
                    performSubmission(targetUrl, authToken, finalResult);
                } else {
                    System.err.println("Critical: Failed to extract URL or Token.");
                }
            } else {
                System.err.println("API Failure: " + apiResponse.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String constructSqlSolution() {
        return "SELECT " +
                "D.DEPARTMENT_NAME, " +
                "AVG(TIMESTAMPDIFF(YEAR, E.DOB, CURDATE())) AS AVERAGE_AGE, " +
                "SUBSTRING_INDEX(GROUP_CONCAT(CONCAT(E.FIRST_NAME, ' ', E.LAST_NAME) SEPARATOR ', '), ', ', 10) AS EMPLOYEE_LIST "
                +
                "FROM DEPARTMENT D " +
                "JOIN EMPLOYEE E ON D.DEPARTMENT_ID = E.DEPARTMENT " +
                "JOIN PAYMENTS P ON E.EMP_ID = P.EMP_ID " +
                "WHERE P.AMOUNT > 70000 " +
                "GROUP BY D.DEPARTMENT_ID, D.DEPARTMENT_NAME " +
                "ORDER BY D.DEPARTMENT_ID DESC;";
    }

    private void performSubmission(String targetUrl, String authToken, String finalResult) {
        System.out.println("Sending Solution...");
        HttpHeaders subHeaders = new HttpHeaders();
        subHeaders.setContentType(MediaType.APPLICATION_JSON);
        subHeaders.set("Authorization", authToken);

        SolutionRequest solutionPayload = new SolutionRequest(finalResult);
        HttpEntity<SolutionRequest> subEntity = new HttpEntity<>(solutionPayload, subHeaders);

        try {
            ResponseEntity<String> result = client.postForEntity(targetUrl, subEntity, String.class);
            System.out.println("Submission Result: " + result.getStatusCode());
            System.out.println("Server Reply: " + result.getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
