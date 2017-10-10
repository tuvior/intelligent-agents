package reactiveagent;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReactiveAgent implements ReactiveBehavior {

    private TaskDistribution td;
    private Topology topology;
    private Agent myAgent;
    private int numActions;
    private int costPerKilometer;
    private Map<State, City> strategy;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {

        // Reads the discount factor from the agents.xml file.
        // If the property is not present it defaults to 0.95
        Double discount = agent.readProperty("discount-factor", Double.class, 0.95);

        // picked from first vehicle and used to generate strategy
        this.costPerKilometer = agent.vehicles().get(0).costPerKm();
        this.topology = topology;
        this.td = td;
        this.numActions = 0;
        this.myAgent = agent;

        // initialise all possible states and create an optimized transition table
        LinkedList<State> states = new LinkedList<>();

        for (City city1 : topology.cities()) {
            for (City city2 : topology.cities()) {
                if (city2.equals(city1)) continue;

                states.add(new State(city1, city2, true));
            }
            // this state corresponds to being in a city without any task available
            states.add(new State(city1, null, true));
        }

        for (State state1 : states) {
            for (State state2 : states) {
                state1.addToTransitionTable(state2);
            }
        }

        // Compute strategy
        Map<State, Double> stateValues = learnValues(states, discount);
        this.strategy = computeStrategy(stateValues, discount);
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        Action action;

        // Create current state (equals has been overridden to compare it in the strategy)
        City taskDestination = availableTask == null ? null : availableTask.deliveryCity;
        State currentState = new State(vehicle.getCurrentCity(), taskDestination);

        City nextCity = strategy.get(currentState);

        if (availableTask == null || taskDestination != nextCity) {
            action = new Move(nextCity);
        } else {
            action = new Pickup(availableTask);
        }

        if (numActions >= 1) {
            System.out.println("The total profit after " + numActions + " actions is " + myAgent.getTotalProfit() + " (average profit: " + (myAgent.getTotalProfit() / (double) numActions) + ")");
        }
        numActions++;

        return action;
    }

    private Map<State, City> computeStrategy(Map<State, Double> stateValues, double learningFactor) {
        Map<State, City> strategy = new HashMap<>();

        stateValues.forEach((state, value) -> {
            final City[] bestAction = {null};
            final double[] bestValue = {Double.NEGATIVE_INFINITY};

            state.transitionTable.forEach((taskDestination, probabilities) -> {
                final double[] expectedValue = {0};
                probabilities.forEach((nextState, p) -> expectedValue[0] += p * stateValues.get(nextState));

                expectedValue[0] = state.reward.get(taskDestination) + learningFactor * expectedValue[0];

                if (expectedValue[0] > bestValue[0]) {
                    bestValue[0] = expectedValue[0];
                    bestAction[0] = taskDestination;
                }
            });

            strategy.put(state, bestAction[0]);
        });

        return strategy;
    }

    private Map<State, Double> learnValues(List<State> states, double discount) {
        Map<State, Double> values = states.stream().collect(Collectors.toMap(s -> s, s -> Double.NEGATIVE_INFINITY));
        Map<State, Double> previousValues;

        do {
            previousValues = new HashMap<>(values);

            // For each state
            states.forEach(state -> {
                final double[] maxValue = {Double.NEGATIVE_INFINITY};
                // For each action, aka destination city
                state.reward.forEach((action, reward) -> {
                    final int[] expectedNextValue = {0};

                    state.transitionTable.get(action).forEach(
                            (nextState, p) -> expectedNextValue[0] += p * values.get(nextState));

                    maxValue[0] = Math.max(maxValue[0], reward + discount * expectedNextValue[0]);
                });

                values.put(state, maxValue[0]);
            });
        } while (!goodEnoughValues(values, previousValues));

        return values;
    }

    private boolean goodEnoughValues(Map<State, Double> current, Map<State, Double> previous) {
        double delta = 0.1;

        for (Map.Entry<State, Double> entry : current.entrySet()) {
            if (Math.abs(entry.getValue() - previous.get(entry.getKey())) > delta) {
                return false;
            }
        }

        return true;
    }

    public class State {
        public City currentCity;
        public City taskDestination;
        public HashMap<City, Double> reward;
        public HashMap<City, HashMap<State, Double>> transitionTable;

        public State(City current, City dest) {
            this(current, dest, false);
        }

        public State(City current, City dest, boolean withRewards) {
            currentCity = current;
            taskDestination = dest;
            if (withRewards) {
                reward = new HashMap<>();
                transitionTable = new HashMap<>();
                currentCity.neighbors().forEach(city -> transitionTable.put(city, new HashMap<>()));
                if (taskDestination != null && !transitionTable.containsKey(taskDestination)) {
                    transitionTable.put(taskDestination, new HashMap<>());
                }
                initRewards();
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof State)) return false;
            if (obj == this) return true;

            State other = (State) obj;
            return this.currentCity == other.currentCity && this.taskDestination == other.taskDestination;
        }

        @Override
        public int hashCode() {
            return taskDestination == null ? currentCity.hashCode() :
                    (currentCity.hashCode() ^ taskDestination.hashCode());
        }

        private void initRewards() {
            currentCity.neighbors().forEach(city -> {
                if (city.equals(taskDestination)) {
                    reward.put(city, td.reward(currentCity, city) - currentCity.distanceTo(city) * costPerKilometer);
                } else {
                    reward.put(city, -currentCity.distanceTo(city) * costPerKilometer);
                }
            });
            if (taskDestination != null && !reward.containsKey(taskDestination)) {
                reward.put(taskDestination, td.reward(currentCity, taskDestination) - currentCity.distanceTo(taskDestination) * costPerKilometer);
            }
        }

        public void addToTransitionTable(State state) {
            // if this doesn't apply it means we're trying to add an impossible transition
            // what is possible is either a neighbour of the city, or the destination of its available task
            if (transitionTable.containsKey(state.currentCity)) {
                double probability = td.probability(currentCity, taskDestination) * td.probability(state.currentCity, state.taskDestination);
                transitionTable.get(state.currentCity).put(state, probability);
            }
        }


    }
}