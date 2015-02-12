package com.myrobot;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import com.myrobot.utility.EnemyWave;

public class JRMBattleSurfer extends AdvancedRobot {

	public static int BINS = 47;
	public static double _surfStats[] = new double[BINS];
	public Point2D.Double _myLocation; // our bot's location
	public Point2D.Double _enemyLocation; // enemy bot's location

	public ArrayList<EnemyWave> _enemyWaves;
	public ArrayList<Integer> _surfDirections;
	public ArrayList<Double> _surfAbsBearings;

	public static double _oppEnergy = 100.0; //enemy last known energy level

	/**
	 * This is a rectangle that represents an 800x600 battle field, used for a
	 * simple, iterative WallSmoothing method (by PEZ). If you're not familiar
	 * with WallSmoothing, the wall stick indicates the amount of space we try
	 * to always have on either end of the tank (extending straight out the
	 * front or back) before touching a wall.
	 */
	public static Rectangle2D.Double _fieldRect = new java.awt.geom.Rectangle2D.Double(
			18, 18, 764, 564);
	public static double WALL_STICK = 160;

	public void run() {
		_enemyWaves = new ArrayList<EnemyWave>();
		_surfDirections = new ArrayList<Integer>();
		_surfAbsBearings = new ArrayList<Double>();

		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);

