package auction;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology;

import java.util.*;

public class Planner {

    private static final int MAX_TEMP = 1;
    private static final double CHOICE_THRESHOLD = 0.4;

    private State latestState;
    private State latestSimulation;
    private Random random;
    private double temperature;

    public Planner(List<Vehicle> vehicles) {
        temperature = MAX_TEMP;
        latestState = new State(vehicles);
        random = new Random();
    }

    public double getLastConfirmedCost() {
        return latestState.getCost();
    }

    public void confirmNewPlan() {
        latestState = latestSimulation;
        latestSimulation = null;
    }

    public List<Plan> getFinalPlan(List<Vehicle> vehicles) {
        return latestState.getPlans(vehicles);
    }

    public double simulateWithNewTask(Task task, int timeout) {
        long start = System.currentTimeMillis();
        long deadline = start + timeout;
        latestSimulation = latestState.clone();
        latestSimulation.addTask(task);
        temperature = MAX_TEMP;

        long time;
        double lastCost = latestSimulation.getCost();

        while ((time = System.currentTimeMillis()) < deadline) {
            List<State> neighbours = latestSimulation.chooseNeighbours();
            State candidate = localChoice(neighbours);

            double cost = candidate.getCost();

            if (cost < lastCost || random.nextDouble() <= temperature) {
                latestSimulation = candidate;
                lastCost = cost;
            }

            temperature = (time - start) / (double) timeout;
        }

        return lastCost;
    }


    /**
     * Return the best neighbor in term of the objective function
     *
     * @param neighbours
     * @return Best neighbor state
     */
    private State localChoice(List<State> neighbours) {
        State bestState = null;
        double bestCost = Double.POSITIVE_INFINITY;

        if (random.nextDouble() <= temperature) {
            return neighbours.get(random.nextInt(neighbours.size()));
        }

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

        public State() {

        }

        public State(List<Vehicle> vehicles) {
            firstTasks = new HashMap<>();
            nextTask = new HashMap<>();

            vehicles.forEach(v -> firstTasks.put(v, null));
        }

        public void addTask(Task task) {
            try {
                Vehicle candidate = firstTasks.keySet().stream().
                        filter(v -> v.capacity() <= task.weight)
                        .min(Comparator.comparingDouble(v -> v.homeCity().distanceTo(task.pickupCity)))
                        .orElseThrow(() -> new Exception("No vehicle can handle the task"));

                ConcreteTask pickup = ConcreteTask.pickup(task);
                ConcreteTask delivery = ConcreteTask.delivery(task);

                // Add to new vehicle
                ConcreteTask first = firstTasks.get(candidate);

                firstTasks.put(candidate, pickup);
                nextTask.put(pickup, delivery);
                nextTask.put(delivery, first);
            } catch (Exception e) {
                e.printStackTrace();
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

                if (!firstTasks.containsKey(vehicle)) {
                    System.err.println("Computing plan on adversary is not supported");
                    return;
                }

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
