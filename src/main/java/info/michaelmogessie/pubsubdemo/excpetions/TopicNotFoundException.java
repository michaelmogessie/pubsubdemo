package info.michaelmogessie.pubsubdemo.excpetions;

public class TopicNotFoundException extends Exception {
    private static final String MESSAGE = "TOPIC NOT FOUND";

    @Override
    public String getMessage() {
        return MESSAGE;
    }
}
