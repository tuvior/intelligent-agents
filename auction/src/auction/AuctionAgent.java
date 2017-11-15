package auction;

//the list of imports

import logist.LogistSettings;
import logist.Measures;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AuctionAgent implements AuctionBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private Vehicle vehicle;
    private City currentCity;
    private long timeout_setup;
    private long timeout_plan;
    private long timeout_bid;

    private static boolean [] xd  = new boolean [Integer.MAX_VALUE];

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

        try {
            LogistSettings ls = Parsers.parseSettings("config/settings_default.xml");
            timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
            timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
            timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);
        } catch (Exception ex) {
            System.err.println("There was a problem loading the configuration file.");
        }

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicle = agent.vehicles().get(0);
        this.currentCity = vehicle.homeCity();

        long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
        this.random = new Random(seed);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        if (winner == agent.id()) {
            currentCity = previous.deliveryCity;
        }
    }

    private void evaluateTopology(Topology topology, TaskDistribution distribution) {
        topology.cities().forEach(city -> {
            final double[] totalProb1 = {0};
            final double[] totalProb2 = {0};
            topology.cities().forEach(city2 -> {
                totalProb1[0] += distribution.probability(city, city2);
                totalProb2[0] += distribution.probability(city2, city);
            });
        });
    }

    @Override
    public Long askPrice(Task task) {

        if (vehicle.capacity() < task.weight)
            return null;

        long distanceTask = task.pickupCity.distanceUnitsTo(task.deliveryCity);
        long distanceSum = distanceTask
                + currentCity.distanceUnitsTo(task.pickupCity);
        double marginalCost = Measures.unitsToKM(distanceSum
                * vehicle.costPerKm());

        double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
        double bid = ratio * marginalCost;

        return (long) Math.round(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

        Plan planVehicle1 = naivePlan(vehicle, tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size())
            plans.add(Plan.EMPTY);

        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity))
                plan.appendMove(city);

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path())
                plan.appendMove(city);

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
}
