package alarm_message_test;

import alarm.exception.MessagingException;
import alarm.fcm.MessageSender;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

// MessageSender에 대한 BDD Mockito 단위테스트
@ExtendWith(MockitoExtension.class)
public class MessageSenderTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private MessageSender messageSender;

    @Test
    void sendMessage_단일사용자_정상전송() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>(List.of("token1"));
        
        String expectedResponse = "message-id-123";
        
        given(firebaseMessaging.send(any(Message.class))).willReturn(expectedResponse);

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        // 비동기 처리
        Thread.sleep(100);

        // then
        then(firebaseMessaging).should().send(any(Message.class));
    }

    @Test
    void sendMessage_다중사용자_정상전송() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = Arrays.asList("token1", "token2", "token3");
        
        ApiFuture<BatchResponse> mockApiFuture = mock(ApiFuture.class);
        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        SendResponse mockSendResponse = mock(SendResponse.class);
        
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class))).willReturn(mockApiFuture);
        given(mockBatchResponse.getSuccessCount()).willReturn(3);
        given(mockBatchResponse.getFailureCount()).willReturn(0);
        given(mockSendResponse.isSuccessful()).willReturn(true);
        given(mockBatchResponse.getResponses()).willReturn(Arrays.asList(mockSendResponse, mockSendResponse, mockSendResponse));
        
        // ApiFuture의 addListener를 모킹하여 즉시 실행되도록 설정
        doAnswer(invocation -> {
            Runnable listener = invocation.getArgument(0);
            // BatchResponse를 반환하도록 설정
            when(mockApiFuture.get()).thenReturn(mockBatchResponse);
            listener.run();
            return null;
        }).when(mockApiFuture).addListener(any(Runnable.class), any());

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        // 비동기 처리를 위한 잠시 대기
        Thread.sleep(100);

        // then
        then(firebaseMessaging).should().sendEachForMulticastAsync(any(MulticastMessage.class));
    }

    @Test
    void sendMessage_NOT_DEFINED_토큰필터링_정상() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>(Arrays.asList("token1", "NOT_DEFINED", "token2"));
        
        ApiFuture<BatchResponse> mockApiFuture = mock(ApiFuture.class);
        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        SendResponse mockSendResponse = mock(SendResponse.class);
        
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class))).willReturn(mockApiFuture);
        given(mockBatchResponse.getSuccessCount()).willReturn(2);
        given(mockBatchResponse.getFailureCount()).willReturn(0);
        given(mockSendResponse.isSuccessful()).willReturn(true);
        given(mockBatchResponse.getResponses()).willReturn(Arrays.asList(mockSendResponse, mockSendResponse));
        
        doAnswer(invocation -> {
            Runnable listener = invocation.getArgument(0);
            when(mockApiFuture.get()).thenReturn(mockBatchResponse);
            listener.run();
            return null;
        }).when(mockApiFuture).addListener(any(Runnable.class), any());

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        Thread.sleep(100);

        // then
        then(firebaseMessaging).should().sendEachForMulticastAsync(any(MulticastMessage.class));
    }

    @Test
    void sendMessage_빈토큰리스트_메시지전송안함() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>();

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        // then
        then(firebaseMessaging).should(never()).send(any(Message.class));
        then(firebaseMessaging).should(never()).sendEachForMulticastAsync(any(MulticastMessage.class));
    }

    @Test
    void sendMessage_NOT_DEFINED만있는경우_메시지전송안함() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>(Arrays.asList("NOT_DEFINED", "NOT_DEFINED"));

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        // then
        then(firebaseMessaging).should(never()).send(any(Message.class));
        then(firebaseMessaging).should(never()).sendEachForMulticastAsync(any(MulticastMessage.class));
    }

    @Test
    void sendMessage_단일사용자_전송실패시_예외발생() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>();
        receiverTokens.add("invalid-token");
        
        FirebaseMessagingException firebaseException = mock(FirebaseMessagingException.class);
        
        given(firebaseMessaging.send(any(Message.class))).willThrow(firebaseException);

        // when
        messageSender.sendMessage(messageData, receiverTokens);
        Thread.sleep(100);
        
        // then
        then(firebaseMessaging).should().send(any(Message.class));
    }

    @Test
    void sendMessage_다중사용자_일부실패_로그출력() throws Exception {
        // given
        Map<String, String> messageData = Map.of("title", "테스트 제목", "body", "테스트 내용");
        List<String> receiverTokens = new ArrayList<>();
        receiverTokens.add("token1");
        receiverTokens.add("invalid-token");
        receiverTokens.add("token3");
        
        ApiFuture<BatchResponse> mockApiFuture = mock(ApiFuture.class);
        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        SendResponse successResponse = mock(SendResponse.class);
        SendResponse failResponse = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class))).willReturn(mockApiFuture);
        given(mockBatchResponse.getSuccessCount()).willReturn(2);
        given(mockBatchResponse.getFailureCount()).willReturn(1);
        given(successResponse.isSuccessful()).willReturn(true);
        given(failResponse.isSuccessful()).willReturn(false);
        given(failResponse.getException()).willReturn(exception);
        given(exception.getMessage()).willReturn("Invalid token");
        given(mockBatchResponse.getResponses()).willReturn(Arrays.asList(successResponse, failResponse, successResponse));
        
        doAnswer(invocation -> {
            Runnable listener = invocation.getArgument(0);
            when(mockApiFuture.get()).thenReturn(mockBatchResponse);
            listener.run();
            return null;
        }).when(mockApiFuture).addListener(any(Runnable.class), any());

        // when
        messageSender.sendMessage(messageData, receiverTokens);

        Thread.sleep(100);

        // then
        then(firebaseMessaging).should().sendEachForMulticastAsync(any(MulticastMessage.class));
    }
}