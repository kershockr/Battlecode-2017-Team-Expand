package RewritingLumberjacks;

import battlecode.common.*;
import java.math.*;

import java.util.Random;

public strictfp class RobotPlayer
{
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
    static final int NESTED_GARDENERS = 10; // holds max trees, so the amount of trees scales to the number of gardeners
    static final int MAX_SOLDIER_CHANNEL = 11;
    static final int LUMBERJACK_LOCATION_X_CHANNEL = 12;
    static final int LUMBERJACK_LOCATION_Y_CHANNEL = 13;
    static final int LUMBERJACK_COUNT_CHANNEL = 14;
    static final int LUMBERJACK_SUM_CHANNEL = 15;
    static final int OBSTRUCTION_CHANNEL = 16;
    static final int PATH_CHANGE_DEGREES_CHANNEL = 17;

    //max number of gardeners we want to build

    static Direction buildDirection = Direction.getSouth();

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException
    {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType())
        {
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
    static void runArchon() throws GameActionException
    {
        while (true)
        {
            try
            {
                //need to handle what happens if the head archon dies
                if (rc.getRoundNum() == 1 && rc.readBroadcast(HEAD_ARCHON_CHANNEL) == 0)
                { // excecuted only on first round, sends the senior archon's ID to the head archon channel
                    rc.broadcast(HEAD_ARCHON_CHANNEL, rc.getID());
                    rc.broadcast(MAX_SOLDIER_CHANNEL, 10);
                }
                //this method of counting makes bots easy to track by enemies
                if (rc.getID() == rc.readBroadcast(HEAD_ARCHON_CHANNEL))
                { // only the head archon does the counting
                    rc.broadcast(GARDENER_COUNT_CHANNEL, rc.readBroadcast(GARDENER_SUM_CHANNEL)); //moves the sum to the final count channel to be used by archons
                    rc.broadcast(GARDENER_SUM_CHANNEL, 0); //resets the sum channel
                    rc.broadcast(SCOUT_COUNT_CHANNEL, rc.readBroadcast(SCOUT_SUM_CHANNEL));
                    rc.broadcast(SCOUT_SUM_CHANNEL, 0);
                    rc.broadcast(SOLDIER_COUNT_CHANNEL, rc.readBroadcast(SOLDIER_SUM_CHANNEL));
                    rc.broadcast(SOLDIER_SUM_CHANNEL, 0);
                    rc.broadcast(LUMBERJACK_COUNT_CHANNEL, rc.readBroadcast(LUMBERJACK_SUM_CHANNEL));
                    rc.broadcast(LUMBERJACK_SUM_CHANNEL, 0);
                    rc.broadcast(PATH_CHANGE_DEGREES_CHANNEL, (rc.getRoundNum() % 200 < 100)?90:270);
                    if (rc.getTeamBullets() > 500)
                    {
                        rc.donate(10); //getting victory points
                    }
                }
                if(rc.getRoundNum() > 2900)
                {
                    rc.donate(rc.getTeamBullets());
                }
                Direction dir = randomDirection();
                if (rc.canHireGardener(dir) && rc.readBroadcast(GARDENER_COUNT_CHANNEL) <= rc.readBroadcast(NESTED_GARDENERS))
                { //will hire a gardener if it is possible and there are less than the desired maximum
                    rc.hireGardener(dir);

                }

                move(dir);

                Clock.yield();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    static void runGardener() throws GameActionException
    {
        MapLocation nestLocation = null;
        Direction movingDirection = null;
        boolean nestComplete = false;
        MapLocation[] enemyLocation = rc.getInitialArchonLocations(rc.getTeam().opponent());
        Direction nestDirection = rc.getLocation().directionTo(enemyLocation[0]);
        int unitsBuilt = 0;
        boolean sentToChannel = false;

        while(true)
        {
            try
            {
                rc.broadcast(GARDENER_SUM_CHANNEL, rc.readBroadcast(GARDENER_SUM_CHANNEL) + 1); //adds 1 to the sum channel
                RobotInfo[] veryCloseEnemies = rc.senseNearbyRobots((float)3, rc.getTeam().opponent());
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees((float)2.1, rc.getTeam());

                nestComplete = (nearbyTrees.length >= 5); //will check if we have enough trees in our nest. if we do, nest is complete

                if(nestComplete && !sentToChannel)
                {
                    rc.broadcast(NESTED_GARDENERS, rc.readBroadcast(NESTED_GARDENERS) + 1);
                    sentToChannel = true;
                }
                //building an early scout
                if (rc.readBroadcast(LUMBERJACK_COUNT_CHANNEL) == 0 && rc.canBuildRobot(RobotType.LUMBERJACK, nestDirection))
                { //to build an early jack, protects us a bit and helps clear shit
                    rc.buildRobot(RobotType.LUMBERJACK, nestDirection);
                }
                else if (rc.readBroadcast(SCOUT_COUNT_CHANNEL) == 0 && rc.canBuildRobot(RobotType.SCOUT, nestDirection))
                { //to build an early scout, early tree shaking is very valuable
                    rc.buildRobot(RobotType.SCOUT, nestDirection);
                }


                    if (rc.getLocation().equals(nestLocation)) //are you in your nest
                    {//yes
                        if (nestComplete) //is your nest complete?
                        {//yes
                            rc.setIndicatorDot(rc.getLocation(), 0, 256, 0);

                            //this block will execute for watering nearby trees
                            TreeInfo dyingTree = getDyingTree();
                            if (dyingTree != null)
                            {
                                if (rc.canWater(dyingTree.getID()))
                                { //tries to water the dying tree
                                    rc.water(dyingTree.getID());
                                } else if (rc.canMove(dyingTree.getLocation()))
                                { //if we have a dying tree, and we can move towards them, we do
                                    rc.move(dyingTree.getLocation());
                                }
                            }

                            //building units
                            if (unitsBuilt % 6 <= 3) //first four builds should be soldiers
                            {
                                if (rc.canBuildRobot(RobotType.SOLDIER, nestDirection))
                                {
                                    rc.buildRobot(RobotType.SOLDIER, nestDirection);
                                    unitsBuilt++;
                                }
                            }
                            else if(unitsBuilt % 6 == 4)
                            {
                                if(rc.canBuildRobot(RobotType.LUMBERJACK, nestDirection))
                                {
                                    rc.buildRobot(RobotType.LUMBERJACK, nestDirection);
                                    unitsBuilt++;
                                }
                            }
                            else if(unitsBuilt % 6 == 5)
                            {
                                if(rc.canBuildRobot(RobotType.SCOUT, nestDirection))
                                {
                                    rc.buildRobot(RobotType.SCOUT, nestDirection);
                                    unitsBuilt++;
                                }
                            }



                        } else
                        {//no
                            nest(rc.getLocation(), nestDirection);
                        }
                    } else
                    {//no
                        if (nestLocation != null) //do you have a nest location?
                        {//yes
                            //move to it
                            if (rc.isCircleOccupiedExceptByThisRobot(nestLocation, (float) 3) || !rc.onTheMap(nestLocation, (float) 3))
                            {
                                nestLocation = null;
                            }

                            if (rc.getLocation().distanceTo(nestLocation) < 1) //this handles the special case of needing to move to the exact maplocation
                            {
                                rc.move(nestLocation);
                            } else
                            {
                                movingDirection = rc.getLocation().directionTo(nestLocation); //move in it's direction
                                tryMove(movingDirection);
                            }
                        } else
                        {//no
                            float offset = 0; //try to find one
                            while (offset <= 3 && nestLocation == null) //while we haven't checked more than 3 times and we haven't found a nest yet
                            {
                                MapLocation center = rc.getLocation().add(nestDirection.rotateLeftDegrees(180), offset);
                                if (!rc.isCircleOccupiedExceptByThisRobot(center, (float) 3) && rc.onTheMap(center, (float) 3)) // if the circle is empty except this robot
                                {
                                    nestLocation = center; //set the nest location to the empty circle
                                }
                                offset += 1;
                            }
                            //if you still haven't found a nest, move randomly
                            if (nestLocation == null)
                            {
                                movingDirection = randomDirection();
                                tryMove(movingDirection);
                            }
                        }
                    }

                if(veryCloseEnemies.length > 0)
                {
                    rc.broadcast(LUMBERJACK_LOCATION_X_CHANNEL, (int)rc.getLocation().x);
                    rc.broadcast(LUMBERJACK_LOCATION_Y_CHANNEL, (int)rc.getLocation().y);
                }
                if(rc.getRoundNum() > 2990)
                {
                    rc.donate(rc.getTeamBullets());
                }
                Clock.yield();

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /*
    static void runSoldier() throws GameActionException
    {
        float senseRadius = rc.getType().bodyRadius + rc.getType().sensorRadius;
        while(true)
        {
            try
            {
                rc.broadcast(SOLDIER_SUM_CHANNEL, rc.readBroadcast(SOLDIER_SUM_CHANNEL) + 1); //counter for soldiers
                boolean moved = false;
                boolean fired = false;
                BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3); //sense nearby bullets in a radius of 3 units around
                RobotInfo[] enemyBots = rc.senseNearbyRobots(senseRadius, rc.getTeam().opponent()); //sense all enemy bots nearby and put it into an array

                //block for dodging. executed first because we don't want to waste movements and not be able to dodge
                if(nearbyBullets.length != 0)
                {

                }

                Clock.yield();
            } catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    */

    //running for soldiers
    static void runSoldier() throws GameActionException
    {
        while (true)
        {
            try
            {
                rc.broadcast(SOLDIER_SUM_CHANNEL, rc.readBroadcast(SOLDIER_SUM_CHANNEL) + 1); //counter for soldiers
                Direction dir = randomDirection(); //direction we will eventually move, starts out random
                boolean moved = false;
                Direction fired = null;

                //dodge incoming bullets first
                BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3); //sense nearby bullets in a radius of 3 units around
                if(nearbyBullets.length != 0)
                {
                    if(bulletWillCollide(nearbyBullets[0])) //if we're in the path of a bullet
                    {//dodge
                        BulletInfo incomingBullet = nearbyBullets[0];
                        if(canDodge(incomingBullet.getDir())) //if we can dodge left or right
                        {
                            dir = dodgeDirection(rc.getLocation().directionTo(incomingBullet.getLocation()));
                            moved = tryMove(dir);
                        }
                        else //if we can't dodge left or right, set movement direction away from bullet
                        {
                            dir = rc.getLocation().directionTo(incomingBullet.getLocation()).rotateLeftDegrees(180); //move away from the bullet
                            moved = tryMove(dir);
                        }
                    }

                }



                //firing at enemies
                RobotInfo[] enemyBots = rc.senseNearbyRobots(RobotType.SOLDIER.bodyRadius + RobotType.SOLDIER.sensorRadius, rc.getTeam().opponent()); //sense all enemy bots nearby and put it into an array
                if (enemyBots.length != 0)
                { //if there are nearby enemies, fire at them
                    if(enemyBots.length >= 10) //if we sense an army, set that as a new location
                    {
                        rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int)enemyBots[5].getLocation().x);
                        rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int)enemyBots[5].getLocation().y);
                    }

                    Direction firingDirection = rc.getLocation().directionTo(enemyBots[0].getLocation());
                    if(enemyBots[0].getLocation().isWithinDistance(rc.getLocation(), 4))
                    {
                        if(!willFriendlyFire(firingDirection))
                        {
                            fired = fireTriadBullet(enemyBots[0].getLocation());
                        }
                        else
                        {
                            moved = tryMove(firingDirection.rotateLeftDegrees(90));
                        }
                    }
                    if(!willFriendlyFire(firingDirection))
                    {
                        fired = fireBullet(enemyBots[0].getLocation());
                    }
                    else
                    {
                        moved = tryMove(firingDirection.rotateLeftDegrees(90));
                    }
                }

                //if there is an attack location
                if (rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL) != 0)
                {
                    MapLocation attackLocation = new MapLocation((float) rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL), (float) rc.readBroadcast(ATTACK_LOCATION_Y_CHANNEL)); //creates a maplocation of the attack target
                    if (rc.getLocation().distanceTo(attackLocation) < 1) //if we are close to the attack location
                    {
                        if (!canSenseEnemyGarden(rc.getType().bodyRadius + rc.getType().sensorRadius) && !canSenseEnemyArchon(rc.getType().bodyRadius + rc.getType().sensorRadius)) //if we don't sense any more gardens, reset the location
                        {
                            rc.broadcast(ATTACK_LOCATION_X_CHANNEL, 0);
                            rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, 0);
                        }
                    } else //otherwise, move to
                    {
                        Direction directionToTarget = rc.getLocation().directionTo(attackLocation);
                        moved = tryMove(directionToTarget); //try to move to that location
                        if(!moved)
                        {
                            TreeInfo[] neutralTrees = rc.senseNearbyTrees((float)4, Team.NEUTRAL);
                            if(neutralTrees.length != 0)
                            {
                                rc.broadcast(OBSTRUCTION_CHANNEL, rc.readBroadcast(OBSTRUCTION_CHANNEL) + 1);
                                if(rc.readBroadcast(OBSTRUCTION_CHANNEL) == 10)
                                {
                                    rc.broadcast(LUMBERJACK_LOCATION_X_CHANNEL, (int)neutralTrees[0].getLocation().x);
                                    rc.broadcast(LUMBERJACK_LOCATION_Y_CHANNEL, (int)neutralTrees[0].getLocation().y);
                                }
                                if(!willFriendlyFire(directionToTarget) && !willFriendlyFire(directionToTarget.rotateLeftDegrees(20)) && !willFriendlyFire(directionToTarget.rotateLeftDegrees(20)))
                                {
                                    rc.setIndicatorDot(rc.getLocation(), 0, 256, 256);
                                    if(rc.canFireSingleShot())
                                    {
                                        rc.fireSingleShot(directionToTarget);
                                    }
                                }
                            }
                            tryMove(directionToTarget.rotateLeftDegrees(rc.readBroadcast(PATH_CHANGE_DEGREES_CHANNEL)));
                        }


                    }
                }
                if(!moved && (fired == null))
                {
                    move(dir);
                }
                Clock.yield();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    //running for lumberjacks
    static void runLumberjack() throws GameActionException
    {
        float senseRadius = rc.getType().bodyRadius + rc.getType().sensorRadius;
        MapLocation[] archons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        Direction dir = rc.getLocation().directionTo(archons[0]);

        while(true)
        {
            try
            {
                rc.broadcast(LUMBERJACK_SUM_CHANNEL, rc.readBroadcast(LUMBERJACK_SUM_CHANNEL) + 1);
                MapLocation targetLocation = null;
                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots((float)7, rc.getTeam().opponent());
                TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(4, Team.NEUTRAL);
                TreeInfo targetTree = null;

                if(nearbyEnemies.length > 0) //if there are enemies nearby
                {
                    targetLocation = nearbyEnemies[0].getLocation(); //target location is the nearest enemy
                    if(rc.getLocation().distanceTo(targetLocation) <= 2.5) //if we are within strike distance
                    {
                        rc.strike(); //strike
                    }
                    else //if we aren't within strike distance
                    {
                        pathTo(targetLocation); //try to move towards the enemy
                        if(rc.getLocation().distanceTo(targetLocation) <= 2) //if that movement put is within strike distance
                        {
                            rc.strike(); //strike
                        }
                    }
                }
                else if (rc.readBroadcast(LUMBERJACK_LOCATION_X_CHANNEL) != 0) //otherwise, if there is a chop location
                {
                    //target Location is the chop location
                    targetLocation = new MapLocation(rc.readBroadcast(LUMBERJACK_LOCATION_X_CHANNEL), rc.readBroadcast(LUMBERJACK_LOCATION_Y_CHANNEL));
                    if(rc.getLocation().distanceTo(targetLocation) < 5) //if we are close to the target location
                    {
                        if(nearbyNeutralTrees.length > 0) //if there are trees near the location
                        {
                            chopTree(nearbyNeutralTrees[0]); //move to and chop the nearest tree
                        }
                        else //otherwise if there are no trees near the location
                        {
                            rc.broadcast(LUMBERJACK_LOCATION_X_CHANNEL, 0); //reset the location
                            rc.broadcast(LUMBERJACK_LOCATION_Y_CHANNEL, 0);
                            rc.broadcast(OBSTRUCTION_CHANNEL, 0);
                        }
                    }
                    else //otherwise if we aren't near the target location
                    {
                        pathTo(targetLocation); //path to the target
                    }
                }
                else if (nearbyNeutralTrees.length != 0) //otherwise, if there are neutral trees near us
                {
                    for(int i = 0; i < nearbyNeutralTrees.length; i++)
                    {
                        if(nearbyNeutralTrees[i].getContainedRobot() != null)
                        {
                            targetTree = nearbyNeutralTrees[i];
                            break;
                        }
                    }
                    if(targetTree == null)
                    {
                        targetTree = nearbyNeutralTrees[0];
                    }
                    if(targetTree.getContainedBullets() > 0)
                    {
                        if(rc.canShake(targetTree.getID()))
                        {
                            rc.shake(targetTree.getID());
                        }
                    }
                    chopTree(targetTree); //move to and chop the nearest tree
                }
                else //otherwise, if we have nothing to do
                {
                    boolean moved = tryMove(dir);
                    if(!moved)
                    {
                        dir = randomDirection();
                    }
                }
                if(targetLocation != null)
                {
                    rc.setIndicatorLine(rc.getLocation(), targetLocation, 256, 0, 0);
                }
                Clock.yield();
            } catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }



    //running for tanks
    static void runTank() throws GameActionException
    {
        while (true)
        {
            try
            {
                Direction dir = randomDirection(); //direction we will eventually move, starts out random
                boolean moved = false;
                Direction fired = null;

                RobotInfo[] enemyBots = rc.senseNearbyRobots(RobotType.SOLDIER.bodyRadius + RobotType.SOLDIER.sensorRadius, rc.getTeam().opponent()); //sense all enemy bots nearby and put it into an array
                if (enemyBots.length != 0)
                { //if there are nearby enemies, fire at them
                    if(enemyBots.length >= 10) //if we sense an army, set that as a new location
                    {
                        rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int)enemyBots[5].getLocation().x);
                        rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int)enemyBots[5].getLocation().y);
                    }

                    Direction firingDirection = rc.getLocation().directionTo(enemyBots[0].getLocation());
                    if(enemyBots[0].getLocation().isWithinDistance(rc.getLocation(), 4))
                    {
                        if(!willFriendlyFire(firingDirection))
                        {
                            fired = fireTriadBullet(enemyBots[0].getLocation());
                        }
                        else
                        {
                            moved = tryMove(firingDirection.rotateLeftDegrees(90));
                        }
                    }
                    if(!willFriendlyFire(firingDirection))
                    {
                        fired = fireBullet(enemyBots[0].getLocation());
                    }
                    else
                    {
                        moved = tryMove(firingDirection.rotateLeftDegrees(90));
                    }

                    if (rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL) != 0)
                    {
                        MapLocation attackLocation = new MapLocation((float) rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL), (float) rc.readBroadcast(ATTACK_LOCATION_Y_CHANNEL)); //creates a maplocation of the attack target
                        if (rc.getLocation().distanceTo(attackLocation) < 1) //if we are close to the attack location
                        {
                            if (!canSenseEnemyGarden(rc.getType().bodyRadius + rc.getType().sensorRadius) && !canSenseEnemyArchon(rc.getType().bodyRadius + rc.getType().sensorRadius)) //if we don't sense any more gardens, reset the location
                            {
                                rc.broadcast(ATTACK_LOCATION_X_CHANNEL, 0);
                                rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, 0);
                            }
                        } else //otherwise, move to
                        {
                            Direction directionToTarget = rc.getLocation().directionTo(attackLocation);
                            moved = tryMove(directionToTarget); //try to move to that location
                            if(!moved)
                            {
                                TreeInfo[] neutralTrees = rc.senseNearbyTrees((float)4, Team.NEUTRAL);
                                if(neutralTrees.length != 0)
                                {
                                    rc.broadcast(OBSTRUCTION_CHANNEL, rc.readBroadcast(OBSTRUCTION_CHANNEL) + 1);
                                    if(rc.readBroadcast(OBSTRUCTION_CHANNEL) >= 10)
                                    {
                                        rc.broadcast(LUMBERJACK_LOCATION_X_CHANNEL, (int)neutralTrees[0].getLocation().x);
                                        rc.broadcast(LUMBERJACK_LOCATION_Y_CHANNEL, (int)neutralTrees[0].getLocation().y);
                                        rc.broadcast(OBSTRUCTION_CHANNEL, 0);
                                    }
                                    if(!willFriendlyFire(directionToTarget) && !willFriendlyFire(directionToTarget.rotateLeftDegrees(20)) && !willFriendlyFire(directionToTarget.rotateLeftDegrees(20)))
                                    {
                                        rc.setIndicatorDot(rc.getLocation(), 0, 256, 256);
                                        if(rc.canFireSingleShot())
                                        {
                                            rc.fireSingleShot(directionToTarget);
                                        }
                                    }
                                }
                                tryMove(directionToTarget.rotateLeftDegrees(rc.readBroadcast(PATH_CHANGE_DEGREES_CHANNEL)));
                            }


                        }
                    }
                    if(!moved && (fired == null))
                    {
                        move(dir);
                    }
                }
                Clock.yield();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    static void runScout() throws GameActionException
    {
        MapLocation[] archons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        Direction dir = rc.getLocation().directionTo(archons[0]);
        float senseRadius = rc.getType().bodyRadius + rc.getType().sensorRadius;


        while (true)
        {
            try
            {
                MapLocation targetLocation = null;
                rc.broadcast(SCOUT_SUM_CHANNEL, rc.readBroadcast(SCOUT_SUM_CHANNEL) + 1);
                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(senseRadius, rc.getTeam().opponent());
                TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(senseRadius, Team.NEUTRAL);
                TreeInfo[] enemyTrees = rc.senseNearbyTrees(senseRadius, rc.getTeam().opponent());
                RobotInfo[] nearbyGardeners = getNearbyGardeners(nearbyEnemies);
                MapLocation archonLocation = nearbyArchon(nearbyEnemies);

                if(nearbyGardeners.length > 0) //if there are nearby enemies
                {
                    targetLocation = nearbyGardeners[0].getLocation(); //target location is the nearest gardener
                    Direction dirToTarget = rc.getLocation().directionTo(targetLocation);
                    if(rc.getLocation().distanceTo(targetLocation) <= 3) //if we are close to them
                    {
                        if(!bulletBlockedByTree(dirToTarget, nearbyGardeners[0])) //if our bullet isn't blocked by trees
                        {
                            if(rc.canMove(dirToTarget, (float) .05))
                            {
                                rc.move(dirToTarget, (float) .05);
                            }
                            System.out.println("Distance to target: " + rc.getLocation().distanceTo(targetLocation));
                            fireBullet(targetLocation);
                        }
                        else
                        {
                            tryMove(dirToTarget.rotateLeftDegrees(45));
                            if(!bulletBlockedByTree(dirToTarget, nearbyGardeners[0])) //if our bullet isn't blocked by trees
                            {
                                fireBullet(targetLocation);
                            }
                        }
                    }
                    else //if we aren't close to the enemy, move towards it
                    {
                        pathTo(targetLocation);
                    }
                }
                else if(nearbyNeutralTrees.length != 0) //if there are nearby neutral trees
                {
                    for(int i = 0; i < nearbyNeutralTrees.length; i++)
                    {
                        if(nearbyNeutralTrees[i].getContainedBullets() > 0) //if they are shakeable
                        {
                            targetLocation = nearbyNeutralTrees[i].getLocation();
                            break;
                        }
                    }

                    if(targetLocation != null)
                    {
                        if(rc.canShake(targetLocation)) //if we can shake them
                        {
                            rc.shake(targetLocation); //shake
                        }
                        else
                        {
                            pathTo(targetLocation); //otherwise move towards them and try to shake again
                            if(rc.canShake(targetLocation))
                            {
                                rc.shake(targetLocation);
                            }
                        }
                    }
                }
                if(!rc.hasMoved())
                {
                    boolean moved = tryMove(dir);
                    if(!moved)
                    {
                        dir = randomDirection();
                        tryMove(dir);
                    }
                }

                if(rc.getRoundNum() % 100 == 0)
                {
                    dir = rc.getLocation().directionTo(archons[0]);
                }
                if(archonLocation != null)
                {
                    rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int) archonLocation.x); //broadcast the closest x value to the x coord channel
                    rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int) archonLocation.y);
                }
                else if(enemyTrees.length >= 3 && rc.readBroadcast(ATTACK_LOCATION_X_CHANNEL) == 0)
                {
                    MapLocation nestTree = enemyTrees[enemyTrees.length / 2].getLocation();
                    rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int) nestTree.x); //broadcast the closest x value to the x coord channel
                    rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int) nestTree.y);
                }
                if(nearbyEnemies.length >= 10)
                {
                    MapLocation armyLocation = nearbyEnemies[5].getLocation();
                    rc.broadcast(ATTACK_LOCATION_X_CHANNEL, (int)armyLocation.x);
                    rc.broadcast(ATTACK_LOCATION_Y_CHANNEL, (int)armyLocation.y);
                }
                Clock.yield();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    static void move(Direction dir) throws GameActionException
    {
        try
        {
            if (rc.canMove(dir) && !rc.hasMoved())
            {
                rc.move(dir);
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    static void moveTo(MapLocation loc) throws GameActionException
    {
        try
        {
            if (rc.canMove(loc) && !rc.hasMoved())
            {
                rc.move(loc);
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static Direction randomDirection()
    {
        return new Direction(rand.nextFloat() * 2 * (float) Math.PI); //takes a random number from "rand" multiplies it by 2pi to get a random radian
    }

    public static TreeInfo getDyingTree()
    { //will return treeinfo of a dying tree (missing 10 more more health) or null if there are none
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius + rc.getType().bodyRadius, rc.getTeam()); //get an array of nearby trees
        if (nearbyTrees.length != 0)
        { //will only execute if there are nearby trees
            for (int i = 0; i < nearbyTrees.length; i++)
            { //for loop to parse through the nearby trees
                if (nearbyTrees[i].getMaxHealth() - nearbyTrees[i].getHealth() > 10)
                {
                    return nearbyTrees[i];
                }
            }
        }
        return null;
    }

    public static TreeInfo getNearbyTree(Team team)
    { //will return treeinfo of a nearby tree (missing 10 more more health) or null if there are none
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius + rc.getType().bodyRadius, team); //get an array of nearby trees
        if (nearbyTrees.length != 0)
        {
            return nearbyTrees[0];
        }
        return null;
    }

    //methods to fire bullets based on a given maplocation or direction
    //best to use it in methods, because changing directions will make you walk into your bullet
    public static Direction fireBullet(MapLocation loc) throws GameActionException
    {
        if (rc.canFireSingleShot())
        {
            Direction dir = rc.getLocation().directionTo(loc);
            rc.fireSingleShot(dir);
            return dir;
        }
        return null;

    }

    public static Direction fireTriadBullet(MapLocation loc) throws GameActionException
    {
        if(rc.canFireTriadShot())
        {
            Direction dir = rc.getLocation().directionTo(loc);
            rc.fireTriadShot(dir);
            return dir;
        }
        return null;
    }

    public static void fireBullet(Direction dir) throws GameActionException
    {
        if (rc.canFireSingleShot())
        {
            rc.fireSingleShot(dir);
        }
    }

    public static boolean canSenseEnemyGarden(float radius)
    {
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(radius, rc.getTeam().opponent());
        if (enemyTrees.length >= 3)
        {
            return true;
        }
        return false;
    }

    public static boolean canSenseEnemyArchon(float radius)
    {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
        for(int i = 0; i < nearbyEnemies.length; i++)
        {
            if(nearbyEnemies[i].getType() == RobotType.ARCHON)
            {
                return true;
            }
        }
        return false;
    }

    public static MapLocation getEnemyGardenLoc(float radius)
    {
        if (canSenseEnemyGarden(radius))
        {
            TreeInfo[] enemyTrees = rc.senseNearbyTrees(radius, rc.getTeam().opponent()); //sense all nearby enemy trees
            return enemyTrees[0].getLocation();
        }
        return null;
    }

    public static boolean tryMove(Direction dir) throws GameActionException
    {
        return tryMove(dir, 15, 3);
    }


    public static boolean tryMove(Direction dir, int degreeOffset, int checksPerSide) throws GameActionException
    {
        // First, try intended direction
        if (!rc.hasMoved() && rc.canMove(dir))
        {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        //boolean moved = rc.hasMoved();
        int currentCheck = 1;

        while (currentCheck <= checksPerSide)
        {
            // Try the offset of the left side
            if (!rc.hasMoved() && rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck)))
            {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            // Try the offset on the right side
            if (!rc.hasMoved() && rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck)))
            {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    public static boolean pathTo(MapLocation targetLocation) throws GameActionException
    {
        Direction dirToTarget = rc.getLocation().directionTo(targetLocation);
        boolean moved = false;
        moved = tryMove(dirToTarget);
        if(!moved)
        {
            moved = tryMove(dirToTarget.rotateLeftDegrees(rc.readBroadcast(PATH_CHANGE_DEGREES_CHANNEL)));
        }
        return moved;
    }

    public static boolean bulletWillCollide(BulletInfo bullet)
    {
        Direction bulletDirection = bullet.getDir();
        Direction directionToBullet = rc.getLocation().directionTo(bullet.getLocation());
        Direction inverseDirectionToBullet = directionToBullet.rotateRightDegrees(180);
        return (inverseDirectionToBullet.degreesBetween(bulletDirection) < 15);
    }

    public static boolean canDodge(Direction bulletDir)
    {
        return rc.canMove(bulletDir.rotateLeftDegrees(90)) || rc.canMove(bulletDir.rotateRightDegrees(90));
    }

    public static Direction dodgeDirection(Direction directionToBullet)
    {//returns the direction we will dodge in
        if(rc.canMove(directionToBullet.rotateLeftDegrees(90)))
        {
            return directionToBullet.rotateLeftDegrees(90);
        }
        else //don't need to check cause this method will only be called if we can move either left or right
            return directionToBullet.rotateRightDegrees(90);
    }

    public static void nest(MapLocation center, Direction openDirection) throws GameActionException
    {
        Direction startDir = openDirection.rotateRightDegrees(60); //start with a 60 degree turn from the open direction
        for(int i = 0; i < 5; i++) //while it hasn't made a full circle back to the start open direction
        {
            if(rc.canPlantTree(startDir) && !startDir.equals(openDirection, (float).1)) //if we can plant a tree in the direction
            {
                rc.plantTree(startDir); //plant it and break out - we are done here
                break;
            }
            else
            {
                startDir = startDir.rotateRightDegrees(60); //otherwise, rotate it another 60 degrees
            }
        }
    }

    public static boolean willFriendlyFire(Direction firingDirection)
    {
        RobotInfo[] friendlies = rc.senseNearbyRobots(3, rc.getTeam());
        for(int i = 0; i < friendlies.length; i++)
        {
            Direction dirToFriendly = rc.getLocation().directionTo(friendlies[i].getLocation());
            if(dirToFriendly.equals(firingDirection, (float).4))
            {
                return true;
            }
        }
        return false;
    }

    public static RobotInfo[] getNearbyGardeners(float radius, Team team)
    {
        RobotInfo[] allNearbyRobots = rc.senseNearbyRobots(radius, team);
        int gardenerCount = 0;
        int gardenerArrayIndex = 0;
        for(int i = 0; i < allNearbyRobots.length; i++)
        {
            if(allNearbyRobots[i].getType() == RobotType.GARDENER)
            {
                gardenerCount++;
            }
        }

        RobotInfo[] nearbyGardeners = new RobotInfo[gardenerCount];
        for(int i = 0; i < allNearbyRobots.length; i++)
        {
            if(allNearbyRobots[i].getType() == RobotType.GARDENER)
            {
                nearbyGardeners[gardenerArrayIndex] = allNearbyRobots[i];
                gardenerArrayIndex++;
            }
        }
        return nearbyGardeners;
    }

    public static RobotInfo[] getNearbyGardeners(RobotInfo[] allNearbyRobots)
    {
        int gardenerCount = 0;
        int gardenerArrayIndex = 0;
        for(int i = 0; i < allNearbyRobots.length; i++)
        {
            if(allNearbyRobots[i].getType() == RobotType.GARDENER)
            {
                gardenerCount++;
            }
        }

        RobotInfo[] nearbyGardeners = new RobotInfo[gardenerCount];
        for(int i = 0; i < allNearbyRobots.length; i++)
        {
            if(allNearbyRobots[i].getType() == RobotType.GARDENER)
            {
                nearbyGardeners[gardenerArrayIndex] = allNearbyRobots[i];
                gardenerArrayIndex++;
            }
        }
        return nearbyGardeners;
    }

    public static boolean bulletBlockedByTree(Direction firingDirection, RobotInfo target)
    {
        TreeInfo[] treesToTarget = rc.senseNearbyTrees(rc.getLocation().distanceTo(target.getLocation())); //get all trees within the range of the distancce between us to the target
        if(treesToTarget.length <= 0) //if there are no trees between us
        {//bullet isn't blocked by tree
            return false;
        }
        else
        {
            for(int i = 0; i < treesToTarget.length; i++)
            {
                Direction dirTreeToTarget = treesToTarget[i].getLocation().directionTo(target.getLocation());
                if(firingDirection.equals(dirTreeToTarget, (float).2))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static void chopTree(TreeInfo tree) throws GameActionException
    {
        int treeID = tree.getID();
        if(rc.canChop(treeID))
        {
            rc.chop(treeID);
        }
        else
        {
            pathTo(tree.getLocation());
            if(rc.canChop(treeID))
            {
                rc.chop(treeID);
            }
        }
    }

    //best as i can figure this should work, if i was motivated enough to find a way to test it
    public static boolean willBulletCollide(Direction firingDirection, MapLocation obstructionCenter, float obstructionRadius, MapLocation targetCenter, float targetRadius)
    {
        float thetaInRadians;
        float epsilonInRadians;

        MapLocation offsetPointT = targetCenter.add(targetCenter.directionTo(obstructionCenter), targetRadius);

        //calculate the degree we need to offset the obstruction point at
        float distance = offsetPointT.distanceTo(obstructionCenter);
        thetaInRadians = (float)Math.acos(distance/obstructionRadius);

        //set the obstruction point
        Direction offsetODirection = new Direction(thetaInRadians);
        MapLocation offsetPointO = obstructionCenter.add(offsetODirection, obstructionRadius);

        epsilonInRadians = 2 * (offsetPointT.directionTo(obstructionCenter)).radiansBetween(offsetPointT.directionTo(offsetPointO));

        rc.setIndicatorLine(offsetPointT, offsetPointO, 256, 0, 0);

        return (firingDirection.equals(obstructionCenter.directionTo(targetCenter), epsilonInRadians));
    }

    public static MapLocation nearbyArchon(RobotInfo[] nearbyEnemyRobots)
    {
        for(int i = 0; i < nearbyEnemyRobots.length; i++)
        {
            if(nearbyEnemyRobots[i].getType() == RobotType.ARCHON)
            {
                return nearbyEnemyRobots[i].getLocation();
            }
        }
        return null;
    }

    public static boolean willBulletHitMe(BulletInfo bullet)
    {
        float radius = rc.getType().bodyRadius;
        float distanceBetween = rc.getLocation().distanceTo(bullet.getLocation());
        float thetaInRadians = 0;
        return false;
    }

}