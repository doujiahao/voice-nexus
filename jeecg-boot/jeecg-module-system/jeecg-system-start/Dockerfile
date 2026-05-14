FROM 192.168.1.22:30003/library/eclipse-temurin:21.0.1_12-jdk-alpine
ENV LANG=C.UTF-8
ENV PARAMS=""
WORKDIR /build
COPY ./*.jar /build/app.jar
ENTRYPOINT java ${JAVA_OPTS} -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai -jar /build/app.jar ${PARAMS}
