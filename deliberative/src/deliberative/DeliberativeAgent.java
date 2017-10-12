package deliberative;

/* import table */

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An optimal planner for one vehicle.
 */
public class DeliberativeAgent implements DeliberativeBehavior {

    enum Algorithm {BFS, ASTAR}

    enum Action{MOVE, PICKUP, DELIVER}

    /* Environment */
    Topology topology;
    TaskDistribution td;

    /* the properties of the agent */
    Agent agent;
    int capacity;

    /* the planning class */
    Algorithm algorithm;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {
        this.topology = topology;
        this.td = td;
        this.agent = agent;

        // initialize the planner
        int capacity = agent.vehicles().get(0).capacity();
        String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");

        // Throws IllegalArgumentException if algorithm is unknown
        algorithm = Algorithm.valueOf(algorithmName.toUpperCase());

        // ...
    }

    @Override
    public Plan plan(Vehicle vehicle, TaskSet tasks) {
        Plan plan;

        // Compute the plan with the selected algorithm.
        switch (algorithm) {
            case ASTAR:
                plan = planASTAR(vehicle, tasks);
                break;
            case BFS:
                plan = planBFS(vehicle, tasks);
                break;
            default:
                throw new AssertionError("Should not happen.");
        }
        return plan;
    }

    private Plan planBFS(Vehicle vehicle, TaskSet tasks) {

        return null;
    }

    private Plan planASTAR(Vehicle vehicle, TaskSet tasks) {

        return null;
    }

    @Override
    public void planCancelled(TaskSet carriedTasks) {

        if (!carriedTasks.isEmpty()) {
            // This cannot happen for this simple agent, but typically
            // you will need to consider the carriedTasks when the next
            // plan is computed.
        }
    }

    public class Node {
        public City agentPosition;
        public Action parentAction;
        public double weightCarried;
        public double cost;
        public int level;

        public Set<Task> tasksCarried;
        public Set<Task> tasksAvailable;

        public Node parent;
        public Node next;

        public Node(City positon , Node parent, Action action, Vehicle vehicle) {
            this(positon, parent, action, null, vehicle);
        }

        public Node(City positon , Node parent, Action action, Task toProcess, Vehicle vehicle) {
            agentPosition = positon;
            this.parent = parent;
            parentAction = action;
            cost = parent.cost;
            level = parent.level++;
            tasksAvailable = new HashSet<>(parent.tasksAvailable);
            tasksCarried = new HashSet<>(parent.tasksCarried);


            switch(action) {
                case MOVE:
                    cost += parent.agentPosition.distanceTo(agentPosition) * vehicle.costPerKm();
                case PICKUP:
                    tasksAvailable.remove(toProcess);
                    tasksCarried.add(toProcess);
                    weightCarried += toProcess.weight;
                case DELIVER:
                    tasksCarried.remove(toProcess);
                    weightCarried -= toProcess.weight;

            }
        }

        public List<Node> getSuccessors(Vehicle vehicle){
            boolean canMove = true;
            ArrayList<Node> successors = new ArrayList<>();
            for (Task t : tasksCarried) {
                if (t.deliveryCity.equals(agentPosition)) {
                    successors.add(new Node(agentPosition, this, Action.DELIVER, t, vehicle));
                    canMove = false;
                }
            }
            for (Task t : tasksAvailable) {
                if (t.pickupCity.equals(agentPosition)) {
                    successors.add(new Node(agentPosition, this, Action.PICKUP, t, vehicle));
                }
            }
            if (canMove) {
                for (City c : agentPosition.neighbors()) {
                    successors.add(new Node(c, this, Action.MOVE, vehicle));
                }
            }
            return successors;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;

            if (Double.compare(node.weightCarried, weightCarried) != 0) return false;
            if (agentPosition != null ? !agentPosition.equals(node.agentPosition) : node.agentPosition != null)
                return false;
            if (tasksCarried != null ? !tasksCarried.equals(node.tasksCarried) : node.tasksCarried != null)
                return false;
            return tasksAvailable != null ? tasksAvailable.equals(node.tasksAvailable) : node.tasksAvailable == null;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = agentPosition != null ? agentPosition.hashCode() : 0;
            temp = Double.doubleToLongBits(weightCarried);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (tasksCarried != null ? tasksCarried.hashCode() : 0);
            result = 31 * result + (tasksAvailable != null ? tasksAvailable.hashCode() : 0);
            return result;
        }
    }
}