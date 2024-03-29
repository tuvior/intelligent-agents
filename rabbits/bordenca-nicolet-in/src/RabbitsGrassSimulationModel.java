import uchicago.src.reflector.RangePropertyDescriptor;
import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.analysis.Sequence;
import uchicago.src.sim.analysis.plot.OpenGraph;
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
    private static int INITIAL_GRASS = (int) (GRID_HEIGHT * GRID_WIDTH * 0.1);
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
    private int initialGrass = INITIAL_GRASS;
    private int birthEnergy = BIRTH_ENERGY;
    private int grassEnergy = GRASS_ENERGY;

    // Graphs
    OpenSequenceGraph populationGraph;

    public static void main(String[] args) {
        SimInit init = new SimInit();
        RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
        init.loadModel(model, "", false);
    }

    public void setup() {
        rgsSpace = null;
        rabbits = new ArrayList<>();
        schedule = new Schedule(1);

        // Tear down and init display
        if (displaySurf != null) {
            displaySurf.dispose();
        }
        displaySurf = null;
        displaySurf = new DisplaySurface(this, "Rabbits Grass Simulation Window 1");

        // Tear down and init graphs
        if (populationGraph != null)
        {
            populationGraph.dispose();
        }
        populationGraph = null;
        populationGraph = new OpenSequenceGraph("Population graph", this);
        populationGraph.setXViewPolicy(OpenSequenceGraph.SHOW_LAST);
        populationGraph.setXRange(0, 2000);

        // Sliders
        descriptors.put("GrassGrowthRate", new RangePropertyDescriptor("GrassGrowthRate", 0, 50, 10));
        descriptors.put("GrassEnergy", new RangePropertyDescriptor("GrassEnergy", 0, 50, 10));
        descriptors.put("BirthThreshold", new RangePropertyDescriptor("BirthThreshold", 10, 80, 20));
        descriptors.put("BirthEnergy", new RangePropertyDescriptor("BirthEnergy", 10, 100, 20));

        // Register displays
        registerDisplaySurface("Rabbits Grass Simulation Window 1", displaySurf);
        registerMediaProducer("Population graph", populationGraph);

    }

    public void begin() {
        buildModel();
        buildSchedule();
        buildDisplay();

        // Display windows
        displaySurf.display();
        populationGraph.display();
    }

    /* -- Model -- */
    public void buildModel() {
        rgsSpace = new RabbitsGrassSimulationSpace(gridWidth, gridHeight, this);
        rgsSpace.placeGrass(initialGrass);

        for (int i = 0; i < numRabbits; i++) {
            addNewRabbit();
        }

        rabbits.forEach(RabbitsGrassSimulationAgent::report);
    }

    /* -- Display -- */
    public void buildDisplay() {
        // Create the grass display
        ColorMap map = new ColorMap();
        map.mapColor(0, Color.BLACK);
        map.mapColor(1, Color.GREEN);

        Value2DDisplay displayGrass =
                new Value2DDisplay(rgsSpace.getCurrentGrassField(), map);

        // Create the rabbit display
        Object2DDisplay displayAgents = new Object2DDisplay(rgsSpace.getCurrentRabbitSpace());
        displayAgents.setObjectList(rabbits);

        // Add displays to surface
        displaySurf.addDisplayable(displayGrass, "Grass");
        displaySurf.addDisplayable(displayAgents, "Rabbits");

        // Add sequences to graphs
        populationGraph.addSequence("Number of rabbits", new RabbitsNumber(), OpenGraph.CIRCLE);
        populationGraph.addSequence("Number of grass cells", new GrassNumber(), OpenGraph.CIRCLE);
    }

    /* -- Schedule -- */
    public void buildSchedule() {
        schedule.scheduleActionBeginning(0, new SimulationStepAction());
        schedule.scheduleActionAtInterval(10, new UpdateGraphAction());
    }

    class SimulationStepAction extends BasicAction {
        public void execute() {
            SimUtilities.shuffle(rabbits);
            rabbits.forEach(RabbitsGrassSimulationAgent::step);
            reapDeadRabbits();
            birthRabbits();
            rgsSpace.placeGrass(grassGrowthRate);
            displaySurf.updateDisplay();
        }
    }

    class UpdateGraphAction extends BasicAction {
        public void execute() {
            populationGraph.step();
        }
    }



    /* -- Helpers -- */
    public int countLivingRabbits(){
        return (int) rabbits.stream().filter(r -> r.getEnergy() > 0).count();
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

    private void addNewRabbit() {
        RabbitsGrassSimulationAgent rabbit = new RabbitsGrassSimulationAgent(birthEnergy, this);
        if (rgsSpace.addRabbit(rabbit)) {
            rabbits.add(rabbit);
        }
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

    /* -- Graphs -- */
    class RabbitsNumber implements DataSource, Sequence {

        public Object execute() {
            return getSValue();
        }

        public double getSValue() {
            return (double)countLivingRabbits();
        }
    }

    class GrassNumber implements DataSource, Sequence {

        public Object execute() {
            return getSValue();
        }

        public double getSValue() {
            return (double)rgsSpace.countGrass();
        }
    }


    /* -- Getters - Setters */
    public String[] getInitParam() {
        return new String[]{"NumRabbits", "BirthThreshold", "GrassGrowthRate", "GridWidth", "GridHeight", "InitialGrass", "BirthEnergy", "GrassEnergy"};
    }

    public String getName() {
        return "RabbitsGrassSimulation";
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public int getGrassEnergy() {
        return grassEnergy;
    }

    public void setGrassEnergy(int grassEnergy) {
        this.grassEnergy = grassEnergy;
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

    public int getInitialGrass() {
        return initialGrass;
    }

    public void setInitialGrass(int initialGrass) {
        this.initialGrass = initialGrass;
    }

    public int getBirthEnergy() {
        return birthEnergy;
    }

    public void setBirthEnergy(int birthEnergy) {
        this.birthEnergy = birthEnergy;
    }
}
