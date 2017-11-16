package auction;

import logist.simulation.Vehicle;
import logist.task.TaskSet;
import logist.topology.Topology;

import java.awt.*;

public class FastVehicle implements Vehicle {
    private int id;
    private int capacity;
    private int costPerKm;
    private String name;
    private Topology.City homeCity;

    public FastVehicle(Vehicle vehicle) {
        this.id = vehicle.id();
        this.capacity = vehicle.capacity();
        this.costPerKm = vehicle.costPerKm();
        this.name = vehicle.name();
        this.homeCity = vehicle.homeCity();
    }

    public FastVehicle(int capacity, Topology.City homeCity, int costPerKm) {
        this.id = 0;
        this.name = "";
        this.capacity = capacity;
        this.homeCity = homeCity;
        this.costPerKm = costPerKm;
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public int capacity() {
        return this.capacity;
    }

    @Override
    public Topology.City homeCity() {
        return this.homeCity;
    }

    @Override
    public int costPerKm() {
        return this.costPerKm;
    }

    // Unused methods
    @Override
    public double speed() {
        return Double.MAX_VALUE;
    }

    @Override
    public Topology.City getCurrentCity() {
        return null;
    }

    @Override
    public TaskSet getCurrentTasks() {
        return null;
    }

    @Override
    public long getReward() {
        return 0;
    }

    @Override
    public long getDistanceUnits() {
        return 0;
    }

    @Override
    public double getDistance() {
        return 0;
    }

    @Override
    public Color color() {
        return null;
    }
}
