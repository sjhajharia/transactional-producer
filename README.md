# Transactional Producer
This is the project I used to run a Transactional Producer sending off events to a topic as a part of a transaction with a transaction id.
The configs for connecting to the cluster are present in the `cluster.config` class where one needs to provide the bootstrap servers, the username and the password.
These have been replaced currently to prevent any leakages.

### Running the project
Build the project using:
```java
./gradlew run
```
You can use the enter button to send the events one after the other.