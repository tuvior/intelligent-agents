import uchicago.src.sim.space.Object2DGrid;

import java.awt.*;

/**
 * Class that implements the simulation space of the rabbits grass simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationSpace {

    private Object2DGrid grassField;
    private Object2DGrid rabbitSpace;

    public RabbitsGrassSimulationSpace(int xSize, int ySize) {
        grassField = new Object2DGrid(xSize, ySize);
        rabbitSpace = new Object2DGrid(xSize, ySize);
        for (int i = 0; i < xSize; i++) {
            for (int j = 0; j < ySize; j++) {
                grassField.putObjectAt(i, j, 0);
            }
        }
    }

    public boolean addRabbit(RabbitsGrassSimulationAgent rabbit) {
        int count = 0;
        int countLimit = 10 * rabbitSpace.getSizeX() * rabbitSpace.getSizeY();

        while (count < countLimit) {
            int x = (int) (Math.random() * (rabbitSpace.getSizeX()));
            int y = (int) (Math.random() * (rabbitSpace.getSizeY()));
            if (!isCellOccupied(x, y)) {
                rabbitSpace.putObjectAt(x, y, rabbit);
                rabbit.setXY(x, y);
                rabbit.setRgsSpace(this);
                return true;
            }
            count++;
        }

        return false;
    }
    
    public boolean moveRabbitAt(int x, int y, int newX, int newY){
        if(!isCellOccupied(newX, newY)){
            RabbitsGrassSimulationAgent rabbit = (RabbitsGrassSimulationAgent) rabbitSpace.getObjectAt(x, y);
            removeRabbitAt(x,y);
            rabbit.setXY(newX, newY);
            rabbitSpace.putObjectAt(newX, newY, rabbit);
            return true;
        }
        return false;
    }

    public void placeGrass(int grass) {

        grass = Math.min(grass, grassField.getSizeX() * grassField.getSizeY());

        // Randomly place money in moneySpace
        int placed = 0;
        while (placed < grass) {
            // Choose coordinates
            int x = (int) (Math.random() * (grassField.getSizeX()));
            int y = (int) (Math.random() * (grassField.getSizeY()));

            // Set grass if no grass at coordinates
            if (grassField.getObjectAt(x, y).equals(0)) {
                grassField.putObjectAt(x, y, 1);
                placed++;
            }

        }
    }

    public boolean tryTakeGrass(int x, int y){
        if (isGrass(x, y)) {
            grassField.putObjectAt(x, y, 0);
            return true;
        }
        return false;
    }

    public void removeRabbitAt(int x, int y){
        rabbitSpace.putObjectAt(x, y, null);
    }

    /* -- Helpers -- */
    private boolean isCellOccupied(int x, int y) {
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
