#!/bin/bash

echo "=== Clustered Bank Service Setup and Run ==="
echo

# Function to detect OS and set appropriate paths
detect_environment() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        if command -v brew &> /dev/null; then
            LMDB_LIB_PATH="$(brew --prefix)/lib"
        else
            echo "❌ Homebrew not found. Please install Homebrew first."
            echo "   Run: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
            exit 1
        fi
        JAVA_VERSION="11.0.30-zulu"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        if command -v apt-get &> /dev/null; then
            LMDB_LIB_PATH="/usr/local/lib"
        elif command -v yum &> /dev/null; then
            LMDB_LIB_PATH="/usr/local/lib64"
        else
            LMDB_LIB_PATH="/usr/local/lib"
        fi
        JAVA_VERSION="11.0.30-zulu"
    else
        echo "❌ Unsupported operating system: $OSTYPE"
        exit 1
    fi
}

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"

# Detect environment
detect_environment

# Check if SDKMAN is installed
if ! command -v sdkman &> /dev/null; then
    echo "📦 Installing SDKMAN..."
    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
else
    echo "✅ SDKMAN is already installed"
    if [ -z "$SDKMAN_DIR" ]; then
        export SDKMAN_DIR="$HOME/.sdkman"
    fi
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
fi

# Install SBT
if ! command -v sbt &> /dev/null; then
    echo "📦 Installing SBT..."
    sdk install sbt
else
    echo "✅ SBT is already installed: $(sbt -version | head -n 1)"
fi

# Set up Java 11
echo "🔧 Setting up Java 11..."
if sdk list java | grep -q "$JAVA_VERSION"; then
    sdk use java "$JAVA_VERSION"
else
    echo "📦 Installing Java 11..."
    sdk install java "$JAVA_VERSION"
    sdk use java "$JAVA_VERSION"
fi

# Install LMDB if not present
echo "🔧 Checking LMDB installation..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    if ! brew list lmdb &> /dev/null; then
        echo "📦 Installing LMDB..."
        brew install lmdb
    else
        echo "✅ LMDB is already installed"
    fi
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if ! ldconfig -p | grep -q liblmdb; then
        echo "📦 Installing LMDB..."
        if command -v apt-get &> /dev/null; then
            sudo apt-get update && sudo apt-get install -y liblmdb-dev
        elif command -v yum &> /dev/null; then
            sudo yum install -y lmdb-devel
        else
            echo "⚠️  Please install LMDB manually for your Linux distribution"
        fi
    else
        echo "✅ LMDB is already installed"
    fi
fi

# Set up environment variables
export DYLD_LIBRARY_PATH="$LMDB_LIB_PATH:$DYLD_LIBRARY_PATH"
export LD_LIBRARY_PATH="$LMDB_LIB_PATH:$LD_LIBRARY_PATH"

# Create necessary directories
# mkdir -p logs
# mkdir -p target

echo
echo "🌍 Environment Setup:"
echo "   Java version: $(java -version 2>&1 | head -n 1)"
echo "   LMDB library path: $LMDB_LIB_PATH"
echo "   SBT version: $(sbt -version | head -n 1)"
echo

echo "🚀 Starting Clustered Bank Service..."
echo "   Server will start on: http://localhost:8080"
echo "   API endpoints available at: http://localhost:8080/api/accounts"
echo "   Press Ctrl+C to stop the server"
echo

# Start the application and keep it running
sbt "runMain com.clusteredbankservice.Main"
