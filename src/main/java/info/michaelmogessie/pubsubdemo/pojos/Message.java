package info.michaelmogessie.pubsubdemo.pojos;

public class Message {
    private String body;
    private String topic;

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return this.body;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

}
