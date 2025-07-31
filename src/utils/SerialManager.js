// Demo SerialManager for testing without hardware
class SerialManager {
  constructor() {
    this.connections = new Map();
    this.isConnected = new Map();
  }

  async connectSerial(deviceId, portPath) {
    console.log(`Demo: Connecting ${deviceId} to ${portPath}`);
    // Simulate connection delay
    await new Promise(resolve => setTimeout(resolve, 1000));
    this.isConnected.set(deviceId, true);
    return true;
  }

  async connectNetwork(deviceId, ipAddress, port) {
    console.log(`Demo: Connecting ${deviceId} to ${ipAddress}:${port}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
    this.isConnected.set(deviceId, true);
    return true;
  }

  async disconnect(deviceId) {
    console.log(`Demo: Disconnecting ${deviceId}`);
    this.isConnected.set(deviceId, false);
  }

  isDeviceConnected(deviceId) {
    return this.isConnected.get(deviceId) || false;
  }

  async getReliableEDMReading(deviceId) {
    // Simulate measurement delay
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // Return realistic mock EDM reading
    return {
      slopeDistanceMm: 8000 + Math.random() * 10000, // 8-18m
      verticalAngleDecimal: 88 + Math.random() * 4, // 88-92 degrees
      horizontalAngleDecimal: Math.random() * 360,
      statusCode: 85,
      reliable: true
    };
  }

  async sendToScoreboard(deviceId, data) {
    console.log(`Demo: Sending to scoreboard: ${data}`);
    return true;
  }

  async readWindGauge(deviceId) {
    await new Promise(resolve => setTimeout(resolve, 5000)); // 5 second measurement
    return (Math.random() * 4) - 2; // -2 to +2 m/s
  }

  async disconnectAll() {
    console.log('Demo: Disconnecting all devices');
    this.isConnected.clear();
  }
}

export default new SerialManager();
