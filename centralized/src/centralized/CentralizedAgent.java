package centralized;

//the list of imports

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

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 */
public class CentralizedAgent implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private double threshold;

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        threshold = 0.5;
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

    private List<Plan> stochasticLocalSearch(List<Vehicle> vehicles, TaskSet tasks) {
        State state = new State(vehicles, tasks);
        Random random = new Random();

        for (int i = 0; i < 1000; i++) {
            List<State> neighbours = state.chooseNeighbours();
            State candidate = localChoice(neighbours);

            if (random.nextDouble() <= threshold) {
                state = candidate;
            }
        }


        return state.getPlans(vehicles);
    }

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
        public HashMap<ConcreteTask, Integer> time;
        public HashMap<ConcreteTask, Vehicle> vehicle;

        private State() {
            firstTasks = new HashMap<>();
            nextTask = new HashMap<>();
            time = new HashMap<>();
            vehicle = new HashMap<>();
        }

        public State(List<Vehicle> vehicles, TaskSet tasks) {
            firstTasks = new HashMap<>();
            nextTask = new HashMap<>();
            time = new HashMap<>();
            vehicle = new HashMap<>();

            int taskPerVehicle = (int) Math.ceil(tasks.size() / vehicles.size());
            Iterator<Task> taskIterator = tasks.iterator();

            vehicles.forEach(v -> {
                for (int i = 0; i < taskPerVehicle && taskIterator.hasNext(); i++) {
                    Task task = taskIterator.next();
                    ConcreteTask pickup = ConcreteTask.pickup(task);
                    ConcreteTask deliver = ConcreteTask.delivery(task);

                    nextTask.put(pickup, deliver);
                    nextTask.put(deliver, null);
                    vehicle.put(pickup, v);
                    vehicle.put(deliver, v);

                    if (!firstTasks.containsKey(v)) {
                        time.put(pickup, 1);
                        time.put(deliver, 2);
                        firstTasks.put(v, pickup);
                    } else {
                        ConcreteTask lastTask = firstTasks.get(v);
                        while (nextTask.get(lastTask) != null) lastTask = nextTask.get(lastTask);

                        nextTask.put(lastTask, pickup);
                        time.put(pickup, time.get(lastTask) + 1);
                        time.put(deliver, time.get(pickup) + 1);
                    }
                }
            });
        }

        public State clone() {
            State clone = new State();
            clone.vehicle = new HashMap<>(vehicle);
            clone.time = new HashMap<>(time);
            clone.nextTask = new HashMap<>(nextTask);
            clone.firstTasks = new HashMap<>(firstTasks);
            return clone;
        }

        public double getCost() {
            final double[] cost = {0};

            firstTasks.forEach(((vehicle, concreteTask) -> {
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
            }));

            return cost[0];
        }

        public List<Plan> getPlans(List<Vehicle> vehicles) {
            ArrayList<Plan> plans = new ArrayList<>();

            vehicles.forEach(vehicle -> {
                Plan plan = new Plan(vehicle.getCurrentCity());
                ConcreteTask current = firstTasks.get(vehicle);
                vehicle.getCurrentCity().pathTo(current.getCity()).forEach(plan::appendMove);
                plan.appendPickup(current.task);

                while (nextTask.get(current) != null) {
                    ConcreteTask next = nextTask.get(current);
                    Topology.City nextCity = next.getCity();
                    current.getCity().pathTo(nextCity).forEach(plan::appendMove);

                    if (next.action == ConcreteTask.Action.PICKUP) {
                        plan.appendPickup(next.task);
                    } else {
                        plan.appendDelivery(next.task);
                    }
                }

                plans.add(plan);
            });

            return plans;

        }

        public List<State> chooseNeighbours() {
            List<State> neighbors = new ArrayList<>();

            // Get random vehicle
            Vehicle vehicle;
            do {
                Random random = new Random();
                List<Vehicle> keys = new ArrayList<>(firstTasks.keySet());
                vehicle = keys.get(random.nextInt(keys.size()));
            } while (firstTasks.get(vehicle) != null);

            // Apply the change vehicle operator
            for (Vehicle v : firstTasks.keySet()) {
                if (vehicle == v) continue;

                State neighbor = changeVehicle(vehicle, v);
                if (Constraints.checkConstraints(neighbor)) {
                    neighbors.add(changeVehicle(vehicle, v));
                }
            }

            // Apply the change task order operator
            ConcreteTask current = firstTasks.get(vehicle);

            while (nextTask.get(current) != null) {
                ConcreteTask other = nextTask.get(current);

                while (nextTask.get(other) != null) {
                    if (current.isRelated(other)) break;

                    other = nextTask.get(other);
                }

                current = nextTask.get(current);
            }

            return null;
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

            // Update times of new vehicle
            ConcreteTask current = pickup;
            int time = 1;
            while (current != null) {
                neighbor.time.put(current, time++);
                current = neighbor.nextTask.get(current);
            }

            return neighbor;
        }

        // Remove first pickup and its delivery and update times
        private ConcreteTask removeFirstCouple(Vehicle vehicle) {
            // Remove pickup
            ConcreteTask pickup = firstTasks.get(vehicle);
            firstTasks.put(vehicle, nextTask.get(pickup));

            // Find the delivery and update times at the same time
            ConcreteTask prev = pickup;
            while (!nextTask.get(prev).task.equals(pickup.task)) {
                // Update time
                time.put(prev, time.get(prev) - 1);

                // Advance
                prev = nextTask.get(prev);
            }

            // Remove delivery
            ConcreteTask delivery = nextTask.get(prev);
            nextTask.put(prev, nextTask.get(delivery));

            // Update times after delivery
            ConcreteTask task = nextTask.get(prev);
            while (task != null) {
                time.put(task, time.get(task) - 2);
                task = nextTask.get(task);
            }

            return delivery;
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
            return action == Action.PICKUP && other.action == Action.DELIVERY && task.equals(other.task);
        }
    }

    /**
     * Constraints checker.
     * <p>
     * Note that not all the constraints need to be manually checked,
     * since the neighbors generation take into account the obvious
     * contraints as:
     * * Time constraints
     * * Vehicle constraints
     * * Order constraints
     * * All tasks delivered constraint
     * <p>
     * Then only remains the weight constraint.
     */
    public static class Constraints {
        public static boolean checkConstraints(State state) {
            return checkWeight(state);
        }

        private static boolean checkWeight(State state) {
            return state.firstTasks.entrySet().stream().allMatch(entry -> {
                Vehicle vehicle = entry.getKey();
                ConcreteTask task = entry.getValue();

                int weight = 0;
                do {
                    // Update carried weight
                    if (task.action.equals(ConcreteTask.Action.PICKUP)) {
                        weight += task.task.weight;
                    } else {
                        weight -= task.task.weight;
                    }

                    if (vehicle.capacity() < weight) {
                        return false;
                    }

                    task = state.nextTask.get(task);
                } while (task != null);

                return true;
            });
        }

    }
}