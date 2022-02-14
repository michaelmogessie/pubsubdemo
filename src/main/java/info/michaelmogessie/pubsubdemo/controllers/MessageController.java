package info.michaelmogessie.pubsubdemo.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import info.michaelmogessie.pubsubdemo.excpetions.TopicNotFoundException;
import info.michaelmogessie.pubsubdemo.handlers.WebSocketHandler;
import info.michaelmogessie.pubsubdemo.pojos.Message;

@RestController
@CrossOrigin(origins = "*", allowCredentials = "true", allowedHeaders = "*")
public class MessageController {

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
