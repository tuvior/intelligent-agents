package centralized;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;

import java.util.*;

public class CentralizedAgent implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private double choiceThreshold;
    private int convergenceThreshold;
    private int iterations;

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        choiceThreshold = agent.readProperty("choice-threshold", Double.class, 0.4);
        iterations = agent.readProperty("iterations", Integer.class, 100000);
        convergenceThreshold = agent.readProperty("convergence-threshold", Integer.class, 2000);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();

        List<Plan> solution = stochasticLocalSearch(vehicles, tasks);

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");

        return solution;
    }

    /**
     * Compute the optimal solution with stochastic local search.
     *
     * @param vehicles
     * @param tasks
     * @return The optimal plans
     */
    private List<Plan> stochasticLocalSearch(List<Vehicle> vehicles, TaskSet tasks) {
        long deadline = System.currentTimeMillis() + timeout_plan;
        State state = new State(vehicles, tasks);
        System.out.println("Initial solution cost: " + state.getCost());
        Random random = new Random();
        double lastCost = Double.MAX_VALUE;
        int unchangedIterations = 0;

        for (int i = 0; i < iterations; i++) {
            List<State> neighbours = state.chooseNeighbours();
            State candidate = localChoice(neighbours);

            double cost = candidate.getCost();

            if (Double.compare(lastCost, cost) == 0) {
                unchangedIterations++;
            } else {
                unchangedIterations = 0;
            }

            // if the solution hasn't gotten better in convergenceThreshold iterations, return it
            if (unchangedIterations > convergenceThreshold) {
                System.out.println("Unchanged");
                break;
            }

            if (random.nextDouble() <= choiceThreshold) {
                state = candidate;
                lastCost = cost;
            }

            // stop if we're passing the planning deadline
            if (System.currentTimeMillis() > deadline) break;
        }

        System.out.println("Found solution cost: " + state.getCost());
        return state.getPlans(vehicles);
    }

    /**
     * Return the best neighbor in term of the objective function
     *
     * @param neighbours
     * @return Best neighbor state
     */
    private State localChoice(List<State> neighbours) {
        Random random = new Random();
        State bestState = null;
        double bestCost = Double.POSITIVE_INFINITY;

        for (State state : neighbours) {
            double cost = state.getCost();

            if (cost < bestCost || cost == bestCost && random.nextBoolean()) {
                bestCost = cost;
                bestState = state;
            }
        }

        return bestState;
    }

    public static class State {
        public HashMap<Vehicle, ConcreteTask> firstTasks;
        public HashMap<ConcreteTask, ConcreteTask> nextTask;

        private State() {
        }

        public State(List<Vehicle> vehicles, TaskSet tasks) {
            firstTasks = new HashMap<>();
            nextTask = new HashMap<>();

            vehicles.forEach(v -> firstTasks.put(v, null));

            Iterator<Task> taskIterator = tasks.iterator();

            while (taskIterator.hasNext()) {
                vehicles.forEach(v -> {
                    if (taskIterator.hasNext()) {
                        Task task = taskIterator.next();

                        ConcreteTask pickup = ConcreteTask.pickup(task);
                        ConcreteTask deliver = ConcreteTask.delivery(task);

                        nextTask.put(pickup, deliver);
                        nextTask.put(deliver, null);

                        if (firstTasks.get(v) == null) {
                            firstTasks.put(v, pickup);
                        } else {
                            ConcreteTask lastTask = firstTasks.get(v);
                            while (nextTask.get(lastTask) != null) lastTask = nextTask.get(lastTask);

                            nextTask.put(lastTask, pickup);
                        }
                    }
                });
            }
        }

        public State clone() {
            State clone = new State();
            clone.nextTask = new HashMap<>(nextTask);
            clone.firstTasks = new HashMap<>(firstTasks);
            return clone;
        }

        public double getCost() {
            final double[] cost = {0};

            firstTasks.forEach(((vehicle, concreteTask) -> {
                if (concreteTask != null) {
                    double costPerKM = vehicle.costPerKm();
                    ConcreteTask current = concreteTask;

                    cost[0] += vehicle.getCurrentCity().distanceTo(current.task.pickupCity) * costPerKM;

                    while (nextTask.get(current) != null) {
                        ConcreteTask next = nextTask.get(current);
                        Topology.City startCity = current.getCity();
                        Topology.City endCity = next.getCity();

                        cost[0] += startCity.distanceTo(endCity) * costPerKM;
                        current = next;
                    }
                }
            }));

            return cost[0];
        }

        /**
         * Generate the plan for each vehicle from the state
         *
         * @param vehicles
         * @return The plan for each vehicle
         */
        public List<Plan> getPlans(List<Vehicle> vehicles) {
            ArrayList<Plan> plans = new ArrayList<>();

            // Generate a plan for each vehicle
            vehicles.forEach(vehicle -> {
                Plan plan = new Plan(vehicle.getCurrentCity());
                ConcreteTask current = firstTasks.get(vehicle);

                if (current != null) {

                    // Append moves actions until the first pickup
                    vehicle.getCurrentCity().pathTo(current.getCity()).forEach(plan::appendMove);

                    // Append first pickup
                    plan.appendPickup(current.task);

                    // Then between each task, append moves and the task
                    while (nextTask.get(current) != null) {
                        ConcreteTask next = nextTask.get(current);
                        Topology.City nextCity = next.getCity();
                        current.getCity().pathTo(nextCity).forEach(plan::appendMove);

                        if (next.action == ConcreteTask.Action.PICKUP) {
                            plan.appendPickup(next.task);
                        } else {
                            plan.appendDelivery(next.task);
                        }

                        current = next;
                    }
                }

                plans.add(plan);
            });

            return plans;

        }

        /**
         * Generate all the neighbors
         *
         * @return List of neighbors
         */
        public List<State> chooseNeighbours() {
            List<State> neighbors = new ArrayList<>();

            // Get random vehicle
            Vehicle vehicle;
            Random random = new Random();
            do {
                List<Vehicle> keys = new ArrayList<>(firstTasks.keySet());
                vehicle = keys.get(random.nextInt(keys.size()));
            } while (firstTasks.get(vehicle) == null);

            // Apply the change vehicle operator
            for (Vehicle v : firstTasks.keySet()) {
                if (vehicle == v) continue;
                State neighbor = changeVehicle(vehicle, v);
                if (Constraints.checkConstraints(neighbor)) {
                    neighbors.add(neighbor);
                }
            }

            // Apply the change task order operator
            ConcreteTask current = firstTasks.get(vehicle);

            while (nextTask.get(current) != null) {
                ConcreteTask other = nextTask.get(current);

                do {
                    if (current.isRelated(other)) break;


                    // Only do a swap if it doesn't break a pickup/deliver relationship,
                    // i.e. other is a delivery and gets moved before its pickup
                    if (checkIfValidSwap(current, other)) {
                        State neighbor = swapTasks(vehicle, current, other);

                        if (Constraints.checkConstraints(neighbor)) {
                            neighbors.add(neighbor);
                        }
                    }

                    other = nextTask.get(other);
                } while (other != null);

                current = nextTask.get(current);
            }


            return neighbors;
        }

        private State changeVehicle(Vehicle v1, Vehicle v2) {
            State neighbor = this.clone();

            // Remove the pickup, delivery and update times
            ConcreteTask pickup = neighbor.firstTasks.get(v1);
            ConcreteTask delivery = neighbor.removeFirstCouple(v1);

            // Add to new vehicle
            ConcreteTask first = neighbor.firstTasks.get(v2);

            neighbor.firstTasks.put(v2, pickup);
            neighbor.nextTask.put(pickup, delivery);
            neighbor.nextTask.put(delivery, first);

            return neighbor;
        }

        // Remove first pickup and its delivery
        private ConcreteTask removeFirstCouple(Vehicle vehicle) {
            // Remove pickup
            ConcreteTask pickup = firstTasks.get(vehicle);

            // Find the delivery times
            ConcreteTask prev = pickup;
            while (!pickup.isRelated(nextTask.get(prev)) && nextTask.get(prev) != null) {
                // Advance
                prev = nextTask.get(prev);
            }


            ConcreteTask delivery = nextTask.get(prev);

            // Remove pickup
            if (delivery == nextTask.get(pickup)) {
                firstTasks.put(vehicle, nextTask.get(delivery));
            } else {
                firstTasks.put(vehicle, nextTask.get(pickup));
            }

            // Remove delivery
            nextTask.put(prev, nextTask.get(delivery));
            return delivery;
        }

        private boolean checkIfValidSwap(ConcreteTask task1, ConcreteTask task2) {
            if (task2.action == ConcreteTask.Action.PICKUP) return true;
            ConcreteTask current = nextTask.get(task1);

            do {
                if (current.isRelated(task2)) return false;
                if (current == task2) return true;
                current = nextTask.get(current);
            } while (current != null);

            return true;
        }

        private State swapTasks(Vehicle v, ConcreteTask task1, ConcreteTask task2) {
            State neighbor = this.clone();

            // Get parents
            ConcreteTask parent1 = null;
            ConcreteTask parent2 = null;
            for (Map.Entry<ConcreteTask, ConcreteTask> map : nextTask.entrySet()) {
                if (task1 == map.getValue()) parent1 = map.getKey();
                if (task2 == map.getValue()) parent2 = map.getKey() == task1 ? task2 : map.getKey();
            }

            // Get children
            ConcreteTask child1 = nextTask.get(task1) == task2 ? task1 : nextTask.get(task1);
            ConcreteTask child2 = nextTask.get(task2);

            // Swap
            if (parent1 == null) {
                neighbor.firstTasks.put(v, task2);
            } else {
                neighbor.nextTask.put(parent1, task2);
            }
            neighbor.nextTask.put(parent2, task1);
            neighbor.nextTask.put(task1, child2);
            neighbor.nextTask.put(task2, child1);

            return neighbor;
        }
    }

    public static class ConcreteTask {
        public enum Action {PICKUP, DELIVERY}

        public Action action;
        public Task task;


        private ConcreteTask(Action action, Task task) {
            this.action = action;
            this.task = task;
        }

        public static ConcreteTask pickup(Task task) {
            return new ConcreteTask(Action.PICKUP, task);
        }

        public static ConcreteTask delivery(Task task) {
            return new ConcreteTask(Action.DELIVERY, task);
        }

        public Topology.City getCity() {
            return action == Action.PICKUP ? task.pickupCity : task.deliveryCity;
        }

        public boolean isRelated(ConcreteTask other) {
            if (other == null) return false;
            return action == Action.PICKUP && other.action == Action.DELIVERY && task.equals(other.task);
        }
    }

    /**
     * Constraints checker.
     * <p>
     * Note that not all the constraints need to be manually checked,
     * since the neighbors generation take into account the obvious
     * constraints as:
     * * Time constraints
     * * Vehicle constraints
     * * Order constraints
     * * All tasks delivered
     * <p>
     * Then only remains the weight constraint.
     */
    public static class Constraints {
        public static boolean checkConstraints(State state) {
            return checkWeight(state);
        }

        private static boolean checkWeight(State state) {
            return (state.firstTasks.entrySet()).parallelStream().noneMatch(entry -> {
                Vehicle vehicle = entry.getKey();
                ConcreteTask task = entry.getValue();

                int weight = 0;

                // Go through the pickups/deliveries and make sure we do not ever violate the capacity
                while (task != null) {
                    // Update carried weight
                    if (task.action == ConcreteTask.Action.PICKUP) {
                        weight += task.task.weight;
                    } else {
                        weight -= task.task.weight;
                    }

                    if (vehicle.capacity() < weight) {
                        return true;
                    }

                    task = state.nextTask.get(task);
                }

                return false;
            });
        }

    }
}