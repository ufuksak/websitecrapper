FROM gradle:5.2.1-jdk-alpine

ADD --chown=gradle . /code
ADD build.gradle /code
WORKDIR /code
RUN chmod 755 fetch

RUN ./gradlew clean build -x test --stacktrace
