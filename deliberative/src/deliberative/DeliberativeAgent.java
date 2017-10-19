package deliberative;

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

    // the planning class
    private Algorithm algorithm;

    // A* heuristic
    private Heuristic heuristic;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {
        // initialize the planner
        String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
        String heuristicName = agent.readProperty("heuristic", String.class, "NONE");

        // throws IllegalArgumentException if algorithm or heuristic are unknown
        algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
        heuristic = Heuristic.valueOf(heuristicName.toUpperCase());

    }

    @Override
    public Plan plan(Vehicle vehicle, TaskSet tasks) {
        // compute the plan with the selected algorithm.
        switch (algorithm) {
            case ASTAR:
                return planASTAR(vehicle, tasks);
            case BFS:
                return planBFS(vehicle, tasks);
            default:
                throw new AssertionError("Should not happen.");
        }
    }

    @Override
    public void planCancelled(TaskSet taskSet) {
        // carried tasks are handled in DeliberativeAgent#plan
    }

    private Plan planBFS(Vehicle vehicle, TaskSet tasks) {
        long start = System.currentTimeMillis();
        Node root = Node.makeRoot(vehicle, tasks);
        Queue<Node> queue = new LinkedList<>();
        HashSet<Node> c = new HashSet<>();
        Node bestGoal = null;
        queue.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.remove();

            if (c.contains(current)) continue;

            if (current.isGoal()) {
                // retain the best goal state currently found
                if (bestGoal == null || current.cost < bestGoal.cost) {
                    bestGoal = current;
                }
            } else {
                if (bestGoal == null || current.cost < bestGoal.cost) {
                    for (Node succ : current.getSuccessors()) {
                        // only add successors that would still cost lest than our best solution so far
                        if (bestGoal == null || succ.cost < bestGoal.cost) {
                            queue.add(succ);
                        }
                    }
                }

                c.add(current);
            }
        }

        long end = System.currentTimeMillis();

        System.out.println("Time: " + (end - start) / 1000 );

        if (bestGoal != null) {
            return generatePlanFromGraph(bestGoal, root);
        }

        return null;
    }

    private Plan planASTAR(Vehicle vehicle, TaskSet tasks) {
        long start = System.currentTimeMillis();
        Node root = Node.makeRoot(vehicle, tasks);
        Comparator<Node> f;
        switch (heuristic) {
            case MAXCOST:
                f = Comparator.comparingDouble(n -> n.cost + n.getMaximumFutureCost());
                break;
            case NONE:
                f = Comparator.comparingDouble(n -> n.cost);
                break;
            default:
                throw new AssertionError("Should not happen.");

        }
        // this queue keeps itself sorted on the given f
        PriorityQueue<Node> queue = new PriorityQueue<>(10, f);
        HashSet<Node> c = new HashSet<>();
        Node goal = null;
        queue.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.remove();

            if (c.contains(current)) continue;

            if (current.isGoal()) {
                // since queue is sorted first goal found has to be the optimal solution
                goal = current;
                break;
            } else {
                queue.addAll(current.getSuccessors());
                c.add(current);
            }
        }


        long end = System.currentTimeMillis();

        System.out.println("Time: " + (end - start) / 1000 );

        if (goal != null) {
            return generatePlanFromGraph(goal, root);
        }

        System.out.println("???");
        return null;
    }

    private Plan generatePlanFromGraph(Node goal, Node root) {
        Plan plan = new Plan(root.agentPosition);
        Node curr = goal;

        // we go back up to the root while leaving breadcrumbs to be able to recreate the optimal solution
        while (curr != root) {
            curr.parent.next = curr;
            curr = curr.parent;
        }

        while (curr != goal) {
            curr = curr.next;
            switch (curr.generatingAction) {
                case MOVE:
                    curr.parent.agentPosition.pathTo(curr.agentPosition).forEach(plan::appendMove);
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

    public static class Node {
        public City agentPosition;
        public Action generatingAction;
        public double weightCarried;
        public double cost;
        public Vehicle vehicle;

        public Set<Task> tasksCarried;
        public Set<Task> tasksAvailable;
        public Task processedTask;

        public Node parent;
        public Node next;

        // generates a root for the search tree
        public static Node makeRoot(Vehicle vehicle, TaskSet taskSet) {
            return new Node(vehicle.getCurrentCity(), vehicle, taskSet);
        }

        private Node(City position, Vehicle vehicle, TaskSet ts) {
            this.agentPosition = position;
            this.cost = 0;
            this.vehicle = vehicle;
            this.tasksAvailable = new HashSet<>(ts);

            // these two are useful only with more agents running, since a replan can have carried tasks
            this.tasksCarried = new HashSet<>(vehicle.getCurrentTasks());
            this.weightCarried = tasksCarried.stream().mapToDouble(t -> t.weight).sum();
        }

        private Node(City position, Node parent, Action action, Vehicle vehicle) {
            this(position, parent, action, null, vehicle);
        }

        private Node(City position, Node parent, Action action, Task toProcess, Vehicle vehicle) {
            this.agentPosition = position;
            this.parent = parent;
            this.generatingAction = action;
            this.processedTask = toProcess;
            this.vehicle = vehicle;
            this.cost = parent.cost;
            this.tasksAvailable = new HashSet<>(parent.tasksAvailable);
            this.tasksCarried = new HashSet<>(parent.tasksCarried);

            // state updates are inferred from the action that lead to it
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

        public List<Node> getSuccessors() {
            // we generate moves from this state only when no task could be delivered at current position
            boolean canMove = tasksCarried.stream().noneMatch(t -> t.deliveryCity.equals(agentPosition));
            ArrayList<Node> successors = new ArrayList<>();

            for (Task t : tasksCarried) {
                if (t.deliveryCity.equals(agentPosition)) {
                    successors.add(new Node(agentPosition, this, Action.DELIVER, t, vehicle));
                } else if (canMove) {
                    successors.add(new Node(t.deliveryCity, this, Action.MOVE, vehicle));
                }
            }

            for (Task t : tasksAvailable) {
                if (t.pickupCity.equals(agentPosition)) {
                    if (weightCarried + t.weight <= vehicle.capacity()) {
                        successors.add(new Node(agentPosition, this, Action.PICKUP, t, vehicle));
                    }
                } else if (canMove) {
                    successors.add(new Node(t.pickupCity, this, Action.MOVE, vehicle));
                }
            }

            return successors;
        }

        public boolean isGoal() {
            return tasksAvailable.isEmpty() && tasksCarried.isEmpty();
        }

        // returns the most costly trip that could be performed to act out any delivery
        public double getMaximumFutureCost() {
            double deliverCost = tasksCarried.stream().mapToDouble(t -> agentPosition.distanceTo(t.deliveryCity) * vehicle.costPerKm()).max().orElse(0);
            double pickupCost = tasksAvailable.stream().mapToDouble(t -> (agentPosition.distanceTo(t.pickupCity) + t.pathLength()) * vehicle.costPerKm()).max().orElse(0);

            return Math.max(deliverCost, pickupCost);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof Node)) return false;

            Node node = (Node) o;

            // this allows less costly nodes to not be recognized as cycles when testing with HashSet#contains
            if (cost < node.cost) return false;
            if (Double.compare(node.weightCarried, weightCarried) != 0) return false;
            if (!agentPosition.equals(node.agentPosition)) return false;
            if (!tasksCarried.equals(node.tasksCarried)) return false;
            return tasksAvailable.equals(node.tasksAvailable);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = agentPosition.hashCode();
            temp = Double.doubleToLongBits(weightCarried);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + tasksCarried.hashCode();
            result = 31 * result + tasksAvailable.hashCode();
            return result;
        }
    }
}
