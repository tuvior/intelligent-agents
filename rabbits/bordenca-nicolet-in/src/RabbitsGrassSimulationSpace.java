import uchicago.src.sim.space.Object2DGrid;

import java.awt.*;

/**
 * Class that implements the simulation space of the rabbits grass simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationSpace {

    private RabbitsGrassSimulationModel rgsModel;
    private Object2DGrid grassField;
    private Object2DGrid rabbitSpace;

    public RabbitsGrassSimulationSpace(int xSize, int ySize, RabbitsGrassSimulationModel model) {
        grassField = new Object2DGrid(xSize, ySize);
        rabbitSpace = new Object2DGrid(xSize, ySize);
        rgsModel = model;
        for (int i = 0; i < xSize; i++) {
            for (int j = 0; j < ySize; j++) {
                grassField.putObjectAt(i, j, 0);
            }
        }
    }

    public boolean addRabbit(RabbitsGrassSimulationAgent rabbit) {
        if (countEmptySpace() < 1) return false;

        while (true) {
            // choose coordinates
            int x = (int) (Math.random() * (rabbitSpace.getSizeX()));
            int y = (int) (Math.random() * (rabbitSpace.getSizeY()));

            // place rabbit if empty cell
            if (isCellEmpty(x, y)) {
                rabbitSpace.putObjectAt(x, y, rabbit);
                rabbit.setXY(x, y);
                rabbit.setRgsSpace(this);
                return true;
            }

        }
    }

    public void removeRabbitAt(int x, int y){
        rabbitSpace.putObjectAt(x, y, null);
    }
    
    public boolean moveRabbitAt(int x, int y, int newX, int newY){
        if(!isRabbit(newX, newY)){
            RabbitsGrassSimulationAgent rabbit = (RabbitsGrassSimulationAgent) rabbitSpace.getObjectAt(x, y);
            removeRabbitAt(x,y);
            rabbit.setXY(newX, newY);
            rabbitSpace.putObjectAt(newX, newY, rabbit);
            return true;
        }
        return false;
    }

    public void placeGrass(int grass) {

        // reduce amount of grass placed if field is full
        grass = Math.min(grass, countEmptySpace());

        // randomly place grass in field
        int placed = 0;
        while (placed < grass) {
            // choose coordinates
            int x = (int) (Math.random() * (grassField.getSizeX()));
            int y = (int) (Math.random() * (grassField.getSizeY()));

            // set grass if no grass nor rabbit there
            if (isCellEmpty(x, y)) {
                grassField.putObjectAt(x, y, 1);
                placed++;
            }

        }
    }

    public boolean tryTakeGrass(int x, int y) {
        if (isGrass(x, y)) {
            grassField.putObjectAt(x, y, 0);
            return true;
        }
        return false;
    }

    public int countGrass() {
        int grass = 0;
        for (int i = 0; i < grassField.getSizeX(); i++) {
            for (int j = 0; j < grassField.getSizeY(); j++) {
                if (isGrass(i, j)) {
                    grass++;
                }
            }
        }

        return grass;
    }

    public int countEmptySpace() {
        int empty = 0;

        for (int i = 0; i < grassField.getSizeX(); i++) {
            for (int j = 0; j < grassField.getSizeY(); j++) {
                if (isCellEmpty(i, j)) {
                    empty++;
                }
            }
        }

        return empty;
    }

    /* -- Helpers -- */
    private boolean isCellEmpty(int x, int y) {
        return !isRabbit(x, y) && !isGrass(x, y);
    }

    private boolean isRabbit(int x, int y) {
        return rabbitSpace.getObjectAt(x, y) != null;
    }

    /* -- Getters - Setters -- */
    public boolean isGrass(int x, int y) {
        return grassField.getObjectAt(x, y).equals(1);
    }

    public Dimension getRabbitSpaceSize() {
        return rabbitSpace.getSize();
    }

    public Object2DGrid getCurrentGrassField() {
        return grassField;
    }

    public Object2DGrid getCurrentRabbitSpace() {
        return rabbitSpace;
    }
}
