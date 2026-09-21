# CAN Masquerade Attack Detection

Java-based simulation of a Controller Area Network (CAN) gateway that detects and blocks masquerade attacks from suspicious or unauthorized ECU clients.

## Project Overview

This project simulates vehicle ECU communication using Java socket programming. A central CAN gateway receives CAN-like messages from simulated ECUs, checks them against learned normal communication patterns, and blocks abnormal or forged messages.

The simulation demonstrates how a vehicle network can be attacked when an unknown device tries to pretend to be a legitimate ECU. It also shows how graph-based normal communication patterns can be used to detect and stop suspicious communication.

## Main Features

- Simulated CAN gateway server
- Simulated ECU client
- WASD-based driving command simulation
- CAN-like message format
- Normal communication pattern learning
- Graph-style source and destination relationship checking
- Masquerade attack detection
- Suspicious command blocking
- Console-based intrusion alerts

## Technologies Used

- Java 21
- Java Socket Programming
- TCP/IP localhost communication
- Java collections such as `HashSet` and `HashMap`
- Maven project structure

## Message Format

The project uses a simple CAN-like text format:

```text
MSG_ID|SOURCE_ECU|DESTINATION_ECU|DATA|TIME
```

Example:

```text
100|ECU_ENGINE|ECU_DASHBOARD|SPEED=60|10:30:15
```

## Normal ECU Patterns

The gateway starts with learned normal communication patterns:

```text
ECU_ENGINE -> ECU_DASHBOARD
ECU_BRAKE  -> ECU_DASHBOARD
ECU_SENSOR -> ECU_DASHBOARD
ECU_ENGINE -> ECU_BRAKE
```

If a message does not match the learned pattern, the gateway treats it as suspicious and blocks the connection.

## Project Structure

```text
src/main/java/com/canids
├── Main.java
├── ECUTest.java
├── ecu
│   └── ECUSimulator.java
├── model
│   ├── CANMessage.java
│   ├── ClientInfo.java
│   └── ECU.java
└── server
    ├── CANGatewayServer.java
    ├── ClientHandler.java
    └── CANIntrusionDetectionSystem.java
```

## How to Run

Open the project folder:

```powershell
cd "G:\Programming\Java Programming\MiniProject 2\CANMasqueradeDetection"
```

Compile the project:

```powershell
javac -d target\classes src\main\java\com\canids\model\CANMessage.java src\main\java\com\canids\model\ClientInfo.java src\main\java\com\canids\model\ECU.java src\main\java\com\canids\server\CANIntrusionDetectionSystem.java src\main\java\com\canids\server\ClientHandler.java src\main\java\com\canids\server\CANGatewayServer.java src\main\java\com\canids\ecu\ECUSimulator.java src\main\java\com\canids\ECUTest.java src\main\java\com\canids\Main.java
```

Start the gateway in Terminal 1:

```powershell
java -cp target\classes com.canids.Main
```

Expected output:

```text
CAN Gateway started on port 5000
Normal communication learned...
```

Run the ECU test client in Terminal 2:

```powershell
java -cp target\classes com.canids.ECUTest
```

## Testing Normal Driving Mode

Choose:

```text
1
```

Then use:

```text
w = accelerate
a = steer left
s = brake
d = steer right
x = unwanted forged command
q = quit
```

Normal commands should be accepted:

```text
Gateway: VALID|Message accepted
```

The unwanted command should be blocked:

```text
Gateway: ABNORMAL COMMUNICATION|Message does not match the learned normal communication graph
Gateway: CONNECTION BLOCKED
```

## Testing Attacker Mode

Run `ECUTest` again and choose:

```text
2
```

Then send an attack command:

```text
s
```

Expected output:

```text
Gateway: MASQUERADE ATTACK DETECTED|Actual connection is from an unknown device: UNKNOWN_DEVICE
Gateway: CONNECTION BLOCKED
```

## IntelliJ IDEA Run Steps

1. Open the project in IntelliJ IDEA.
2. Set Project SDK to Java 21.
3. Run `com.canids.Main`.
4. Keep `Main` running.
5. Run `com.canids.ECUTest` in another run configuration.
6. Use normal mode or attacker mode to test detection.

## Example Attack Scenario

An unknown device connects to the gateway and sends:

```text
200|ECU_ENGINE|ECU_BRAKE|BRAKE=OFF
```

The message claims to be from `ECU_ENGINE`, but the actual connection is `UNKNOWN_DEVICE`. The gateway detects this as a masquerade attack and blocks the connection.

## Current Scope

This project is a simulation only. It does not connect to a real vehicle CAN bus or real ECU hardware. TCP/IP sockets are used to simulate CAN communication between ECUs and the gateway.

## Future Improvements

- Add JavaFX dashboard
- Add CSV logging for normal messages and attack events
- Display the communication graph visually
- Add vehicle state such as speed, brake, and steering
- Add more advanced anomaly detection rules
