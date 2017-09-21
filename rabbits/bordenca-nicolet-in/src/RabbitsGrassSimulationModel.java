import uchicago.src.sim.engine.Schedule;
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

    private Schedule schedule;
    private int numRabbits;
    private int birthThreshold;
    private int grassGrowthRate;
    private int gridHeight;
    private int gridWidth;

    public static void main(String[] args) {
        System.out.println("Rabbit skeleton");
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
