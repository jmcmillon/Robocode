package myrobot;

import java.awt.Color;

import robocode.Robot;
import robocode.ScannedRobotEvent;

public class JRMWarMachine extends Robot {

	public void run() {
		setBodyColor(Color.WHITE);
		setGunColor(Color.RED);

		while (true) {
			ahead(1000);
			turnRight(180);
			ahead(1000);
			turnLeft(180);
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		// change the firepower based on how close you are to the enemy
		fire(Math.min(400 / e.getDistance(), 3));
	}

}
