package timsplayer;
import battlecode.common.*;
import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Random rand = new Random();
    static final int HEAD_ARCHON_CHANNEL = 1; //stores the ID of the head archon
    static final int GARDENER_COUNT_CHANNEL = 2; //the final count of gardeners read by archons
    static final int GARDENER_SUM_CHANNEL = 3; //the sum of gardeners incremented by gardeners on their turn

    static final int MAX_GARDENERS = 5; //max number of gardeners we want to build
    static final int MAX_TREES = 6; //max number of trees

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
                }
                Direction dir = randomDirection();
                if (rc.canHireGardener(dir) && rc.readBroadcast(GARDENER_COUNT_CHANNEL) < MAX_GARDENERS) { //will hire a gardener if it is possible and there are less than the desired maximum
                    rc.hireGardener(dir);
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
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(7, rc.getTeam()); //get an array of nearby trees
                rc.broadcast(GARDENER_SUM_CHANNEL, rc.readBroadcast(GARDENER_SUM_CHANNEL) + 1); //adds 1 to the sum channel
                Direction dir = randomDirection();

                //this block will execute for watering nearby trees
                if(nearbyTrees.length != 0) { //will only execute if there are nearby trees
                    int dyingTreeID = 0; //id that will be set to the value of a tree that needs help
                    MapLocation dyingTreeLoc = null; //Maplocation of the dying tree
                    for (int i = 0; i < nearbyTrees.length; i++) { //for loop to parse through the nearby trees
                        if(nearbyTrees[i].getMaxHealth() - nearbyTrees[i].getHealth() >= 5){ //watering heals for 5, so if a tree is missing 5 or more health, we call them a dying tree and try to help them
                            dyingTreeID = nearbyTrees[i].getID(); //sets the ID of the tree with missing health
                            dyingTreeLoc = nearbyTrees[i].getLocation(); //same for location
                            break;
                        }
                    }
                    if(rc.canWater(dyingTreeID)){ //tries to water the dying tree
                        rc.water(dyingTreeID);
                    }
                    else if (dyingTreeLoc != null && rc.canMove(dyingTreeLoc)) { //if we have a dying tree, and we can move towards them, we do
                        rc.move(dyingTreeLoc);
                    }


                }
                if(rc.getTreeCount() < MAX_TREES && rc.canPlantTree(dir)){ //if there aren't enough trees and a tree can be planted in the random direciton dir
                    rc.plantTree(dir);
                }
                else { //contained in an else because we don't want gardeners wandering away from their trees
                    move(dir);
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
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void move(Direction dir) throws GameActionException {
        try {
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Direction randomDirection() {
        return new Direction(rand.nextFloat() * 2 * (float) Math.PI); //takes a random number from "rand" multiplies it by 2pi to get a random radian
    }
}