docker stop pubsubdemo-docker || true
docker rm pubsubdemo-docker || true
docker rmi pubsubdemo-docker || true
mvn clean package -DskipTests -Pprod
docker build -t pubsubdemo-docker:latest .
docker run -p 8091:8091 -d --name pubsubdemo-docker pubsubdemo-docker