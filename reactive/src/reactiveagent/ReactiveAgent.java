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
import java.util.Random;

public class ReactiveAgent implements ReactiveBehavior {

    private Random random;
    private double pPickup;
    private int numActions;
    private Agent myAgent;
    private int costPerKilometer;
    private TaskDistribution td;
    private Topology topology;

    private LinkedList<State> states;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {

        // Reads the discount factor from the agents.xml file.
        // If the property is not present it defaults to 0.95
        Double discount = agent.readProperty("discount-factor", Double.class,
                0.95);

        costPerKilometer = agent.vehicles().get(0).costPerKm();

        this.topology = topology;
        this.td = td;

        states = new LinkedList<>();

        for (City city1 : topology.cities()) {
            for (City city2 : topology.cities()) {
                if (city2.equals(city1)) continue;

                states.add(new State(city1, city2));
            }
            states.add(new State(city1, null));
        }

        for (State state1 : states) {
            for (State state2 : states) {
                state1.addToTransitionTable(state2);
            }
        }

        this.random = new Random();
        this.pPickup = discount;
        this.numActions = 0;
        this.myAgent = agent;
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        Action action;

        if (availableTask == null) {
            City currentCity = vehicle.getCurrentCity();
            action = new Move(currentCity.randomNeighbor(random));
        } else {
            //todo:
            action = new Pickup(availableTask);
        }

        if (numActions >= 1) {
            System.out.println("The total profit after " + numActions + " actions is " + myAgent.getTotalProfit() + " (average profit: " + (myAgent.getTotalProfit() / (double) numActions) + ")");
        }
        numActions++;

        return action;
    }

    public class State {
        public City currentCity;
        public City destination;
        public HashMap<City, Double> reward;
        public HashMap<City, HashMap<State, Double>> transitionTable;

        public State(City current, City dest) {
            currentCity = current;
            destination = dest;
            reward = new HashMap<>();
            transitionTable = new HashMap<>();
            currentCity.neighbors().forEach(city -> transitionTable.put(city, new HashMap<>()));
            if (destination != null && !transitionTable.containsKey(destination)) {
                transitionTable.put(destination, new HashMap<>());
            }
            initRewards();
        }

        private void initRewards() {
            currentCity.neighbors().forEach(city -> {
                if (city.equals(destination)) {
                    reward.put(city, td.reward(currentCity, city) - currentCity.distanceTo(city) * costPerKilometer);
                } else {
                    reward.put(city, -currentCity.distanceTo(city) * costPerKilometer);
                }
            });
            if (!reward.containsKey(destination)) {
                reward.put(destination, td.reward(currentCity, destination) - currentCity.distanceTo(destination) * costPerKilometer);
            }
        }

        public void addToTransitionTable(State state) {
            if (transitionTable.containsKey(state.currentCity)) {
                if (destination != null) {
                    if (state.destination != null) {
                        double probability = td.probability(currentCity, state.currentCity) * td.probability(state.currentCity, state.destination);
                        transitionTable.get(state.currentCity).put(state, probability);
                    } else {
                        double probability = td.probability(currentCity, state.currentCity);
                        for (City city2 : topology.cities()) {
                            probability *= 1 - td.probability(state.currentCity, city2);
                        }
                        transitionTable.get(state.currentCity).put(state, probability);
                    }
                } else {
                    if (state.destination != null) {
                        double probability = td.probability(state.currentCity, state.destination);
                        for (City city2 : topology.cities()) {
                            probability *= 1 - td.probability(currentCity, city2);
                        }
                        transitionTable.get(state.currentCity).put(state, probability);
                    } else {
                        double probability = 1;
                        for (City city2 : topology.cities()) {
                            probability *= 1 - td.probability(currentCity, city2);
                            probability *= 1 - td.probability(state.currentCity, city2);
                        }
                        transitionTable.get(state.currentCity).put(state, probability);
                    }
                }

            }
        }


    }

}
