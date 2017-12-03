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
    private static final double INTEREST_THRESHOLD= 0.1;
    private static final double UNDERCUT_RATIO = 0.9;

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private long timeout_setup;
    private long timeout_plan;
    private long timeout_bid;

    private Planner planner;
    private int round = 0;

    private long currentPrediction = 0;

    private Adversary adversary;
    private List<Task> tasks;
    private long payment;


    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

        try {
            LogistSettings ls = Parsers.parseSettings("config/settings_auction.xml");
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

        System.out.println("Init Agent " + agent.id());
        agent.vehicles().forEach(v -> System.out.println(v.name() + " " + v.homeCity()));

       this.adversary = new Adversary();
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        System.out.println("Auction Result[" + agent.id() + "] : " + previous + " " + winner + " " + Arrays.toString(bids));
        boolean win = winner == agent.id();

        if (round == 0) {
            // anchor vehicle in both plans
            adversary.planner1.anchorVehicle(bids[1 - agent.id()], topology);
            adversary.planner2.anchorVehicle(bids[1 - agent.id()], topology);
        } else {
            // check if shuffling is required
            adversary.shuffleIfNeeded(win, bids[agent.id()], bids[1 - agent.id()]);
        }


        if (win) {
            tasks.add(previous);
            payment += bids[agent.id()];
            planner.confirmNewPlan();
        }

        System.out.println("Current Profit[" + agent.id() + "]: " + (payment - planner.lastConfirmedCost));
        profit = (long) (payment - planner.lastConfirmedCost);
        adversary.auctionResult(previous, bids[1 - agent.id()], !win);

        round++;
    }

    private long profit = 0;

    @Override
    public Long askPrice(Task task) {
        System.out.println("Ask Price[" + agent.id() + "] " + task);
        long time = timeout_bid - 2000;
        long  marginalCost = adversary.getNewMarginal(task, time / 2);

        long futureCost =  (long) planner.simulateWithNewTask(task, time / 2, false);
        long ourMarginal = futureCost - (long) planner.lastConfirmedCost;

        long bid = Math.max(0, ourMarginal);

        // don't bother with simulation on first task
        if (round == 0) {
            double ratio = 1.03 + random.nextDouble() * 0.1;
            bid *= ratio;
            currentPrediction = marginalCost;
            return bid;
        }

        // adjust marginalCost with calculated ratio if within bounds
        if (marginalCost < bid && marginalCost * adversary.averageRatio < bid) {
            marginalCost = (long) (marginalCost * adversary.averageRatio);
        }

        System.out.println("Adversary Marginal: " + marginalCost + " Agent Marginal: " + ourMarginal);

        if (marginalCost >= bid) {
            bid = (long) Math.max(bid + ((marginalCost - bid) * 0.5), Math.min(bid / UNDERCUT_RATIO, marginalCost * UNDERCUT_RATIO));
            if (bid < ourMarginal) {
                bid = ourMarginal;
            }
            System.out.print("1 -> "); // undercut adversary
        } else if (marginalCost > bid * LOSS_THRESHOLD) {
            if (evaluateCity(task.pickupCity) && evaluateCity(task.deliveryCity)) {
                bid = (long) Math.max(0, bid * LOSS_THRESHOLD);
                System.out.print("2 -> "); // undercut by going in red if good cities
            } else {
                System.out.print("3 -> "); // fallback to 0 profit
            }
        } else if (bid - marginalCost < profit * 0.25 && profit >= adversary.profit){
            bid = marginalCost - 1;
            System.out.print("4 -> "); // fallback to -1 undercut if not major loss
        } else {
            System.out.print("5 -> "); // fallback to 0 profit
        }
        System.out.println(bid);

        currentPrediction = marginalCost;

        return bid;
    }


    /**
     * @param city the city to be evaluated
     * @return probability that a future task will interest this city and a city visited by the agent
     */
    private boolean evaluateCity(City city) {
        HashSet<City> visitedCities = new HashSet<>();

        tasks.forEach(t -> {
            visitedCities.add(t.deliveryCity);
            visitedCities.add(t.pickupCity);
        });

        int nCities = topology.cities().size();
        final double[] pVisitTo = {0};
        final double[] pVisitFrom = {0};

        visitedCities.forEach(c -> {
            pVisitFrom[0] += distribution.probability(city, c) / (double) nCities;
            pVisitTo[0] += distribution.probability(c, city) / (double) nCities;
        });

        System.out.println(pVisitFrom[0] + pVisitTo[0]);

        return (pVisitFrom[0] + pVisitTo[0]) > INTEREST_THRESHOLD;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        return planner.getFinalPlan(vehicles, tasks, timeout_plan - 1000);
    }

    /**
     * class to represent everything related to the adversary
     */
    public class Adversary {
        public List<Task> tasks;
        public HashMap<Task, Long> bids;
        public Planner planner1;
        public Planner planner2;
        public long payment;
        public long profit;
        public double averageRatio;

        public Adversary() {
            // Init adversary with 2 configurations
            List<FastVehicle> advVehicles = FastVehicle.generateVehicles(agent.vehicles(), topology, false, false, true, FastVehicle.HomeCityRandomness.FULL);
            List<FastVehicle> advVehicles2 = FastVehicle.generateVehicles(agent.vehicles(), topology, true, true, true, FastVehicle.HomeCityRandomness.FULL);

            tasks = new ArrayList<>();
            bids = new HashMap<>();
            planner1 = new Planner(advVehicles);
            planner2 = new Planner(advVehicles2);
            payment = 0;
            averageRatio = 1;
        }

        /**
         * Updates all required data for next round
         *
         * @param task the task from this auction round
         * @param bid the adversary bid
         * @param winner whether the adversary won or not
         */
        public void auctionResult(Task task, long bid, boolean winner) {
            if (winner) {
                payment += bid;
                tasks.add(task);
                planner1.confirmNewPlan();
                planner2.confirmNewPlan();
                double ratio = bid / (double) currentPrediction;
                if (ratio > 0.9 && ratio < 1.5) {
                    averageRatio = (averageRatio + 2.0 * ratio ) / 3.0;
                }
            }
            profit = (long) planner.lastConfirmedCost - payment;
            bids.put(task, bid);
        }


        /**
         * Evaluates whether a vehicle reshuffle is needed in either of the plans
         *
         * @param agentWin if the agent won
         * @param bid1 agent bid
         * @param bid2 adversary bid
         */
        public void shuffleIfNeeded(boolean agentWin, long bid1, long bid2 ){
            if (agentWin && Math.abs(currentPrediction - bid2) >  bid2 * 0.25) {
                if (Math.abs(planner1.lastSimulatedCost - bid2) >  bid2 * 0.33) {
                    planner1.shuffleVehicles(topology);
                }
                if (Math.abs(planner2.lastSimulatedCost - bid2) >  bid2 * 0.33) {
                    planner2.shuffleVehicles(topology);
                }
            } else if (!agentWin) {
                double ratio = (bid1 - bid2)/(double) (bid1 - currentPrediction);
                if (ratio < 0.8 || ratio > 1.3) {
                    if (bid2 / planner1.lastConfirmedCost < 0.8 || bid2 / planner1.lastConfirmedCost > 1.3) {
                        planner1.shuffleVehicles(topology);
                    }

                    if (bid2 / planner2.lastConfirmedCost < 0.8 || bid2 / planner2.lastConfirmedCost > 1.3) {
                        planner2.shuffleVehicles(topology);
                    }
                }
            }
        }

        /**
         * @param task task to be added
         * @param timeout timeout for the simulation
         * @return average marginal cost of the two plans
         */
        public long getNewMarginal(Task task, long timeout) {
            return (long) simulateWithNewTask(task, timeout, true);
        }

        private double simulateWithNewTask(Task task, long timeout, boolean getMarginal) {
            long separateTimout = timeout / 2;

            double val1 = planner1.simulateWithNewTask(task, separateTimout, getMarginal);
            double val2 = planner2.simulateWithNewTask(task, separateTimout, getMarginal);

            System.out.println(val1 + " " +  val2 + " " + payment);

            return (val1 + val2) / 2.0;
        }

        /**
         * (WAS ONLY USED AS NAIVE ALTERNATIVE IN TESTING)
         *
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
            return topology.cities().stream().mapToDouble(city::distanceTo).sum() / (double) topology.cities().size();
        }
    }
}
