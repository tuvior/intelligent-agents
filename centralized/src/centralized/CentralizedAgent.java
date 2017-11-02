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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
    private  double threshold;

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

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");

        return null;
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


        return null;
    }

    private State localChoice(List<State> neighbours) {

        return null;
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

                    if (firstTasks.containsKey(v)) {
                        time.put(pickup, 1);
                        time.put(deliver, 1);
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

        public List<State> chooseNeighbours() {
            return null;
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
}
