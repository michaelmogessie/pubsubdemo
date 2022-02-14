FROM openjdk:8-jdk-alpine
VOLUME /tmp
ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS
COPY target/pubsubdemo-0.0.1-SNAPSHOT.jar pubsubdemo.jar
ENTRYPOINT exec java $JAVA_OPTS -jar pubsubdemo.jar