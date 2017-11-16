package auction

import logist.LogistSettings
import logist.agent.Agent
import logist.behavior.AuctionBehavior
import logist.config.Parsers
import logist.plan.Plan
import logist.simulation.Vehicle
import logist.task.Task
import logist.task.TaskDistribution
import logist.task.TaskSet
import logist.topology.Topology
import logist.topology.Topology.City

import java.util.*

class AuctionAgent : AuctionBehavior {

    private var topology: Topology? = null
    private var distribution: TaskDistribution? = null
    private var agent: Agent? = null
    private var random: Random? = null
    private var vehicle: Vehicle? = null
    private var timeout_setup: Long = 0
    private var timeout_plan: Long = 0
    private var timeout_bid: Long = 0

    private var adversary: Adversary? = null
    private var tasks: MutableList<Task>? = null
    private var payment: Long = 0

    /**
     * ASSUMPTIONS:
     * - constant price per km
     * - always 2 agents
     */

    override fun setup(topology: Topology, distribution: TaskDistribution, agent: Agent) {

        try {
            val ls = Parsers.parseSettings("config/settings_default.xml")
            timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP)
            timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN)
            timeout_bid = ls.get(LogistSettings.TimeoutKey.BID)
        } catch (ex: Exception) {
            System.err.println("There was a problem loading the configuration file.")
        }

        this.topology = topology
        this.distribution = distribution
        this.agent = agent
        this.vehicle = agent.vehicles()[0]
        this.adversary = Adversary()
        this.tasks = ArrayList()
        this.payment = 0

        this.random = Random()
    }

    override fun auctionResult(previous: Task, winner: Int, bids: Array<Long>) {
        val win = winner == agent!!.id()
        if (win) {
            tasks!!.add(previous)
            payment += bids[agent!!.id()]
        }

        when (agent!!.id()) {
            0 -> adversary!!.auctionResult(previous, bids[1], !win)
            1 -> adversary!!.auctionResult(previous, bids[0], !win)
        }
    }

    override fun askPrice(task: Task): Long? {
        // TODO: undercut adversary but don't bid too much in case of bad resulting plan or loss of profit
        return 0L
    }

    override fun plan(vehicles: List<Vehicle>, tasks: TaskSet): List<Plan>? {
        // TODO: stochastic local search again
        return null
    }

    /**
     * The method generates an optimal plan with all assigned tasks and addition of newTask
     * The current total payment received by the agent is then compared against the cost of this new optimal plan,
     * the difference between the two gives a lower bound in terms of bid required to generate profit if the auction
     * was won.
     *
     * @param newTask auctioned task to be evaluated
     * @return the minimum bid to be even in terms of revenue
     */
    private fun planCostRequirement(newTask: Task): Long {
        // TODO: stochastic local search I guess, then (plan cost - payment) is the result
        return 0
    }

    /**
     * TODO: figure out if we can find something meaningful to optimise undercutting prices without losses
     *
     * @param topology     the topology for this simulation
     * @param distribution the task distribution for this simulation
     */
    private fun evaluateTopology(topology: Topology, distribution: TaskDistribution) {
        topology.cities().forEach { city ->
            val totalProb1 = doubleArrayOf(0.0)
            val totalProb2 = doubleArrayOf(0.0)
            topology.cities().forEach { city2 ->
                totalProb1[0] += distribution.probability(city, city2)
                totalProb2[0] += distribution.probability(city2, city)
            }
        }
    }

    /**
     * class to represent everything related to the adversary
     */
    inner class Adversary {
        var tasks: MutableList<Task>
        var bids: HashMap<Task, Long>

        init {
            tasks = ArrayList()
            bids = HashMap()
        }

        fun auctionResult(task: Task, bid: Long, winner: Boolean) {
            if (winner) tasks.add(task)
            bids.put(task, bid)
        }

        /**
         * The method tries to estimate the best case scenario for the adversary adding this task to their plan
         * it picks a city that already needs to be visited as bridge for the pickup and then assumes no
         * direct delivery will be performed. Without reusing the bridge city (as it would imply backtracking)
         * it looks for another city that will need to be visited and calculates the cost of delivery to only
         * start from there, meaning that the rest of the path will be part of the already existing plan.
         * In case of first task it assumes the distance to pickup as average distance from the pickup city
         *
         * @param newTask the task being auctioned
         * @return a lower bound for the adversary's cost
         */
        fun getMinCostForNewTask(newTask: Task): Double {
            var pickupGap = java.lang.Double.POSITIVE_INFINITY
            val deliveryGap = doubleArrayOf(newTask.pickupCity.distanceTo(newTask.deliveryCity))

            if (tasks.size == 0) {
                return (averageDistance(newTask.pickupCity) + deliveryGap[0]) * COST_KM
            }

            val bridgeCities = HashSet<City>()

            for (t in tasks) {
                val pickupDist = t.pickupCity.distanceTo(newTask.pickupCity)
                if (pickupDist < pickupGap) {
                    pickupGap = pickupDist
                    bridgeCities.clear()
                    bridgeCities.add(t.pickupCity)
                } else if (pickupDist == pickupGap) {
                    bridgeCities.add(t.pickupCity)
                }
                val deliveryDist = t.deliveryCity.distanceTo(newTask.pickupCity)
                if (deliveryDist < pickupGap) {
                    pickupGap = deliveryDist
                    bridgeCities.clear()
                    bridgeCities.add(t.deliveryCity)
                } else if (deliveryDist == pickupGap) {
                    bridgeCities.add(t.deliveryCity)
                }
            }

            bridgeCities.forEach { bridgeCity ->
                for (t in tasks) {
                    if (t.pickupCity != bridgeCity && t.pickupCity.distanceTo(newTask.pickupCity) < deliveryGap[0]) {
                        deliveryGap[0] = t.pickupCity.distanceTo(newTask.pickupCity)
                    }
                    if (t.deliveryCity != bridgeCity && t.deliveryCity.distanceTo(newTask.pickupCity) < deliveryGap[0]) {
                        deliveryGap[0] = t.deliveryCity.distanceTo(newTask.pickupCity)
                    }
                }
            }

            return (deliveryGap[0] + pickupGap) * COST_KM
        }

        private fun averageDistance(city: City): Double {
            return topology!!.cities().stream().mapToDouble(ToDoubleFunction<City> { city.distanceTo(it) }).sum() / topology!!.cities().size
        }
    }

    companion object {

        private val COST_KM = 5
    }
}
