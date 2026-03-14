# ClusteredBankService - Comprehensive Learning Project

A complete Akka implementation demonstrating Event Sourcing, CQRS, Cluster Sharding, and Projections for a bank account system.

## Architecture Overview

- **Frontend/Client** sends POST/GET requests to Akka HTTP
- **Akka HTTP** converts requests into Commands and sends them to Cluster Sharding
- **Cluster Sharding** finds the node where that specific Persistent Actor (Bank Account) lives
- **Persistent Actor** validates the command and persists an Event to the Journal
- **Akka Projection** watches the Journal and updates read-optimized tables via Akka Streams

## Features Implemented

### Core Akka Concepts
- ✅ **Akka Typed Actors** - Type-safe actor implementation
- ✅ **Event Sourcing** - Persistent actors with event replay
- ✅ **CQRS Pattern** - Separate command and query models
- ✅ **Cluster Sharding** - Distributed actor management
- ✅ **Akka HTTP** - RESTful API endpoints
- ✅ **Akka Projections** - Read model updates
- ✅ **JSON Serialization** - Spray JSON integration

### Banking Domain Features
- Account creation with initial balance
- Money deposits and withdrawals
- Balance inquiries
- Account details retrieval
- Account closure
- Transaction history tracking
- Real-time statistics

## Project Structure

```
src/main/scala/com/clusteredbankservice/
├── Main.scala                    # Application bootstrap
├── domain/
│   └── BankAccount.scala        # Domain models, commands, events
├── actor/
│   └── BankAccountActor.scala   # Persistent actor implementation
├── sharding/
│   └── BankAccountSharding.scala # Cluster sharding setup
├── http/
│   ├── JsonFormats.scala         # JSON serialization
│   └── BankAccountRoutes.scala  # HTTP routes
└── projection/
    ├── ReadModel.scala           # Read model definitions
    └── BankAccountProjection.scala # Event projections

src/test/scala/com/clusteredbankservice/
├── actor/
│   └── BankAccountActorSpec.scala # Actor unit tests
├── http/
│   └── BankAccountRoutesSpec.scala # HTTP route tests
└── IntegrationTest.scala         # End-to-end tests
```

## API Endpoints

### Account Management
- `POST /api/accounts` - Create new account
- `GET /api/accounts/{accountId}` - Get account details
- `GET /api/accounts/{accountId}/balance` - Get account balance
- `POST /api/accounts/{accountId}/deposit` - Deposit money
- `POST /api/accounts/{accountId}/withdraw` - Withdraw money
- `POST /api/accounts/{accountId}/close` - Close account
- `GET /api/accounts/health` - Health check

### Request/Response Examples

#### Create Account
```bash
POST /api/accounts
{
  "accountId": "acc-123",
  "initialBalance": 1000.0,
  "owner": "John Doe"
}
```

#### Deposit Money
```bash
POST /api/accounts/acc-123/deposit
{
  "amount": 500.0
}
```

#### Get Balance
```bash
GET /api/accounts/acc-123/balance
```

Response:
```json
{
  "status": "success",
  "message": "Balance retrieved",
  "data": {
    "accountId": "acc-123",
    "balance": 1500.0
  }
}
```

## Running the Application

### Prerequisites
- Java 11 or higher (Java 21+ recommended)
- SBT 1.5.0 or higher

### Quick Start (Recommended)
Use the provided setup script which will install SBT if needed:

```bash
./setup_and_run.sh
```

### Manual Setup

1. **Install SBT (if not already installed):**
   ```bash
   # Using SDKMAN (recommended)
   curl -s "https://get.sdkman.io" | bash
   source "$HOME/.sdkman/bin/sdkman-init.sh"
   sdk install sbt
   
   # Or using Homebrew (macOS)
   brew install sbt
   ```

2. **Start the Application:**
   ```bash
   sbt run
   ```

The server will start on `http://localhost:8080`

### Testing the API

Use the provided test script to verify all endpoints:

```bash
# In a separate terminal, after the server is running
./test_api.sh
```

Or test manually with curl:

