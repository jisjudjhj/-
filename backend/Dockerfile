FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m"

WORKDIR /app
COPY --from=build /build/target/ecommerce-recommendation-1.0.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
