import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.util.SimUtilities;

import java.awt.*;
import java.util.ArrayList;

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
    private static int NUM_RABBITS = 20;
    private static int BIRTH_THRESHOLD = 80;
    private static int GRASS_GROWTH_RATE = 2;
    private static int TOTAL_GRASS = (int) (GRID_HEIGHT * GRID_WIDTH * 0.1);
    private static int BIRTH_ENERGY = 40;
    private static int GRASS_ENERGY = 10;

    private RabbitsGrassSimulationSpace rgsSpace;
    private DisplaySurface displaySurf;
    private ArrayList<RabbitsGrassSimulationAgent> rabbits;

    private Schedule schedule;
    private int numRabbits = NUM_RABBITS;
    private int birthThreshold = BIRTH_THRESHOLD;
    private int grassGrowthRate = GRASS_GROWTH_RATE;
    private int gridHeight = GRID_HEIGHT;
    private int gridWidth = GRID_WIDTH;
    private int grass = TOTAL_GRASS;
    private int birthEnergy = BIRTH_ENERGY;
    private int grassEnergy = GRASS_ENERGY;

    public static void main(String[] args) {
        SimInit init = new SimInit();
        RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
        init.loadModel(model, "", false);
    }

    public void begin() {
        buildModel();
        buildSchedule();
        buildDisplay();

        displaySurf.display();
    }

    public void buildModel() {
        rgsSpace = new RabbitsGrassSimulationSpace(gridWidth, gridHeight);
        rgsSpace.placeGrass(grass);

        for (int i = 0; i < numRabbits; i++) {
            addNewRabbit();
        }

        rabbits.forEach(RabbitsGrassSimulationAgent::report);
    }

    public int getGrassEnergy() {
        return grassEnergy;
    }

    public void setGrassEnergy(int grassEnergy) {
        this.grassEnergy = grassEnergy;
    }


    class RabbitsGrassSimulationStep extends BasicAction {
        public void execute() {
            SimUtilities.shuffle(rabbits);
            rabbits.forEach(RabbitsGrassSimulationAgent::step);
            reapDeadRabbits();
            birthRabbits();
            rgsSpace.placeGrass(grassGrowthRate);
            displaySurf.updateDisplay();
        }
    }

    class RabbitsGrassSimulationCountLiving extends BasicAction {
        public void execute(){
            countLivingRabbits();
        }
    }

    public void buildSchedule() {
        schedule.scheduleActionBeginning(0, new RabbitsGrassSimulationStep());
        schedule.scheduleActionAtInterval(10, new RabbitsGrassSimulationCountLiving());
    }

    private int countLivingRabbits(){
        return (int) rabbits.stream().filter(r -> r.getEnergy() > 0).count();
    }

    public void buildDisplay() {
        ColorMap map = new ColorMap();

        map.mapColor(0, Color.BLACK);
        map.mapColor(1, Color.GREEN);

        Value2DDisplay displayGrass =
                new Value2DDisplay(rgsSpace.getCurrentGrassField(), map);

        Object2DDisplay displayAgents = new Object2DDisplay(rgsSpace.getCurrentRabbitSpace());
        displayAgents.setObjectList(rabbits);

        displaySurf.addDisplayable(displayGrass, "Grass");
        displaySurf.addDisplayable(displayAgents, "Rabbits");
    }

    public String[] getInitParam() {
        return new String[]{"NumRabbits", "BirthThreshold", "GrassGrowthRate", "GridWidth", "GridHeight", "Grass", "BirthEnergy", "GrassEnergy"};
    }

    public String getName() {
        return "RabbitsGrassSimulation";
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setup() {
        rgsSpace = null;
        rabbits = new ArrayList<>();
        schedule = new Schedule(20);

        if (displaySurf != null) {
            displaySurf.dispose();
        }
        displaySurf = null;

        displaySurf = new DisplaySurface(this, "Rabbits Grass Simulation Window 1");

        registerDisplaySurface("Rabbits Grass Simulation Window 1", displaySurf);
    }

    private void addNewRabbit() {
        RabbitsGrassSimulationAgent rabbit = new RabbitsGrassSimulationAgent(birthEnergy);
        rabbit.setRgsModel(this);
        rabbits.add(rabbit);
        rgsSpace.addRabbit(rabbit);
    }


    private void reapDeadRabbits(){
        int count = 0;
        for(int i = (rabbits.size() - 1); i >= 0 ; i--){
            RabbitsGrassSimulationAgent rabbit = rabbits.get(i);
            if(rabbit.getEnergy() < 1){
                rgsSpace.removeRabbitAt(rabbit.getX(), rabbit.getY());
                rabbits.remove(i);
                count++;
            }
        }
        if (count > 0)
            System.out.println(count + " rabbits died.");
    }

    private void birthRabbits() {
        final int[] count = {0};
        rabbits.forEach(rabbit -> {
            if (rabbit.getEnergy() > birthThreshold) {
                count[0]++;
                rabbit.birthFatigue();
            }
        });

        for (int i = 1; i <= count[0]; i++) {
            addNewRabbit();
        }

        if (count[0] > 0)
            System.out.println(count[0] + " rabbits were born");
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

    public int getGrass() {
        return grass;
    }

    public void setGrass(int grass) {
        this.grass = grass;
    }

    public int getBirthEnergy() {
        return birthEnergy;
    }

    public void setBirthEnergy(int birthEnergy) {
        this.birthEnergy = birthEnergy;
    }
}
