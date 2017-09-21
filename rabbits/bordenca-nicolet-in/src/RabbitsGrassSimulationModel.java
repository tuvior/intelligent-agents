import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.engine.SimModelImpl;

/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @author
 */


public class RabbitsGrassSimulationModel extends SimModelImpl {

    // Default values
    private static int GRID_WIDTH = 20;
    private static int GRID_HEIGHT = 20;
    private static int NUM_RABBITS = 10;
    private static int BIRTH_THRESHOLD = 5;
    private static int GRASS_GROWTH_RATE = 10;

    private Schedule schedule;
    private int numRabbits = NUM_RABBITS;
    private int birthThreshold = BIRTH_THRESHOLD;
    private int grassGrowthRate = GRASS_GROWTH_RATE;
    private int gridHeight = GRID_HEIGHT;
    private int gridWidth = GRID_WIDTH;

    public static void main(String[] args) {
        SimInit init = new SimInit();
        RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
        init.loadModel(model, "", false);
    }

    public void begin() {
        buildModel();
        buildSchedule();
        buildDisplay();
    }

    public void buildModel() {
    }

    public void buildSchedule() {
    }

    public void buildDisplay() {
    }

    public String[] getInitParam() {
        return new String[]{"NumRabbits", "BirthThreshold", "GrassGrowthRate"};
    }

    public String getName() {
        return "RabbitsGrassSimulation";
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setup() {
        // TODO Auto-generated method stub
    }

    public int getNumRabbits() {
        return numRabbits;
    }

    public void setNumRabbits(int numRabbits) {
        this.numRabbits = numRabbits;
    }

    public int getBirthThreshold() {
        return birthThreshold;
    }

    public void setBirthThreshold(int birthThreshold) {
        this.birthThreshold = birthThreshold;
    }

    public int getGrassGrowthRate() {
        return grassGrowthRate;
    }

    public void setGrassGrowthRate(int grassGrowthRate) {
        this.grassGrowthRate = grassGrowthRate;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public void setGridHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public void setGridWidth(int gridWidth) {
        this.gridWidth = gridWidth;
    }
}
