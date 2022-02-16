package info.michaelmogessie.pubsubdemo.configs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import info.michaelmogessie.pubsubdemo.handlers.WebSocketHandler;

/**
 * This is a Spring configuration class that is used to register websocket
 * endpoint handlers.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private WebSocketHandler webSocketHandler;

    /**
     * Adds a websocket handler to the websocket handler registry.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/start").setAllowedOrigins("*");
        new Thread(webSocketHandler).start();
    }
}
