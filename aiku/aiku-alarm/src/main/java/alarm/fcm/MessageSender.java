package alarm.fcm;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.*;
import alarm.exception.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static common.response.status.BaseErrorCode.FAIL_TO_SEND_MESSAGE;

@Slf4j
@RequiredArgsConstructor
@Component
public class MessageSender {

    private static final String NOT_DEFINED_TOKEN = "NOT_DEFINED";
    private final FirebaseMessaging firebaseMessaging;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public CompletableFuture<Void> sendMessage(Map<String, String> messageDataMap, List<String> receiverTokens) {
        List<String> validTokens = filterValidTokens(receiverTokens);

        if (validTokens.isEmpty()) {
            log.warn("No valid tokens found for message sending");
            return CompletableFuture.completedFuture(null);
        }

        if (validTokens.size() == 1) {
            return sendMessageToUser(messageDataMap, validTokens.get(0));
        } else {
            return sendMessageToUsers(messageDataMap, validTokens);
        }
    }

    private List<String> filterValidTokens(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !NOT_DEFINED_TOKEN.equals(token))
                .filter(Objects::nonNull)
                .toList();
    }

    private CompletableFuture<Void> sendMessageToUser(Map<String, String> messageDataMap, String receiverToken) {
        Message message = Message.builder()
                .putAllData(messageDataMap)
                .setToken(receiverToken)
                .build();

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return firebaseMessaging.send(message);
                    } catch (FirebaseMessagingException e) {
                        throw new RuntimeException("Failed to send message to user", e);
                    }
                }, executorService)
                .thenAccept(response -> log.info("Message sent successfully: {}", response))
                .exceptionally(ex -> {
                    log.error("Failed to send message to user: {}", ex.getMessage(), ex);
                    throw new MessagingException(FAIL_TO_SEND_MESSAGE);
                });
    }

    private CompletableFuture<Void> sendMessageToUsers(Map<String, String> messageDataMap, List<String> receiverTokens) {
        MulticastMessage message = MulticastMessage.builder()
                .putAllData(messageDataMap)
                .addAllTokens(receiverTokens)
                .build();

        CompletableFuture<Void> future = new CompletableFuture<>();

        ApiFuture<BatchResponse> apiFuture = firebaseMessaging.sendEachForMulticastAsync(message);
        apiFuture.addListener(() -> {
            try {
                BatchResponse batchResponse = apiFuture.get();
                log.info("Successfully sent messages: {}, Failed: {}",
                        batchResponse.getSuccessCount(), batchResponse.getFailureCount());

                logFailedResponses(batchResponse);
                future.complete(null);
            } catch (Exception e) {
                log.error("Failed to send batch messages: {}", e.getMessage(), e);
                future.completeExceptionally(new MessagingException(FAIL_TO_SEND_MESSAGE));
            }
        }, executorService);

        return future;
    }

    private void logFailedResponses(BatchResponse batchResponse) {
        batchResponse.getResponses().stream()
                .filter(response -> !response.isSuccessful())
                .forEach(response ->
                        log.error("Message sending failed: {}", response.getException().getMessage()));
    }

}
