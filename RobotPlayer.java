package timsplayer;
import battlecode.common.*;
import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Random rand = new Random();
    static final int HEAD_ARCHON_CHANNEL = 1; //stores the ID of the head archon
    static final int GARDENER_COUNT_CHANNEL = 2; //the final count of gardeners read by archons
    static final int GARDENER_SUM_CHANNEL = 3; //the sum of gardeners incremented by gardeners on their turn
    static final int SCOUT_COUNT_CHANNEL = 4;
    static final int SCOUT_SUM_CHANNEL = 5;
    static final int SOLDIER_COUNT_CHANNEL = 6;
    static final int SOLDIER_SUM_CHANNEL = 7;
    static final int ATTACK_LOCATION_X_CHANNEL = 8; // holds the x location (rounded to the nearest int) of a high priority target (like enemy tree garden)
    static final int ATTACK_LOCATION_Y_CHANNEL = 9;
    static final int MAX_TREES_CHANNEL = 10; // holds max trees, so the amount of trees scales to the number of gardeners

    static final int MAX_GARDENERS = 5; //max number of gardeners we want to build
    static final int MAX_SCOUTS = 4;
    static final int MAX_SOLDIERS = 8;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
        }
    }

    //running for archons
    static void runArchon() throws GameActionException {
        while (true) {
            try {
                //need to handle what happens if the head archon dies
                if(rc.getRoundNum() == 1 && rc.readBroadcast(HEAD_ARCHON_CHANNEL) == 0){ // excecuted only on first round, sends the senior archon's ID to the head archon channel
                    rc.broadcast(HEAD_ARCHON_CHANNEL, rc.getID());
                }
                //this method of counting makes bots easy to track by enemies
                if(rc.getID() == rc.readBroadcast(HEAD_ARCHON_CHANNEL)) { // only the head archon does the counting
                    rc.broadcast(GARDENER_COUNT_CHANNEL, rc.readBroadcast(GARDENER_SUM_CHANNEL)); //moves the sum to the final count channel to be used by archons
                    rc.broadcast(GARDENER_SUM_CHANNEL, 0); //resets the sum channel
                    rc.broadcast(SCOUT_COUNT_CHANNEL, rc.readBroadcast(SCOUT_SUM_CHANNEL));
                    rc.broadcast(SCOUT_SUM_CHANNEL, 0);
                    rc.broadcast(SOLDIER_COUNT_CHANNEL, rc.readBroadcast(SOLDIER_SUM_CHANNEL));
                    rc.broadcast(SOLDIER_SUM_CHANNEL, 0);
                    if(rc.getTeamBullets() > 500)
                    {
                        rc.donate(10); //getting victory points
                    }
                }

                Direction dir = randomDirection();
                if (rc.canHireGardener(dir) && rc.readBroadcast(GARDENER_COUNT_CHANNEL) < MAX_GARDENERS) { //will hire a gardener if it is possible and there are less than the desired maximum
                    rc.hireGardener(dir);
                    rc.broadcast(MAX_TREES_CHANNEL, rc.readBroadcast(MAX_TREES_CHANNEL) + 2); //increase our max amount of trees by 2 for each gardener
                }

                move(dir);
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //running for gardeners
    static void runGardener() throws GameActionException {
        while (true) {
            try {
                rc.broadcast(GARDENER_SUM_CHANNEL, rc.readBroadcast(GARDENER_SUM_CHANNEL) + 1); //adds 1 to the sum channel
                Direction dir = randomDirection();

                //this block will execute for watering nearby trees
                TreeInfo dyingTree = getDyingTree();
                if (dyingTree != null) {
                    if (rc.canWater(dyingTree.getID())) { //tries to water the dying tree
                        rc.water(dyingTree.getID());
                    } else if (rc.canMove(dyingTree.getLocation())) { //if we have a dying tree, and we can move towards them, we do
                        rc.move(dyingTree.getLocation());
                    }
                } else {
                    move(dir);
                }
                if(rc.readBroadcast(SCOUT_COUNT_CHANNEL) == 0 && rc.canBuildRobot(RobotType.SCOUT, dir)){ //to build an early scout, early tree shaking is very valuable
                    rc.buildRobot(RobotType.SCOUT, dir);
                }
                if (rc.getTreeCount() < rc.readBroadcast(MAX_TREES_CHANNEL) && rc.canPlantTree(dir)) { //if there aren't enough trees and a tree can be planted in the random direciton dir
                    rc.plantTree(dir);
                } else if(rc.getTeamBullets() > 80 && rc.canBuildRobot(RobotType.SCOUT, dir) && rc.readBroadcast(SCOUT_COUNT_CHANNEL) < MAX_SCOUTS){
                    rc.buildRobot(RobotType.SCOUT, dir);
                } else if (rc.getTeamBullets() > 100 && rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.readBroadcast(SOLDIER_COUNT_CHANNEL) < MAX_SOLDIERS){
                    rc.buildRobot(RobotType.SOLDIER, dir);
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //running for soldiers
    static void runSoldier() throws GameActionException {
        while (true) {
            try {
                rc.broadcast(SOLDIER_SUM_CHANNEL, rc.readBroadcast(SOLDIER_SUM_CHANNEL) + 1); //counter for soldiers
                Direction dir = randomDirection();
                RobotInfo[] enemyBots = rc.senseNearbyRobots(RobotType.SOLDIER.bodyRadius + RobotType.SOLDIER.sensorRadius, rc.getTeam().opponent()); //sense all enemy bots nearby and put it into an array
                if(enemyBots.length != 0){ //if there are nearby enemies, fire at them
                    fireBullet(enemyBots[0].getLocation());
                }
                if(rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL) != 0) {
                    System.out.println("Moving to target location"); //for debugging
                    MapLocation attackLocation = new MapLocation((float) rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL), (float) rc.readBroadcast(ATTACK_LOCATION_Y_CHANNEL)); //creates a maplocation of the attack target
                    if(rc.getLocation().distanceTo(attackLocation) < 1){
                        if(!canSenseEnemyGarden(rc.getType().bodyRadius + rc.getType().sensorRadius)){
                            rc.broadcast(ATTACK_LOCATION_X_CHANNEL, 0);
                            rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, 0);
                        }
                    }
                    else {
                        moveTo(attackLocation); //try to move to that location
                    }
                }
                move(dir);
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //running for lumberjacks
    static void runLumberjack() throws GameActionException {
        while (true) {
            try {
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //running for tanks
    static void runTank() throws GameActionException {
        while (true) {
            try {
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //running for scouts
    static void runScout() throws GameActionException {
        while (true) {
            try {
                Direction dir = randomDirection();
                rc.broadcast(SCOUT_SUM_CHANNEL, rc.readBroadcast(SCOUT_SUM_CHANNEL) + 1); //counting number of scouts
                TreeInfo neutralTree = getNearbyTree(Team.NEUTRAL);  //get the closest nearby tree
                if(neutralTree != null && neutralTree.getContainedBullets() != 0){ //if one exists and it has bullets in it
                    if(rc.canShake(neutralTree.getID())){ //if we can shake it
                        rc.shake(neutralTree.getID());
                    }
                    else {
                        moveTo(neutralTree.getLocation()); //else move towards it
                    }
                } else { //if there is nearby tree or the closest one has no bullets, move randomly


                    TreeInfo enemyTree = getNearbyTree(rc.getTeam().opponent());
                    if(enemyTree != null && rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL) == 0){ //if we sense enemy trees, and there is no priority target
                        MapLocation priority = getEnemyGardenLoc(rc.getType().bodyRadius + rc.getType().sensorRadius); //try to get a location for attack
                        if(priority != null){ //if we found a priority target
                            System.out.println("Found a target"); //for debugging
                            rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int)priority.x); //broadcast the closest x value to the x coord channel
                            rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int)priority.y); //some for y value
                        } else{
                            moveTo(enemyTree.getLocation());
                        }
                    }
                    //this is where i should put the high priority target detection
                }
                move(dir);
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void move(Direction dir) throws GameActionException {
        try {
            if (rc.canMove(dir) && !rc.hasMoved()) {
                rc.move(dir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void moveTo(MapLocation loc) throws GameActionException{
        try{
            if(rc.canMove(loc) && !rc.hasMoved()){
                rc.move(loc);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Direction randomDirection() {
        return new Direction(rand.nextFloat() * 2 * (float) Math.PI); //takes a random number from "rand" multiplies it by 2pi to get a random radian
    }

    public static TreeInfo getDyingTree(){ //will return treeinfo of a dying tree (missing 10 more more health) or null if there are none
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius + rc.getType().bodyRadius, rc.getTeam()); //get an array of nearby trees
        if(nearbyTrees.length != 0) { //will only execute if there are nearby trees
            for (int i = 0; i < nearbyTrees.length; i++) { //for loop to parse through the nearby trees
                if(nearbyTrees[i].getMaxHealth() - nearbyTrees[i].getHealth() > 10){
                    return nearbyTrees[i];
                }
                }
            }
        return null;
    }

    public static TreeInfo getNearbyTree(Team team){ //will return treeinfo of a nearby tree (missing 10 more more health) or null if there are none
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius + rc.getType().bodyRadius, team); //get an array of nearby trees
        if(nearbyTrees.length != 0) {
            return nearbyTrees[0];
        }
        return null;
    }
    //methods to fire bullets based on a given maplocation or direction
    public static void fireBullet (MapLocation loc) throws GameActionException {
        if(rc.canFireSingleShot()){
            Direction dir = rc.getLocation().directionTo(loc);
            rc.fireSingleShot(dir);
        }


    }

    public static void fireBullet(Direction dir) throws GameActionException{
        if(rc.canFireSingleShot()){
            rc.fireSingleShot(dir);
        }
    }

    public static boolean canSenseEnemyGarden(float radius){
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(radius, rc.getTeam().opponent());
        if(enemyTrees.length >= 3){
            return true;
        }
        return false;
    }

    public static MapLocation getEnemyGardenLoc(float radius) {
        if (canSenseEnemyGarden(radius)) {
            TreeInfo[] enemyTrees = rc.senseNearbyTrees(radius, rc.getTeam().opponent()); //sense all nearby enemy trees
            return enemyTrees[0].getLocation();
        }
        return null;
    }
}