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

/**
 * This is the websocket handler class for the pubsub implementaton. It handles
 * websocket opening, closing and message sending and receiving. This class
 * implements the Runnable interface so it can run the housekeeping thread every
 * {{houseKeepingThreadSleepDurationMilliseconds}} milliseconds.
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler implements Runnable {

    // A map containing a list of clients subscribed to topics.
    private static Map<String, List<ClientInfo>> topicSubscriberMap = new HashMap<>();
    // A map containing published messages that have not been received yet and the
    // clients that have not received them.
    // Any message that has either been received by all clients or whose ttl has
    // expired will be eventually removed from this map.
    private static Map<PublishedMessage, List<String>> unreceivedMessages = new HashMap<>();
    // How often to run the housekeeping thread that discards unreceived messages.
    private int houseKeepingThreadSleepDurationMilliseconds;
    // Some string values, decalred here to avoid repitition in use.
    private static final String CLIENT_ID = "clientId";
    private static final String MESSAGE_TOPIC_NOT_FOUND = "COULD NOT UNSUBSCRIBE. TOPIC NOT FOUND.";
    public static final String MESSAGE_NOT_SUBSCRIBED = "YOU ARE NOT SUBSCRIBED TO THIS TOPIC";
    private static final String MESSAGE_MALFORMED_PAYLOAD = "MESSAGE PAYLOAD IS INVALID.";
    private static final String TOPIC_ACTION_SUBSCRIBE = "subscribe";
    private static final String TOPIC_ACTION_UNSUBSCRIBE = "unsubscribe";
    private static final String MESSAGE_NO_SUCH_ACTION = "The only allowed actions are subscribe and unsubscribe";

    // Initialize SLF4J logger.
    private static Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    /**
     * Constructor for the websocket hander. Spring will inject the list of topics
     * from the appropriate application.properties file (dev, prod) and a setting
     * for how often the housekeeping thread should run.
     * 
     * @param topics                                      The list of topics that is
     *                                                    read from the appropriate
     *                                                    application.properties
     *                                                    file.
     * @param houseKeepingThreadSleepDurationMilliseconds How often to run the
     *                                                    housekeeping thread
     *                                                    (milliseconds).
     */
    public WebSocketHandler(@Value("#{${message.topics}}") List<String> topics,
            @Value("${housekeepingthread.sleepduration.milliseconds}") int houseKeepingThreadSleepDurationMilliseconds) {
        this.houseKeepingThreadSleepDurationMilliseconds = houseKeepingThreadSleepDurationMilliseconds;
        topics.stream().forEach(topic -> {
            topicSubscriberMap.put(topic, new ArrayList<>());
        });
        topicSubscriberMap.put("temperature", new ArrayList<>());
    }

    /**
     * This method is called after a connection has been established by the server
     * and a client.
     * If the client had been disconnected earlier and has some unreceived messages,
     * the pubsub service will attempt to send it all the active messages that it
     * has not received yet.
     * 
     * @param session The websocket session.
     */
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
     * This method handles messages that are sent by clients. The messages are one
     * of:
     * subscribe - subscribe to a topic
     * unsubscribe - unsubscribe from a topic or
     * clientId - to allow a client to get it's connection Id so it can use it to
     * establish a new connection and obtain unreceived messages in the event of the
     * socket closing due to an error.
     * 
     * @param session     The websocket session.
     * @param textMessage The message that has been received from the client.
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
            // We must modify the map in a synchronized manner due to multiple clients.
            synchronized (topicSubscriberMap) {
                topicSubscriberMap.get(topicAndAction[0]).remove(clientInfo.get());
            }
        } else if (topicAndAction[1].equals(TOPIC_ACTION_SUBSCRIBE)) {
            ClientInfo clientInfo = new ClientInfo.Builder().clientId(session.getId()).webSocketSession(session)
                    .build();
            // We must modify the map in a synchronized manner due to multiple clients.
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

    /**
     * A method that attempts to extract a ClientInfo from the topicSubscriberMap.
     * 
     * @param session The client's websocket session.
     * @param topic   The topic the client is subscribed to.
     * @return An optional ClientInfo. Must be resolved by the caller to determine
     *         if the client exists or not.
     */
    private Optional<ClientInfo> getClientInfo(WebSocketSession session, String topic) {
        return topicSubscriberMap.get(topic).stream()
                .filter(ci -> ci.getWebSocketSession().getId().equals(session.getId())).findFirst();
    }

    /**
     * This method delivers previously attempted but underlivered messages to a
     * client.
     * 
     * @param clientId The ID of the client to deliver messages to.
     * @param session  The client's websocket session.
     * @throws IOException This exception is thrown if the websocket connection is
     *                     bad.
     */
    private void deliverUnreceivedMessages(String clientId, WebSocketSession session) throws IOException {
        // We must modify the map in a synchronized manner due to multiple clients.
        synchronized (unreceivedMessages) {
            for (PublishedMessage message : unreceivedMessages.keySet()) {
                if (unreceivedMessages.get(message).contains(clientId)) {
                    session.sendMessage(new TextMessage(message.getMessage()));
                    unreceivedMessages.get(message).remove(clientId);
                }
            }
        }
    }

    /**
     * This method is called by the REST controller to relay messages from a
     * publisher to all subscribers.
     * 
     * @param message The message that is being relayed.
     * @throws TopicNotFoundException This exception is thrown if a publisher
     *                                attempts to publish a message to a topic that
     *                                does not exist.
     */
    public static void publish(Message message) throws TopicNotFoundException {
        if (!topicSubscriberMap.containsKey(message.getTopic())) {
            throw new TopicNotFoundException();
        }
        TextMessage textMessage = new TextMessage(message.getBody());
        PublishedMessage publishedMessage = new PublishedMessage.Builder().message(message.getBody()).build();
        for (ClientInfo clientInfo : topicSubscriberMap.get(message.getTopic())) {
            try {
                clientInfo.getWebSocketSession().sendMessage(textMessage);
            } catch (Exception e) {
                logger.error(e.getMessage());
                // Here we spawn a short-lived thread that will add the message that could not
                // be sent to the unreceived messages map. We do this so the controller method
                // can return a response to the client without having to wait for this
                // operation.
                new WebSocketHandler.UnreceivedMessagesUpdater(publishedMessage, clientInfo).start();
            }
        }

    }

    /**
     * This method handlers websocket connection closing. It removes a client from
     * the topic subscriber map when the connection between the client and the
     * server closes.
     * 
     * @param session The client's websocket session that is closed.
     * @param status  The reason the connection was closed.
     * 
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClientInfo clientInfo = new ClientInfo.Builder().clientId(session.getId()).webSocketSession(session).build();
        synchronized (topicSubscriberMap) {
            topicSubscriberMap.values().stream().filter(clientInfoList -> clientInfoList.contains(clientInfo))
                    .collect(Collectors.toList())
                    .forEach(list -> list.remove(clientInfo));
        }
    }

    /**
     * The run method of the Runnable interface is implemented here. This will wake
     * up every {{houseKeepingThreadSleepDurationMilliseconds}} milliseconds and
     * remove previously unreceived messages that have either been received by all
     * subscribed clients or whose ttl has expired.
     */
    @Override
    public void run() {
        try {
            while (true) {
                synchronized (unreceivedMessages) {
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

    /**
     * Utility method that checks if a published messages ttl has expired.
     * 
     * @param pm The published message that has not been received by some clients.
     * @return Return true if the ttl has expired, return false otherwise.
     */
    private boolean isMessageExpired(PublishedMessage pm) {
        return System.currentTimeMillis() - pm.getCreatedTimestamp() > pm.getTtl();
    }

    /**
     * This inner class extends the Thread class so it can run in the background to
     * update the unreceived messages map with the published message that could not
     * be delivered to some clients.
     * 
     */
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
                if (!unreceivedMessages.containsKey(publishedMessage)) {
                    unreceivedMessages.put(publishedMessage, new ArrayList<>());
                }
                unreceivedMessages.get(publishedMessage).add(clientInfo.getClientId());
            }
        }
    }

    public static Map<String, List<ClientInfo>> getTopicSubscriberMap() {
        return topicSubscriberMap;
    }

    public static Map<PublishedMessage, List<String>> getUnreceivedMessages() {
        return unreceivedMessages;
    }
}
