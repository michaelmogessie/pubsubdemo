package info.michaelmogessie.pubsubdemo.pojos;

public class PublishedMessage {
    private long createdTimestamp;
    private long ttl;
    private String message;

    private PublishedMessage(Builder builder) {
        this.createdTimestamp = builder.createdTimestamp;
        this.message = builder.message;
        this.ttl = builder.ttl;
    }

    public static class Builder {
        private long ttl = 60000;
        private String message;
        private final long createdTimestamp;

        public Builder() {
            this.createdTimestamp = System.currentTimeMillis();
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder ttl(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public PublishedMessage build() {
            return new PublishedMessage(this);
        }
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public long getTtl() {
        return ttl;
    }

    public String getMessage() {
        return message;
    }

}