		do {
			turnRadarRightRadians(Double.POSITIVE_INFINITY);
		} while (true);
	}
	
	/*
	 * Assuming no skipped turns, you detect a bullet on the tick after it is fired. 
	 * Its source is from the enemy's location on the previous tick it has already advanced 
	 * by its velocity from that location; and the last data the enemy saw before turning 
	 * his gun for this bullet is from two ticks ago 
	 */
	 public void onScannedRobot(ScannedRobotEvent e) {
	        _myLocation = new Point2D.Double(getX(), getY());
	 
	        double lateralVelocity = getVelocity()*Math.sin(e.getBearingRadians());
	        double absBearing = e.getBearingRadians() + getHeadingRadians();
	 
	        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing
	            - getRadarHeadingRadians()) * 2);
	 
	        _surfDirections.add(0,
	            new Integer((lateralVelocity >= 0) ? 1 : -1));
	        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));
	 
	 
	        double bulletPower = _oppEnergy - e.getEnergy();
	        if (bulletPower < 3.01 && bulletPower > 0.09
	            && _surfDirections.size() > 2) {
	            EnemyWave ew = new EnemyWave();
	            ew.fireTime = getTime() - 1;
	            ew.bulletVelocity = EnemyWave.bulletVelocity(bulletPower);
	            ew.distanceTraveled = EnemyWave.bulletVelocity(bulletPower);
	            ew.direction = ((Integer)_surfDirections.get(2)).intValue();
	            ew.directAngle = ((Double)_surfAbsBearings.get(2)).doubleValue();
	            ew.fireLocation = (Point2D.Double)_enemyLocation.clone(); // last tick
	 
	            _enemyWaves.add(ew);
	        }
	 
	        _oppEnergy = e.getEnergy();
	 
	        // update after EnemyWave detection, because that needs the previous
	        // enemy location as the source of the wave
	        _enemyLocation = EnemyWave.project(_myLocation, absBearing, e.getDistance());
	 
	        updateWaves();
	        doSurfing();
	 
	        // gun code would go here...
	    }
	 
	 /**
	 * This method is used to predict the wave surfing movement for the robot
	 */
	public void updateWaves() {
	        for (int x = 0; x < _enemyWaves.size(); x++) {
	            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
	 
	            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
	            if (ew.distanceTraveled >
	                _myLocation.distance(ew.fireLocation) + 50) {
	                _enemyWaves.remove(x);
	                x--;
	            }
	        }
	    }
	 
	    /**
	     * Since a bullet will advance by its velocity once more before checking for collisions (see Robocode/Game Physics), 
	     * this method, in effect, surfing waves until they pass the center of your bot.
	     * @return surfWave - the wave from the enemy bot
	     */
	    public EnemyWave getClosestSurfableWave() {
	        double closestDistance = 50000; // I juse use some very big number here
	        EnemyWave surfWave = null;
	 
	        for (int x = 0; x < _enemyWaves.size(); x++) {
	            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
	            double distance = _myLocation.distance(ew.fireLocation)
	                - ew.distanceTraveled;
	 
	            if (distance > ew.bulletVelocity && distance < closestDistance) {
	                surfWave = ew;
	                closestDistance = distance;
	            }
	        }
	 
	        return surfWave;
	    }
	    
	 // Given the EnemyWave that the bullet was on, and the point where we
	    // were hit, calculate the index into our stat array for that factor.
	    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
	        double offsetAngle = (EnemyWave.absoluteBearing(ew.fireLocation, targetLocation)
	            - ew.directAngle);
	        double factor = Utils.normalRelativeAngle(offsetAngle)
	            / EnemyWave.maxEscapeAngle(ew.bulletVelocity) * ew.direction;
	 
	        return (int)EnemyWave.limit(0,
	            (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
	            BINS - 1);
	    }
	    
	    // Given the EnemyWave that the bullet was on, and the point where we
	    // were hit, update our stat array to reflect the danger in that area.
	    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
	        int index = getFactorIndex(ew, targetLocation);
	 
	        for (int x = 0; x < BINS; x++) {
	            // for the spot bin that we were hit on, add 1;
	            // for the bins next to it, add 1 / 2;
	            // the next one, add 1 / 5; and so on...
	            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
	        }
	    }
	    
	    public void onHitByBullet(HitByBulletEvent e) {
	        // If the _enemyWaves collection is empty, we must have missed the
	        // detection of this wave somehow.
	        if (!_enemyWaves.isEmpty()) {
	            Point2D.Double hitBulletLocation = new Point2D.Double(
	                e.getBullet().getX(), e.getBullet().getY());
	            EnemyWave hitWave = null;
	 
	            // look through the EnemyWaves, and find one that could've hit us.
	            for (int x = 0; x < _enemyWaves.size(); x++) {
	                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
	 
	                if (Math.abs(ew.distanceTraveled -
	                    _myLocation.distance(ew.fireLocation)) < 50
	                    && Math.abs(EnemyWave.bulletVelocity(e.getBullet().getPower()) 
	                        - ew.bulletVelocity) < 0.001) {
	                    hitWave = ew;
	                    break;
	                }
	            }
	 
	            if (hitWave != null) {
	                logHit(hitWave, hitBulletLocation);
	 
	                // We can remove this wave now, of course.
	                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
	            }
	        }
	    }
	        
	        /**
	         * Given the rules of Robocode Physics, the wave we are surfing, and the orbiting direction we are predicting 
	         * (1 = clockwise, -1 = counter-clockwise), it predicts where we would be when the wave intercepts us.
	         * @param surfWave - wave from enemy bot
	         * @param direction - direction for robot to go (1 = clockwise, -1 = counter-clockwise)
	         * @return the predicted position when the wave intercepts the bot
	         */
	        public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
	            Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
	            double predictedVelocity = getVelocity();
	            double predictedHeading = getHeadingRadians();
	            double maxTurning, moveAngle, moveDir;
	     
	            int counter = 0; // number of ticks in the future
	            boolean intercepted = false;
	     
	            do {    // the rest of these code comments are rozu's
	                moveAngle =
	                    EnemyWave.wallSmoothing(predictedPosition, EnemyWave.absoluteBearing(surfWave.fireLocation,
	                    predictedPosition) + (direction * (Math.PI/2)), direction, _fieldRect, WALL_STICK)
	                    - predictedHeading;
	                moveDir = 1;
	     
	                if(Math.cos(moveAngle) < 0) {
	                    moveAngle += Math.PI;
	                    moveDir = -1;
	                }
	     
	                moveAngle = Utils.normalRelativeAngle(moveAngle);
	     
	                // maxTurning is built in like this, you can't turn more then this in one tick
	                maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
	                predictedHeading = Utils.normalRelativeAngle(predictedHeading
	                    + EnemyWave.limit(-maxTurning, moveAngle, maxTurning));
	     
	                // this one is nice ;). if predictedVelocity and moveDir have
	                // different signs you want to break down
	                // otherwise you want to accelerate (look at the factor "2")
	                predictedVelocity +=
	                    (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
	                predictedVelocity = EnemyWave.limit(-8, predictedVelocity, 8);
	     
	                // calculate the new predicted position
	                predictedPosition = EnemyWave.project(predictedPosition, predictedHeading,
	                    predictedVelocity);
	     
	                counter++;
	     
	                if (predictedPosition.distance(surfWave.fireLocation) <
	                    surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
	                    + surfWave.bulletVelocity) {
	                    intercepted = true;
	                }
	            } while(!intercepted && counter < 500);
	     
	            return predictedPosition;
	        }
	        
	        //check the direction that is safest to orbit in
	        public double checkDanger(EnemyWave surfWave, int direction) {
	            int index = getFactorIndex(surfWave,
	                predictPosition(surfWave, direction));
	     
	            return _surfStats[index];
	        }
	     
	        public void doSurfing() {
	            EnemyWave surfWave = getClosestSurfableWave();
	     
	            if (surfWave == null) { return; }
	     
	            double dangerLeft = checkDanger(surfWave, -1);
	            double dangerRight = checkDanger(surfWave, 1);
	     
	            double goAngle = EnemyWave.absoluteBearing(surfWave.fireLocation, _myLocation);
	            if (dangerLeft < dangerRight) {
	                goAngle = EnemyWave.wallSmoothing(_myLocation, goAngle - (Math.PI/2), -1, _fieldRect, WALL_STICK);
	            } else {
	                goAngle = EnemyWave.wallSmoothing(_myLocation, goAngle + (Math.PI/2), 1, _fieldRect, WALL_STICK);
	            }
	     
	            EnemyWave.setBackAsFront(this, goAngle);
	        }

}
