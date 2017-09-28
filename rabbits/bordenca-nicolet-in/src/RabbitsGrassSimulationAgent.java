import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationAgent implements Drawable {

    private int id, x, y, vX, vY, energy;
    private RabbitsGrassSimulationSpace rgsSpace;
    private RabbitsGrassSimulationModel rgsModel;

    private static Image icon;
    private static int idNumber = 0;

    public RabbitsGrassSimulationAgent(int energy, RabbitsGrassSimulationModel rgsModel) {
        this.x = -1;
        this.y = -1;
        this.energy = energy;
        this.id = ++idNumber;
        this.rgsModel = rgsModel;
        setVxVy();

        if (icon == null) {
            try {
                icon = ImageIO.read(new File("resources/rabbit.png"));
            } catch (IOException e) {
                System.out.println("Couldn't load rabbit icon, using colored rectangles...");
            }
        }
    }

    /* -- Public interface -- */
    public void step() {
        tryMove();
        setVxVy();
        fatigue();
    }

    public void birthFatigue() {
        energy /= 2;
    }

    public void draw(SimGraphics g) {
        if (this.icon == null) {
            g.drawFastRoundRect(energy > 5 ? Color.PINK : Color.GRAY);
        } else {
            g.drawImageToFit(this.icon);
        }
    }

    public void report() {
        System.out.println(getID() + " at " + x + ", " + y + " has " + getEnergy() + " energy.");
    }

    /* -- Helper methods -- */
    private void tryMove() {
        int newX = x + vX;
        int newY = y + vY;

        Dimension dimension = rgsSpace.getRabbitSpaceSize();
        newX = (newX + dimension.width) % dimension.width;
        newY = (newY + dimension.height) % dimension.height;

        if (rgsSpace.moveRabbitAt(x, y, newX, newY)) {
            if (rgsSpace.tryTakeGrass(x, y)) {
                eat();
            }
        }
    }

    private void eat() {
        energy += rgsModel.getGrassEnergy();
    }

    private void fatigue() {
        energy--;
    }

    private void setVxVy() {
        // Only North, South, East and West
        int dir = Math.random() < 0.5 ? -1 : 1;
        boolean vertical = Math.random() < 0.5;
        vX = vertical ? 0 : dir;
        vY = vertical ? dir : 0;
    }

    /* -- Getters - Setters -- */
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

    public void setXY(int newX, int newY) {
        x = newX;
        y = newY;
    }

    public void setRgsSpace(RabbitsGrassSimulationSpace rgsSpace) {
        this.rgsSpace = rgsSpace;
    }

    public void setRgsModel(RabbitsGrassSimulationModel rgsModel) {
        this.rgsModel = rgsModel;
    }
}
