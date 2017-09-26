import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Object2DGrid;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.File;
import java.io.IOException;
import java.net.URL;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.
 *
 * @author
 */

public class RabbitsGrassSimulationAgent implements Drawable {

    private int id, x, y, vX, vY, energy;
    private RabbitsGrassSimulationSpace rgsSpace;
    private RabbitsGrassSimulationModel rgsModel;
    private Image icon;

    private static int idNumber = 0;

    public RabbitsGrassSimulationAgent(int energy, RabbitsGrassSimulationModel rgsModel) {
        this.x = -1;
        this.y = -1;
        this.energy = energy;
        this.id = ++idNumber;
        this.rgsModel = rgsModel;
        setVxVy();

        try {
            this.icon = ImageIO.read(new File("resources/rabbit.png"));
        } catch (IOException e) {
            System.out.println("Couldn't load rabbit icon, using colored rectangles...");
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
        vX = 0;
        vY = 0;
        while ((vX == 0) && (vY == 0)) {
            vX = (int) Math.floor(Math.random() * 3) - 1;
            vY = (int) Math.floor(Math.random() * 3) - 1;
        }
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
