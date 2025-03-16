package net.mhu.home.blat;


public class BatteryData {
    public static final float MAX_CAPACITY_AH = 105.0f;
    public static final float NOMINAL_VOLTAGE = 12.8f;
    private float voltage;
    private float current;
    private float capacityAh;
    private float powerLast1h;
    private float powerLast24h;

    public BatteryData(byte [] bytes) {

    }

    public float getCapacity() {
        return 100.0f * this.capacityAh / MAX_CAPACITY_AH;
    }

    public float getCapacityWh() {
        return this.capacityAh * NOMINAL_VOLTAGE;
    }

    public int getMinutesToFull() {
        if( powerLast1h <= 0)
            return -1;

        return (int)(60.0 * NOMINAL_VOLTAGE * (MAX_CAPACITY_AH - this.capacityAh ) / powerLast1h);
    }
    public int getMinutesToEmpty() {
        if( powerLast1h >= 0)
            return -1;

        return (int)(-60.0 * NOMINAL_VOLTAGE * this.capacityAh  / powerLast1h);
    }

    public float getPower() {
        return this.voltage * this.current;
    }

    public float getVoltage() {
        return voltage;
    }

    public float getCurrent() {
        return current;
    }

    public float getCapacityAh() {
        return capacityAh;
    }

    public float getPowerLast1h() {
        return powerLast1h;
    }

    public float getPowerLast24h() {
        return powerLast24h;
    }
}