```bash
# Health check
curl http://localhost:8080/api/accounts/health

# Create account
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountId": "acc-123", "initialBalance": 1000.0, "owner": "John Doe"}'

# Get balance
curl http://localhost:8080/api/accounts/acc-123/balance
```

### Run Tests
```bash
sbt test
```

### Run Specific Tests
```bash
# Unit tests only
sbt "testOnly *ActorSpec *RoutesSpec"

# Integration tests only
sbt "testOnly IntegrationTest"
```

## Learning Outcomes

This project demonstrates the following Akka concepts:

### 1. Event Sourcing
- **Commands**: Intent to change state (CreateAccount, DepositMoney, etc.)
- **Events**: Facts that have occurred (AccountCreated, MoneyDeposited, etc.)
- **State**: Derived by replaying events
- **Persistence**: Events stored in journal for recovery

### 2. CQRS (Command Query Responsibility Segregation)
- **Write Model**: Persistent actors handling commands
- **Read Model**: Optimized for queries via projections
- **Separation**: Different data structures for writes vs reads

### 3. Cluster Sharding
- **Entity Distribution**: Automatic distribution across cluster nodes
- **Location Transparency**: Send messages without knowing actor location
- **Scalability**: Horizontal scaling of actors

### 4. Akka Projections
- **Event Processing**: Stream processing of persisted events
- **Read Model Updates**: Maintaining query-optimized views
- **Exactly-Once Processing**: Reliable event processing

### 5. Akka HTTP
- **REST API**: HTTP endpoints for external communication
- **JSON Handling**: Serialization/deserialization
- **Type Safety**: Typed request/response handling

## Configuration

### Database Configuration
The application uses SQLite for development (configurable in `application.conf`):

```hocon
akka.persistence.jdbc-journal.slick.db.url = "jdbc:sqlite:./target/akka-persistence-journal.db"
```

### Cluster Configuration
```hocon
akka.cluster.seed-nodes = [
  "akka://ClusteredBankServiceSystem@127.0.0.1:25520"
]
```

## Development Tips

### Adding New Features
1. **Domain First**: Define commands, events, and state
2. **Actor Logic**: Implement command/event handlers
3. **HTTP Layer**: Add routes and JSON formats
4. **Tests**: Write unit and integration tests
5. **Projections**: Update read models if needed

### Testing Strategy
- **Unit Tests**: Test actor behavior in isolation
- **Route Tests**: Test HTTP endpoints with mocks
- **Integration Tests**: Test complete workflows
- **Cluster Tests**: Test distributed behavior

### Monitoring
- **Akka Management**: Add for cluster monitoring
- **Metrics**: Add Prometheus metrics
- **Logging**: Configured with Logback

## Production Considerations

### Database
- Replace SQLite with PostgreSQL or MySQL
- Configure connection pooling
- Set up database migrations

### Cluster Management
- Add Akka Management for monitoring
- Configure split-brain resolution
- Set up proper seed nodes

### Security
- Add authentication/authorization
- HTTPS configuration
- Rate limiting

### Performance
- Tune actor mailbox sizes
- Optimize projection batching
- Configure appropriate timeouts

## Advanced Topics

### Custom Serializers
```scala
akka.actor.serialization-bindings {
  "com.clusteredbankservice.CustomMessage" = custom-serializer
}
```

### Event Versioning
- Handle schema evolution
- Migration strategies
- Backward compatibility

### Testing Strategies
- Persistence test kit
- Cluster test kit
- Property-based testing

## Troubleshooting

### Common Issues
1. **Cluster Formation**: Check seed node configuration
2. **Persistence**: Verify database connectivity
3. **Serialization**: Ensure JSON formats are correct
4. **Timeouts**: Adjust timeout values

### Debugging
- Enable debug logging
- Use Akka Management
- Check cluster status

## Further Learning

- **Akka Streams**: Reactive stream processing
- **Akka gRPC**: gRPC integration
- **Akka Persistence Cassandra**: NoSQL persistence
- **Akka Distributed Data**: CRDT implementation

## Resources

- [Akka Documentation](https://doc.akka.io/)
- [Akka Samples](https://github.com/akka/akka-samples)
- [Reactive Manifesto](https://www.reactivemanifesto.org/) 