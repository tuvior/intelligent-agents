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

import java.util.*;

/**
 * An optimal planner for one vehicle.
 */
public class DeliberativeAgent implements DeliberativeBehavior {

    enum Algorithm {BFS, ASTAR}

    enum Heuristic {MAXCOST, NONE}

    enum Action {MOVE, PICKUP, DELIVER}

    /* Environment */
    Topology topology;
    TaskDistribution td;

    /* the properties of the agent */
    private Agent agent;

    /* the planning class */
    Algorithm algorithm;

    /* A* Heuristic */
    Heuristic heuristic;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {
        this.topology = topology;
        this.td = td;
        this.agent = agent;

        // initialize the planner
        String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
        String heuristicName = agent.readProperty("heuristic", String.class, "NONE");

        // Throws IllegalArgumentException if algorithm is unknown
        algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
        heuristic = Heuristic.valueOf(heuristicName.toUpperCase());

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
        Node root = new Node(vehicle.getCurrentCity(), null, null, vehicle);
        root.tasksAvailable = new HashSet<>(tasks);
        root.tasksCarried = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        HashSet<Node> c = new HashSet<>();
        Node bestGoal = null;
        queue.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.remove();

            if (c.contains(current)) continue;

            if (current.isGoal()) {
                if (bestGoal == null || current.cost < bestGoal.cost) {
                    bestGoal = current;
                }
            } else {
                for (Node succ : current.getSuccessors()) {
                    if (bestGoal == null || succ.cost < bestGoal.cost) {
                        queue.add(succ);
                    }
                }
            }

            c.add(current);
        }

        if (bestGoal != null) {
            return generatePlanFromGraph(bestGoal, root);
        }

        System.out.println("???");
        return null;
    }

    private Plan planASTAR(Vehicle vehicle, TaskSet tasks) {
        Node root = new Node(vehicle.getCurrentCity(), null, null, vehicle);
        root.tasksAvailable = new HashSet<>(tasks);
        root.tasksCarried = new HashSet<>();
        Comparator<Node> f;
        switch (heuristic) {
            case MAXCOST:
                f = Comparator.comparingDouble(n -> n.cost + n.getMaximumFutureCost());
                break;
            case NONE:
            default:
                f = Comparator.comparingDouble(n -> n.cost);
                break;

        }
        PriorityQueue<Node> queue = new PriorityQueue<>(10, f);
        HashSet<Node> c = new HashSet<>();
        Node goal = null;
        queue.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.remove();

            if (c.contains(current)) continue;

            if (current.isGoal()) {
                goal = current;
                break;
            } else {
                queue.addAll(current.getSuccessors());
            }

            c.add(current);
        }

        if (goal != null) {
            return generatePlanFromGraph(goal, root);
        }

        System.out.println("???");
        return null;
    }

    Plan generatePlanFromGraph(Node goal, Node root) {
        Plan plan = new Plan(root.agentPosition);
        Node curr = goal;

        while (!curr.equals(root)) {
            curr.parent.next = curr;
            curr = curr.parent;
        }

        while (!curr.equals(goal)) {
            curr = curr.next;
            switch (curr.parentAction) {
                case MOVE:
                    plan.appendMove(curr.agentPosition);
                    break;
                case PICKUP:
                    plan.appendPickup(curr.processedTask);
                    break;
                case DELIVER:
                    plan.appendDelivery(curr.processedTask);
                    break;
            }
        }

        System.out.println(plan);
        return plan;
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
        public Vehicle vehicle;

        public Set<Task> tasksCarried;
        public Set<Task> tasksAvailable;
        public Task processedTask;

        public Node parent;
        public Node next;

        public Node(City positon, Node parent, Action action, Vehicle vehicle) {
            this(positon, parent, action, null, vehicle);
        }

        public Node(City positon, Node parent, Action action, Task toProcess, Vehicle vehicle) {
            agentPosition = positon;
            this.parent = parent;
            parentAction = action;
            processedTask = toProcess;
            this.vehicle = vehicle;
            if (parent == null) {
                cost = 0;
                level = 0;
            } else {
                cost = parent.cost;
                level = parent.level++;
                tasksAvailable = new HashSet<>(parent.tasksAvailable);
                tasksCarried = new HashSet<>(parent.tasksCarried);

                switch (action) {
                    case MOVE:
                        cost += parent.agentPosition.distanceTo(agentPosition) * vehicle.costPerKm();
                        break;
                    case PICKUP:
                        tasksAvailable.remove(toProcess);
                        tasksCarried.add(toProcess);
                        weightCarried += toProcess.weight;
                        break;
                    case DELIVER:
                        tasksCarried.remove(toProcess);
                        weightCarried -= toProcess.weight;
                        break;

                }
            }
        }

        public List<Node> getSuccessors() {
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
                    if (weightCarried + t.weight <= vehicle.capacity()) {
                        successors.add(new Node(agentPosition, this, Action.PICKUP, t, vehicle));
                    }
                }
            }
            if (canMove) {
                for (City c : agentPosition.neighbors()) {
                    successors.add(new Node(c, this, Action.MOVE, vehicle));
                }
            }
            return successors;
        }

        public boolean isGoal() {
            return tasksAvailable.isEmpty() && tasksCarried.isEmpty();
        }

        public double getMaximumFutureCost() {
            double cost = 0;

            for (Task t : tasksCarried) {
                if (agentPosition.distanceTo(t.deliveryCity) * vehicle.costPerKm() > cost) {
                    cost = agentPosition.distanceTo(t.deliveryCity) * vehicle.costPerKm();
                }
            }

            for (Task t : tasksAvailable) {
                if ((agentPosition.distanceTo(t.pickupCity) + t.pathLength()) * vehicle.costPerKm() > cost) {
                    cost = (agentPosition.distanceTo(t.pickupCity) + t.pathLength()) * vehicle.costPerKm();
                }
            }

            return cost;
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
