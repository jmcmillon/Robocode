package com.myrobot.utility;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import robocode.AdvancedRobot;
import robocode.util.Utils;

public class EnemyWave {
	public Point2D.Double fireLocation;
	public long fireTime;
	public double bulletVelocity;
	public double directAngle;
	public double distanceTraveled;
	public int direction;

	public EnemyWave() {
	}

	public static double wallSmoothing(Point2D.Double botLocation,
			double angle, int orientation, Rectangle2D.Double fieldRect,
			double wallStick) {
		while (!fieldRect.contains(project(botLocation, angle, wallStick))) {
			angle += orientation * 0.05;
		}
		return angle;
	}

	public static Point2D.Double project(Point2D.Double sourceLocation,
			double angle, double length) {
		return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
				sourceLocation.y + Math.cos(angle) * length);
	}

	public static double absoluteBearing(Point2D.Double source,
			Point2D.Double target) {
		return Math.atan2(target.x - source.x, target.y - source.y);
	}

	public static double limit(double min, double value, double max) {
		return Math.max(min, Math.min(value, max));
	}

	public static double bulletVelocity(double power) {
		return (20.0 - (3.0 * power));
	}

	public static double maxEscapeAngle(double velocity) {
		return Math.asin(8.0 / velocity);
	}

	public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
		double angle = Utils.normalRelativeAngle(goAngle
				- robot.getHeadingRadians());
		if (Math.abs(angle) > (Math.PI / 2)) {
			if (angle < 0) {
				robot.setTurnRightRadians(Math.PI + angle);
			} else {
				robot.setTurnLeftRadians(Math.PI - angle);
			}
			robot.setBack(100);
		} else {
			if (angle < 0) {
				robot.setTurnLeftRadians(-1 * angle);
			} else {
				robot.setTurnRightRadians(angle);
			}
			robot.setAhead(100);
		}
	}
}
