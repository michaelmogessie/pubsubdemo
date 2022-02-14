package info.michaelmogessie.pubsubdemo.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import info.michaelmogessie.pubsubdemo.excpetions.TopicNotFoundException;
import info.michaelmogessie.pubsubdemo.pojos.ClientInfo;
import info.michaelmogessie.pubsubdemo.pojos.Message;
import info.michaelmogessie.pubsubdemo.pojos.PublishedMessage;

@Component
public class WebSocketHandler extends TextWebSocketHandler implements Runnable {

    private static Map<String, List<ClientInfo>> topicSubscriberMap = new HashMap<>();
    private static Map<PublishedMessage, List<String>> unreceivedMessages = new HashMap<>();
    private int houseKeepingThreadSleepDurationMilliseconds;
    private static final String CLIENT_ID = "clientId";
    private static final String MESSAGE_TOPIC_NOT_FOUND = "COULD NOT UNSUBSCRIBE. TOPIC NOT FOUND.";
    private static final String MESSAGE_NOT_SUBSCRIBED = "YOU ARE NOT SUBSCRIBED TO THIS TOPIC";
    private static final String MESSAGE_MALFORMED_PAYLOAD = "MESSAGE PAYLOAD IS INVALID.";
    private static final String TOPIC_ACTION_SUBSCRIBE = "subscribe";
    private static final String TOPIC_ACTION_UNSUBSCRIBE = "unsubscribe";
    private static final String MESSAGE_NO_SUCH_ACTION = "The only allowed actions are subscribe and unsubscribe";

    private static Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    public WebSocketHandler(@Value("#{${message.topics}}") List<String> topics,
            @Value("${housekeepingthread.sleepduration.milliseconds}") int houseKeepingThreadSleepDurationMilliseconds) {
        this.houseKeepingThreadSleepDurationMilliseconds = houseKeepingThreadSleepDurationMilliseconds;
        topics.stream().forEach(topic -> {
            topicSubscriberMap.put(topic, new ArrayList<>());
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUriString(session.getUri().toString())
                    .build().getQueryParams();
            if (queryParams.containsKey(CLIENT_ID)) {
                deliverUnreceivedMessages(queryParams.getFirst(CLIENT_ID), session);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            session.close(CloseStatus.BAD_DATA);
        }
    }

    /**
     * 
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        String message = textMessage.getPayload();
        String[] topicAndAction = message.split("/");
        if (topicAndAction.length != 2) {
            try {
                session.sendMessage(new TextMessage(MESSAGE_MALFORMED_PAYLOAD));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
            return;
        }
        if (!topicSubscriberMap.containsKey(topicAndAction[0])) {
            try {
                session.sendMessage(new TextMessage(MESSAGE_TOPIC_NOT_FOUND));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
            return;
        }
        if (topicAndAction[1].equals(TOPIC_ACTION_UNSUBSCRIBE)) {
            Optional<ClientInfo> clientInfo = getClientInfo(session, topicAndAction[0]);
            if (!clientInfo.isPresent()) {
                try {
                    session.sendMessage(new TextMessage(MESSAGE_NOT_SUBSCRIBED));
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
                return;
            }
            synchronized (topicSubscriberMap) {
                topicSubscriberMap.get(topicAndAction[0]).remove(clientInfo.get());
            }
        } else if (topicAndAction[1].equals(TOPIC_ACTION_SUBSCRIBE)) {
            ClientInfo clientInfo = new ClientInfo.Builder().clientId(session.getId()).webSocketSession(session)
                    .build();
            synchronized (topicSubscriberMap) {
                topicSubscriberMap.get(topicAndAction[0]).add(clientInfo);
            }
        } else if (topicAndAction[1].equals(CLIENT_ID)) {
            try {
                session.sendMessage(new TextMessage(session.getId()));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        } else {
            try {
                session.sendMessage(new TextMessage(MESSAGE_NO_SUCH_ACTION));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

    }

    private Optional<ClientInfo> getClientInfo(WebSocketSession session, String topic) {
        return topicSubscriberMap.get(topic).stream()
                .filter(ci -> ci.getWebSocketSession().getId().equals(session.getId())).findFirst();
    }

    private void deliverUnreceivedMessages(String clientId, WebSocketSession session) throws IOException {
        synchronized (unreceivedMessages) {
            for (PublishedMessage message : unreceivedMessages.keySet()) {
                if (unreceivedMessages.get(message).contains(clientId)) {
                    session.sendMessage(new TextMessage(message.getMessage()));
                    unreceivedMessages.get(message).remove(clientId);
                }
            }
        }
    }

    public static void publish(Message message) throws TopicNotFoundException {
        if (!topicSubscriberMap.containsKey(message.getTopic())) {
            throw new TopicNotFoundException();
        }
        TextMessage textMessage = new TextMessage(message.getBody());
        PublishedMessage publishedMessage = new PublishedMessage.Builder().message(message.getBody()).build();
        for (ClientInfo clientInfo : topicSubscriberMap.get(message.getTopic())) {
            try {
                clientInfo.getWebSocketSession().sendMessage(textMessage);
            } catch (IOException e) {
                logger.error(e.getMessage());
                new WebSocketHandler.UnreceivedMessagesUpdater(publishedMessage, clientInfo).start();
            }
        }

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClientInfo clientInfo = new ClientInfo.Builder().clientId(session.getId()).webSocketSession(session).build();
        topicSubscriberMap.values().stream().filter(clientInfoList -> clientInfoList.contains(clientInfo))
                .collect(Collectors.toList())
                .forEach(list -> list.remove(clientInfo));
    }

    @Override
    public void run() {
        try {
            while (true) {
                synchronized (unreceivedMessages) {
                    System.out.println(houseKeepingThreadSleepDurationMilliseconds);
                    List<PublishedMessage> toRemove = new ArrayList<>();
                    for (PublishedMessage pm : unreceivedMessages.keySet()) {
                        if (isMessageExpired(pm) || unreceivedMessages.get(pm).isEmpty()) {
                            toRemove.add(pm);
                        }
                    }
                    for (PublishedMessage pm : toRemove) {
                        unreceivedMessages.remove(pm);
                    }
                }
                Thread.sleep(houseKeepingThreadSleepDurationMilliseconds);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
    }

    private boolean isMessageExpired(PublishedMessage pm) {
        return System.currentTimeMillis() - pm.getCreatedTimestamp() > pm.getTtl();
    }

    private static class UnreceivedMessagesUpdater extends Thread {
        private PublishedMessage publishedMessage;
        private ClientInfo clientInfo;

        public UnreceivedMessagesUpdater(PublishedMessage publishedMessage, ClientInfo clientInfo) {
            this.publishedMessage = publishedMessage;
            this.clientInfo = clientInfo;
        }

        @Override
        public void run() {
            synchronized (unreceivedMessages) {
                unreceivedMessages.getOrDefault(publishedMessage, new ArrayList<>()).add(clientInfo.getClientId());
            }
        }
    }

}
