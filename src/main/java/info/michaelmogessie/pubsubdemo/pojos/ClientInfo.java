package info.michaelmogessie.pubsubdemo.pojos;

import org.springframework.web.socket.WebSocketSession;

public class ClientInfo {
    private WebSocketSession webSocketSession;
    private String clientId;

    private ClientInfo() {

    }

    private ClientInfo(Builder builder) {
        this.webSocketSession = builder.webSocketSession;
        this.clientId = builder.clientId;
    }

    public static class Builder {
        private WebSocketSession webSocketSession;
        private String clientId;

        public Builder webSocketSession(WebSocketSession webSocketSession) {
            this.webSocketSession = webSocketSession;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public ClientInfo build() {
            return new ClientInfo(this);
        }
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public String getClientId() {
        return clientId;
    }

    public void setWebSocketSession(WebSocketSession webSocketSession) {
        this.webSocketSession = webSocketSession;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClientInfo other = (ClientInfo) obj;
        if (clientId == null) {
            if (other.clientId != null)
                return false;
        } else if (!clientId.equals(other.clientId))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((clientId == null) ? 0 : clientId.hashCode());
        return result;
    }

}
