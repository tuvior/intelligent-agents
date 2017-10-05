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

import java.util.*;

public class ReactiveAgent implements ReactiveBehavior {

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

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
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}

	private Map<State, Double> learnValues(List<State> states, double discount) {
		Map<State, Double> values = new HashMap<>();
		Map<State, Double> previousValues = new HashMap<>();

		while (!goodEnoughValues(values, previousValues)) {
			previousValues = new HashMap<>(values);

			// For each state
			states.forEach(state -> {
				final double[] maxValue = {0};
				// For each action, aka destination city
				state.reward.forEach((action, reward) -> {
					final int[] expectedNextValue = {0};

					state.transitionTable.get(action).forEach((nextState, p) -> {
						expectedNextValue[0] += p * values.get(nextState);
					});

					maxValue[0] = Math.max(maxValue[0], reward + discount * expectedNextValue[0]);
				});

				values.put(state, maxValue[0]);
			});
		}

		return values;
	}

	private boolean goodEnoughValues(Map<State, Double> current, Map<State, Double> previous) {
		double delta = 1;

		for (Map.Entry<State, Double> entry : current.entrySet()) {
			if (Math.abs(entry.getValue() - previous.get(entry.getKey())) > delta) {
				return false;
			}
		}

		return true;
	}
}