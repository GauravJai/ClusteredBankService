#!/bin/bash

# Set up Java 11 environment for LMDB compatibility
if [ -z "$SDKMAN_DIR" ]; then
    export SDKMAN_DIR="$HOME/.sdkman"
fi
source "$SDKMAN_DIR/bin/sdkman-init.sh"
sdk use java 11.0.30-zulu

# Set up LMDB library path (detect OS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    if command -v brew &> /dev/null; then
        export DYLD_LIBRARY_PATH="$(brew --prefix)/lib:$DYLD_LIBRARY_PATH"
    fi
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    export LD_LIBRARY_PATH="/usr/local/lib:$LD_LIBRARY_PATH"
fi

echo "=== Akka API Testing Script ==="
echo "Using Java: $(java -version 2>&1 | head -1)"
echo

BASE_URL="http://localhost:8080/api/accounts"

# Function to test endpoint
test_endpoint() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4
    
    echo "🧪 Testing: $description"
    echo "Request: $method $url"
    if [ -n "$data" ]; then
        echo "Data: $data"
        response=$(curl -s -X $method -H "Content-Type: application/json" -d "$data" "$url")
    else
        response=$(curl -s -X $method "$url")
    fi
    echo "Response: $response"
    echo "---"
}

# Wait for server to be ready
echo "⏳ Waiting for server to be ready..."
for i in {1..60}; do
    if curl -s "$BASE_URL/health" > /dev/null 2>&1; then
        echo "✅ Server is ready!"
        break
    fi
    echo "⏳ Waiting... ($i/60)"
    sleep 2
done

if ! curl -s "$BASE_URL/health" > /dev/null 2>&1; then
echo "❌ Server is not responding. Please start the application first:"
    echo "   export SDKMAN_DIR=/Users/gkj/.sdkman"
    echo "   source /Users/gkj/.sdkman/bin/sdkman-init.sh"
    echo "   sdk use java 11.0.30-zulu"
    echo "   sbt run"
    exit 1
    exit 1
fi

echo
echo "🚀 Starting API tests..."
echo

# Test 1: Health Check
test_endpoint "GET" "$BASE_URL/health" "" "Health Check"

# Test 2: Create Account
test_endpoint "POST" "$BASE_URL" '{
    "accountId": "test-account-1",
    "initialBalance": 1000.0,
    "owner": "John Doe"
}' "Create Account"

# Test 3: Get Account Balance
test_endpoint "GET" "$BASE_URL/test-account-1/balance" "" "Get Balance"

# Test 4: Deposit Money
test_endpoint "POST" "$BASE_URL/test-account-1/deposit" '{
    "amount": 500.0
}' "Deposit Money"

# Test 5: Get Updated Balance
test_endpoint "GET" "$BASE_URL/test-account-1/balance" "" "Get Updated Balance"

# Test 6: Withdraw Money
test_endpoint "POST" "$BASE_URL/test-account-1/withdraw" '{
    "amount": 200.0
}' "Withdraw Money"

# Test 7: Get Final Balance
test_endpoint "GET" "$BASE_URL/test-account-1/balance" "" "Get Final Balance"

# Test 8: Get Account Details
test_endpoint "GET" "$BASE_URL/test-account-1" "" "Get Account Details"

# Test 9: Create Second Account
test_endpoint "POST" "$BASE_URL" '{
    "accountId": "test-account-2",
    "initialBalance": 500.0,
    "owner": "Jane Smith"
}' "Create Second Account"

# Test 10: Try Invalid Withdrawal (should fail)
test_endpoint "POST" "$BASE_URL/test-account-1/withdraw" '{
    "amount": 2000.0
}' "Invalid Withdrawal (Should Fail)"

# Test 11: Close Account
test_endpoint "POST" "$BASE_URL/test-account-1/close" "" "Close Account"

# Test 12: Try Operation on Closed Account (should fail)
test_endpoint "GET" "$BASE_URL/test-account-1/balance" "" "Get Balance of Closed Account (Should Fail)"

echo
echo "✅ API testing completed!"
echo
echo "📊 Summary:"
echo "- Created 2 accounts"
echo "- Performed deposits and withdrawals"
echo "- Tested error conditions"
echo "- Tested account closure"
echo
echo "🔍 Check the application logs for detailed information about the operations."
