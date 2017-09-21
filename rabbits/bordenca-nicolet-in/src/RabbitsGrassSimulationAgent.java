import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Object2DGrid;

import java.awt.*;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationAgent implements Drawable {

    private int x;
    private int y;
    private int vX;
    private int vY;
    private int energy;
    private RabbitsGrassSimulationSpace rgsSpace;
    private RabbitsGrassSimulationModel rgsModel;

    private static int idNumber = 0;
    private int id;

    public RabbitsGrassSimulationAgent(int energy) {
        x = -1;
        y = -1;
        setVxVy();
        this.energy = energy;
        id = ++idNumber;
    }

    public void setXY(int newX, int newY) {
        x = newX;
        y = newY;
    }


    private void setVxVy() {
        vX = 0;
        vY = 0;
        while ((vX == 0) && (vY == 0)) {
            vX = (int) Math.floor(Math.random() * 3) - 1;
            vY = (int) Math.floor(Math.random() * 3) - 1;
        }
    }


    public void draw(SimGraphics g) {
        g.drawFastRoundRect(energy > 5 ? Color.PINK : Color.GRAY);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getID() {
        return "R-" + id;
    }

    public int getEnergy() {
        return energy;
    }


    public void report() {
        System.out.println(getID() +
                " at " +
                x + ", " + y +
                " has " +
                getEnergy() + " energy.");
    }

    public void birthFatigue() {
        energy /= 2;
    }

    public void step() {
        int newX = x + vX;
        int newY = y + vY;

        Object2DGrid grid = rgsSpace.getCurrentRabbitSpace();
        newX = (newX + grid.getSizeX()) % grid.getSizeX();
        newY = (newY + grid.getSizeY()) % grid.getSizeY();

        if (tryMove(newX, newY)) {
            if (rgsSpace.tryEatGrass(x, y)) {
                energy += rgsModel.getGrassEnergy();
            }
        }
        setVxVy();
        energy--;
    }


    private boolean tryMove(int newX, int newY) {
        return rgsSpace.moveRabbitAt(x, y, newX, newY);
    }

    public void setRgsSpace(RabbitsGrassSimulationSpace rgsSpace) {
        this.rgsSpace = rgsSpace;
    }

    public void setRgsModel(RabbitsGrassSimulationModel rgsModel) {
        this.rgsModel = rgsModel;
    }
}
