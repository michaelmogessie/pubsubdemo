package info.michaelmogessie.pubsubdemo.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;

import info.michaelmogessie.pubsubdemo.fakes.FakeWebSocketSession;
import info.michaelmogessie.pubsubdemo.pojos.ClientInfo;
import info.michaelmogessie.pubsubdemo.pojos.Message;
import info.michaelmogessie.pubsubdemo.pojos.PublishedMessage;

@WebMvcTest(WebSocketHandler.class)
@ActiveProfiles("test")
public class WebSocketHandlerTest {

    @Value("#{${message.topics}}")
    private List<String> topics;

    @Value("${housekeepingthread.sleepduration.milliseconds}")
    private int houseKeepingThreadSleepDurationMilliseconds;

    private WebSocketHandler webSocketHandler;

    @BeforeEach
    public void setup() {
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
    }

    @Test
    void testConnectedClientRequestToGetSessionId() throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession("abcdefghi");
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        TextMessage textMessage = new TextMessage("temperature/clientId".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);
        assertEquals(webSocketSession.getId(), webSocketSession.getMessage());
    }

    @Test
    void testConnectedClientSubscribeToTopic() throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession("abcdefghi");
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        TextMessage textMessage = new TextMessage("temperature/clientId".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);
        String clientId = webSocketSession.getId();

        textMessage = new TextMessage("temperature/subscribe".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);
        ClientInfo clientInfo = new ClientInfo.Builder().webSocketSession(webSocketSession).clientId(clientId).build();
        assertTrue(WebSocketHandler.getTopicSubscriberMap().get("temperature").contains(clientInfo));
    }

    @Test
    void testConnectedClientUnsubscribeFromTopic() throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession("abcdefghi");
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        TextMessage textMessage = new TextMessage("temperature/clientId".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);
        String clientId = webSocketSession.getId();

        textMessage = new TextMessage("temperature/subscribe".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        textMessage = new TextMessage("temperature/unsubscribe".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        ClientInfo clientInfo = new ClientInfo.Builder().webSocketSession(webSocketSession).clientId(clientId).build();
        assertFalse(WebSocketHandler.getTopicSubscriberMap().get("temperature").contains(clientInfo));
    }

    @Test
    void testConnectedSubscribedClientReceivesPublishedMessage() throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession("abcdefghi");
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        TextMessage textMessage = new TextMessage("temperature/clientId".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        textMessage = new TextMessage("temperature/subscribe".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        Message message = new Message();
        message.setBody("32 degrees");
        message.setTopic("temperature");

        WebSocketHandler.publish(message);

        assertEquals(webSocketSession.getMessage(), message.getBody());
    }

    @Test
    void testDisconnectedSubscribedClientReceivesUnreceivedMessagesAfterReconnectingUsingPreviousConnectionId()
            throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession("abcdefghi");
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        TextMessage textMessage = new TextMessage("temperature/clientId".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        textMessage = new TextMessage("temperature/subscribe".getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);

        String clientId = webSocketSession.getId();
        ClientInfo clientInfo = new ClientInfo.Builder().webSocketSession(webSocketSession).clientId(clientId).build();
        WebSocketHandler.getTopicSubscriberMap().get("temperature").remove(clientInfo);
        clientInfo = new ClientInfo.Builder().webSocketSession(null).clientId(clientId).build();
        WebSocketHandler.getTopicSubscriberMap().get("temperature").add(clientInfo);

        Message message = new Message();
        message.setBody("32 degrees");
        message.setTopic("temperature");

        WebSocketHandler.publish(message);

        clientInfo = new ClientInfo.Builder().webSocketSession(webSocketSession).clientId(clientId).build();
        webSocketSession = new FakeWebSocketSession(clientId);

        webSocketHandler.afterConnectionEstablished(webSocketSession);

        assertEquals(message.getBody(), webSocketSession.getMessage());

    }

    @Test
    void testUnreceivedMessageIsDiscardedAfterTtlHasExpired() throws Exception {
        PublishedMessage publishedMessage = new PublishedMessage.Builder().message("test message").ttl(5000).build();
        WebSocketHandler.getUnreceivedMessages().put(publishedMessage, new ArrayList<>());
        new Thread(webSocketHandler).start();
        Thread.sleep(20000);
        assertFalse(WebSocketHandler.getUnreceivedMessages().containsKey(publishedMessage));
    }

}
