package info.michaelmogessie.pubsubdemo.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import info.michaelmogessie.pubsubdemo.handlers.WebSocketHandler;
import info.michaelmogessie.pubsubdemo.pojos.Message;

@RunWith(SpringRunner.class)
@WebMvcTest(MessageController.class)
@ActiveProfiles("test")
public class MessageControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private WebSocketHandler webSocketHandler;

    @Value("#{${message.topics}}")
    List<String> topics;

    @Value("${housekeepingthread.sleepduration.milliseconds}")
    int houseKeepingThreadSleepDurationMilliseconds;

    @Test
    public void givenMessagePublishMessageAndReturnSuccess() throws Exception {
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        Message message = new Message();
        message.setBody("32 degrees");
        message.setTopic("temperature");

        mvc.perform(post("/topics").content(new ObjectMapper().writeValueAsString(message))
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
    }

    @Test
    public void givenMessageWithInvalidTopicTryToPublishMessageAndReturnError()
            throws Exception {
        webSocketHandler = new WebSocketHandler(topics, houseKeepingThreadSleepDurationMilliseconds);
        Message message = new Message();
        message.setBody("32 degrees");
        message.setTopic("some unknown topic");

        mvc.perform(post("/topics").content(new ObjectMapper().writeValueAsString(message))
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

}
