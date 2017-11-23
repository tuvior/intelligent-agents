package auction;

import logist.LogistSettings;
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

import java.util.*;
import java.util.stream.Collectors;

public class AuctionAgent implements AuctionBehavior {

    private static final int COST_KM = 5;
    private static final double LOSS_THRESHOLD = 0.8;

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private long timeout_setup;
    private long timeout_plan;
    private long timeout_bid;

    private Planner planner;

    private Adversary adversary;
    private List<Task> tasks;
    private long payment;

    /**
     * ASSUMPTIONS:
     * - always 2 agents
     */

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
        this.tasks = new ArrayList<>();
        this.payment = 0;
        this.random = new Random();
        this.planner = new Planner(agent.vehicles());

        // Init adversary
        List<FastVehicle> advVehicles = FastVehicle.generateVehicles(agent.vehicles(), topology, false, false, true, FastVehicle.HomeCityRandomness.NEIGHBOR);
        this.adversary = new Adversary(advVehicles);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        boolean win = winner == agent.id();
        if (win) {
            tasks.add(previous);
            payment += bids[agent.id()];
            planner.confirmNewPlan();
        }
        
        adversary.auctionResult(previous, bids[1 - agent.id()], !win);
    }

    @Override
    public Long askPrice(Task task) {
        double newAdvPrice = adversary.planner.simulateWithNewTask(task, timeout_bid, false);
        long advGain = adversary.payment;
        long marginalCost = (long) newAdvPrice - advGain;

        long ourMarginal = (long) planner.simulateWithNewTask(task, timeout_bid, true);

        if (marginalCost >= ourMarginal) {
            // TODO: adjust
        } else if (marginalCost > ourMarginal * LOSS_THRESHOLD) {
            
        } else {
            return ourMarginal;
        }

        return 0L;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        // TODO: stochastic local search again
        return null;
    }

    /**
     * The method generates an optimal plan with all assigned tasks and addition of newTask
     * The current total payment received by the agent is then compared against the cost of this new optimal plan,
     * the difference between the two gives a lower bound in terms of bid required to generate profit if the auction
     * was won.
     *
     * @param newTask auctioned task to be evaluated
     * @return the minimum bid to be even in terms of revenue
     */
    private long planCostRequirement(Task newTask) {
        // TODO: stochastic local search I guess, then (plan cost - payment) is the result
        return 0;
    }

    /**
     * TODO: figure out if we can find something meaningful to optimise undercutting prices without losses
     *
     * @param topology     the topology for this simulation
     * @param distribution the task distribution for this simulation
     */
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

    /**
     * class to represent everything related to the adversary
     */
    public class Adversary {
        public List<? extends Vehicle> vehicles;
        public List<Task> tasks;
        public HashMap<Task, Long> bids;
        public Planner planner;
        public long payment;

        public Adversary(List<? extends Vehicle> vehicles) {
            this.vehicles = vehicles;
            tasks = new ArrayList<>();
            bids = new HashMap<>();
            planner = new Planner(vehicles);
            payment = 0;
        }

        public void auctionResult(Task task, long bid, boolean winner) {
            if (winner) {
                payment += bid;
                tasks.add(task);
                planner.confirmNewPlan();
            }
            bids.put(task, bid);
        }

        /**
         * The method tries to estimate the best case scenario for the adversary adding this task to their plan
         * it picks a city that already needs to be visited as bridge for the pickup and then assumes no
         * direct delivery will be performed. Without reusing the bridge city (as it would imply backtracking)
         * it looks for another city that will need to be visited and calculates the cost of delivery to only
         * start from there, meaning that the rest of the path will be part of the already existing plan.
         * In case of first task it assumes the distance to pickup as average distance from the pickup city
         *
         * @param newTask the task being auctioned
         * @return a lower bound for the adversary's cost
         */
        public double getMinCostForNewTask(Task newTask) {
            double pickupGap = Double.POSITIVE_INFINITY;
            final double[] deliveryGap = {newTask.pickupCity.distanceTo(newTask.deliveryCity)};

            if (tasks.size() == 0) {
                return (averageDistance(newTask.pickupCity) + deliveryGap[0]) * COST_KM;
            }

            Set<City> bridgeCities = new HashSet<>();

            for (Task t : tasks) {
                double pickupDist = t.pickupCity.distanceTo(newTask.pickupCity);
                if (pickupDist < pickupGap) {
                    pickupGap = pickupDist;
                    bridgeCities.clear();
                    bridgeCities.add(t.pickupCity);
                } else if (pickupDist == pickupGap) {
                    bridgeCities.add(t.pickupCity);
                }
                double deliveryDist = t.deliveryCity.distanceTo(newTask.pickupCity);
                if (deliveryDist < pickupGap) {
                    pickupGap = deliveryDist;
                    bridgeCities.clear();
                    bridgeCities.add(t.deliveryCity);
                } else if (deliveryDist == pickupGap) {
                    bridgeCities.add(t.deliveryCity);
                }
            }

            bridgeCities.forEach(bridgeCity -> {
                for (Task t : tasks) {
                    if (!t.pickupCity.equals(bridgeCity) && t.pickupCity.distanceTo(newTask.pickupCity) < deliveryGap[0]) {
                        deliveryGap[0] = t.pickupCity.distanceTo(newTask.pickupCity);
                    }
                    if (!t.deliveryCity.equals(bridgeCity) && t.deliveryCity.distanceTo(newTask.pickupCity) < deliveryGap[0]) {
                        deliveryGap[0] = t.deliveryCity.distanceTo(newTask.pickupCity);
                    }
                }
            });

            return (deliveryGap[0] + pickupGap) * COST_KM;
        }

        private double averageDistance(City city) {
            return topology.cities().stream().mapToDouble(city::distanceTo).sum() / topology.cities().size();
        }
    }
}
