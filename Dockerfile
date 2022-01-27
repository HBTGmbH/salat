FROM tomcat:9-jdk11

ADD target/tb.war /usr/local/tomcat/webapps/

EXPOSE 8080

CMD ["catalina.sh", "run"]
