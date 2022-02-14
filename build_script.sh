docker stop pubsubdemo-docker || true
docker rm pubsubdemo-docker || true
docker rmi pubsubdemo-docker || true
mvn clean package docker:build -Pprod
docker run -t -p 8485:8485 -d --name pubsubdemo-docker pubsubdemo-docker