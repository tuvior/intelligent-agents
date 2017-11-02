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
import logist.topology.Topology.City;

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
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");

        return null;
    }

    private List<Plan> stochasticLocalSearch(List<Vehicle> vehicles, TaskSet tasks) {
        HashMap<Vehicle, ConcreteTask> taskLists = new HashMap<>();

        vehicles.forEach(v -> taskLists.put(v, null));

        int taskPerVehicle = (int) Math.ceil(tasks.size() / vehicles.size());
        Iterator<Task> taskIterator = tasks.iterator();

        /*vehicles.forEach(v -> {
            for (int i = 0; i < taskPerVehicle && taskIterator.hasNext(); i++) {
                Task task = taskIterator.next();
                ConcreteTask pickup = ConcreteTask.pickup(task);
                ConcreteTask deliver = ConcreteTask.delivery(task);
                pickup.complement = deliver;
                deliver.complement = pickup;
                pickup.next = deliver;
                deliver.prev = pickup;

                if (taskLists.get(v) == null) {
                    pickup.time = 1;
                    deliver.time = 1;
                    taskLists.put(v, pickup);
                } else {
                    ConcreteTask lastTask = taskLists.get(v);
                    while (lastTask.next != null) lastTask = lastTask.next;
                    lastTask.next = pickup;
                    pickup.time = lastTask.time + 1;
                    deliver.time = pickup.time + 1;
                }
            }
        });
*/





        return null;
    }

    public static class State {
        public HashMap<Vehicle, ConcreteTask> firstTasks;
        public HashMap<ConcreteTask, ConcreteTask> nextTask;
        public HashMap<ConcreteTask, Integer> time;
        public HashMap<ConcreteTask, Vehicle> vehicle;

        public State(List<Vehicle> vehicles, TaskSet tasks) {
            firstTasks = new HashMap<>();
            nextTask = new HashMap<>();
            time = new HashMap<>();
            vehicle = new HashMap<>();
        }
    }

    public static class ConcreteTask {
        public static enum Action {PICKUP, DELIVERY}

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
    }

    /**
     * Constraints checker.
     *
     * Note that not all the constraints need to be manually checked,
     * since the neighbors generation take into account the obvious
     * contraints as:
     *      * Time constraints
     *      * Vehicle constraints
     *      * Order constraints
     *      * All tasks delivered constraint
     *
     * Then only remains the weight constraint.
     */
    private static class Constraints {
        public boolean checkConstraints(State state) {
            return checkWeight(state);
        }

        private boolean checkWeight(State state) {
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