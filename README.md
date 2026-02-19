### Running Client Application

#### Prerequisites for Client
- Java 21+ (for client JAR)
- bash/zsh terminal
- Network access to server (localhost:9091)

#### Running the Client

**Step 1: Build Client (if not already built)**
```bash
cd .../bus_ticketer/bus-ticketer-client/client
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean package -DskipTests
```

**Step 2: Run Interactive Client**
```bash
# Navigate to client directory
cd .../bus_ticketer/bus-ticketer-client/client/target

# Run JAR
java -jar bus-ticketer-client-1.0.0.jar