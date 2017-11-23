package auction;

import logist.simulation.Vehicle;
import logist.task.TaskSet;
import logist.topology.Topology;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FastVehicle implements Vehicle {
    private int id;
    private int capacity;
    private int costPerKm;
    private String name;
    private Topology.City homeCity;

    public enum HomeCityRandomness {NONE, NEIGHBOR, FULL};

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

    public static List<FastVehicle> generateVehicles(List<Vehicle> vehicles,
                                                     Topology topology,
                                                     boolean sameNumber,
                                                     boolean sameCapacity,
                                                     boolean sameCost,
                                                     HomeCityRandomness cityRandomness) {
        List<FastVehicle> newVehicles = new ArrayList<>();
        Random random = new Random();

        // Generate number of new vehicle
        int nVehicles = vehicles.size();
        if (!sameNumber) {
            nVehicles = random.nextBoolean() ? nVehicles - 1 : nVehicles + 1;
            nVehicles = Math.max(2, nVehicles);
        }

        // Generate vehicles
        for(int i = 0; i < nVehicles; ++i) {
            Vehicle reference = vehicles.get(i % vehicles.size());

            int capacity = generateValue(reference.capacity(), sameCapacity, 0.2);
            Topology.City city = generateHomeCity(topology, reference.homeCity(), cityRandomness);
            int costPerKm = generateValue(reference.costPerKm(), sameCost, 0.2);

            FastVehicle v = new FastVehicle(capacity, city, costPerKm);
            newVehicles.add(v);
        }

        return newVehicles;
    }

    private static int generateValue(int value, boolean same, double scaleWidth) {
        if (same) return value;

        Random random = new Random();
        double window = random.nextDouble() * scaleWidth;
        double factor = random.nextBoolean() ? 1 - window : 1 + window;

        return (int) (factor * value);
    }

    private static Topology.City generateHomeCity(Topology topology, Topology.City city, HomeCityRandomness randomness) {
        switch (randomness) {
            case NONE:
                return city;

            case NEIGHBOR:
                return city.randomNeighbor(new Random());

            case FULL:
                return topology.randomCity(new Random());
        }

        return city;
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

    // ----- Unused methods ----- //
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
