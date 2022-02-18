package info.michaelmogessie.pubsubdemo.fakes;

import org.springframework.web.socket.TextMessage;

import info.michaelmogessie.pubsubdemo.handlers.WebSocketHandler;

public class FakeClient implements Runnable {
    private final String id;
    private final WebSocketHandler webSocketHandler;
    private final String topic;

    public FakeClient(WebSocketHandler webSocketHandler, String topic) {
        this.id = System.currentTimeMillis() + "";
        this.webSocketHandler = webSocketHandler;
        this.topic = topic;
    }

    @Override
    public void run() {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession(id);
        try {
            TextMessage textMessage = new TextMessage(
                    new StringBuilder(this.topic).append("/subscribe").toString().getBytes());
            webSocketHandler.handleMessage(webSocketSession, textMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void unsubscribe(String topic) throws Exception {
        FakeWebSocketSession webSocketSession = new FakeWebSocketSession(id);
        TextMessage textMessage = new TextMessage(
                new StringBuilder(this.topic).append("/unsubscribe").toString().getBytes());
        webSocketHandler.handleMessage(webSocketSession, textMessage);
    }

    public String getTopic() {
        return topic;
    }

}
