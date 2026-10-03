MySQL Connector/J dependency

Required file:
mysql-connector-j-9.4.0.jar

build.bat automatically downloads it from Maven Central when internet access is available.
If automatic download is unavailable, download Connector/J 9.4.0 and place the JAR in this folder.

Do not create an empty placeholder JAR: Tomcat needs the real JDBC driver.
