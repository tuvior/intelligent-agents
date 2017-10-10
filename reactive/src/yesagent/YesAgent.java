package yesagent;

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

public class YesAgent implements ReactiveBehavior {

    private int numActions;
    private Agent myAgent;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {
        this.numActions = 0;
        this.myAgent = agent;
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        Action action;

        if (availableTask == null) {
            City currentCity = vehicle.getCurrentCity();
            action = new Move(getNearestNeighbor(currentCity));
        } else {
            action = new Pickup(availableTask);
        }

        if (numActions >= 1) {
            System.out.println("The total profit after " + numActions + " actions is " + myAgent.getTotalProfit() + " (average profit: " + (myAgent.getTotalProfit() / (double) numActions) + ")");
        }
        numActions++;

        return action;
    }

    private static City getNearestNeighbor(City city) {
        double minDist = Double.MAX_VALUE;
        City currentCity = null;
        for (City city2 : city.neighbors()) {
	        if (city.distanceTo(city2) < minDist) {
	            minDist = city.distanceTo(city2);
	            currentCity = city2;
            }
        }
        return currentCity;
    }
}
