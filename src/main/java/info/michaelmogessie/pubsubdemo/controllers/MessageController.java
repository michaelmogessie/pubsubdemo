package info.michaelmogessie.pubsubdemo.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import info.michaelmogessie.pubsubdemo.excpetions.TopicNotFoundException;
import info.michaelmogessie.pubsubdemo.handlers.WebSocketHandler;
import info.michaelmogessie.pubsubdemo.pojos.Message;

/**
 * Controller class with resources that manage message publishing.
 */
@RestController
@CrossOrigin()
public class MessageController {

    /**
     * This controller resource is used to post a message by the message publisher.
     * This controller resource receives a message posted by a publisher and relays
     * it to the websocket handler, which will send it to clients subscribed to the
     * particular topic included inside the message.
     * 
     * @param message The message that is published.
     * @return An HTTP response.
     */
    @PostMapping("/topics")
    ResponseEntity<?> publishMesssage(@RequestBody Message message) {

        try {
            WebSocketHandler.publish(message);
            return ResponseEntity.ok().build();
        } catch (TopicNotFoundException e) {
            return ResponseEntity.badRequest().body("Unable to publish message, topic not found.");
        }
    }

}
