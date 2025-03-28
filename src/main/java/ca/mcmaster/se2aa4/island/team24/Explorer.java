package ca.mcmaster.se2aa4.island.team24;

import java.io.StringReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.ace_design.island.bot.IExplorerRaid;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Explorer implements IExplorerRaid {

    private final Logger logger = LogManager.getLogger();
    
    private boolean hasEchoed = false; // If the drone has used 'ECHO'
    private boolean hasScanned = false; // If the drone has used 'SCAN'
    private boolean foundCreek = false; // If the drone has scanned a creek
    private boolean foundSite = false; // If the drone has scanned an emergency site
    
    private LandCell currentPos;
    private Creek[] creekcords = new Creek[10];//keeps track of creeks for closest one calculation
    private LandCell creekPos;
    private LandCell sitePos;

    private int currentBattery = 0;
    private double finalDistance = 0.0;
    private int range = 0; // Max range between drone and out-of-range border
    private int distanceToEnd = 0; // Variable to check the distance between the drone and the grid border
    private Rotate nextRotation = Rotate.CW; // How to turn depending on direction of travel
    private Direction dir = Direction.E; // Current direction of drone
    JSONArray creeks = new JSONArray(); // Storage for creeks
    JSONArray emergencySites = new JSONArray(); // Storage for emergency sites

    @Override
    public void initialize(String s) {
        currentPos = new LandCell();
        creekPos = new LandCell();
        sitePos = new LandCell();
        currentPos.setX(1);
        currentPos.setY(1);
        logger.info("** Initializing the Exploration Command Center");
        JSONObject info = new JSONObject(new JSONTokener(new StringReader(s)));
        logger.info("** Initialization info:\n {}",info.toString(2));
        String direction = info.getString("heading");
        Integer batteryLevel = info.getInt("budget");
        currentBattery = batteryLevel;
        logger.info("The drone is facing {}", direction);
        logger.info("Battery level is {}", batteryLevel);
    }

    @Override
    public String takeDecision() {
        JSONObject decision = new JSONObject(); // For the action
        JSONObject parameters = new JSONObject(); // For any actions that include parameters 
        
        if (creeks.length() > 0 && !foundCreek) {
            foundCreek = true;
            creekPos.setX(currentPos.getX());
            creekPos.setY(currentPos.getY());
        }

        if (emergencySites.length() > 0 && !foundSite) {
            foundSite = true;
            sitePos.setX(currentPos.getX());
            sitePos.setY(currentPos.getY());
        }

        if (currentBattery > 35) {
            if (hasEchoed && hasScanned) { // Checks if 'ECHO' and 'SCAN' have been executed
                if (distanceToEnd > 1) {
                    decision.put("action", "fly"); // Straight movement
                    distanceToEnd--;
                    if (dir == Direction.E) {
                        currentPos.setX(currentPos.getX()+1);
                    }
                    else {
                        currentPos.setX(currentPos.getX()-1);
                    }
                }
                else {
                    decision.put("action", "heading");
                    if (nextRotation == Rotate.CW) { // Clockwise turn (East-to-South)
                        if (dir == Direction.E) {
                            parameters.put("direction", "S");
                            dir = Direction.S;
                            currentPos.setY(currentPos.getY()+1);;
                        }
                        else if (dir == Direction.S) {
                            parameters.put("direction", "W");
                            dir = Direction.W;
                            nextRotation = Rotate.CCW;
                            distanceToEnd = range - 1;
                            currentPos.setX(currentPos.getX()-1);
                        }
                    }
                    else { // Counter-clockwise Turn (West to East)
                        if (dir == Direction.W) {
                            parameters.put("direction", "S");
                            dir = Direction.S;
                            currentPos.setY(currentPos.getY()+1);;
                        }
                        else if (dir == Direction.S) {
                            parameters.put("direction", "E");
                            dir = Direction.E;
                            nextRotation = Rotate.CW;
                            distanceToEnd = range - 1;
                            currentPos.setX(currentPos.getX()+1);;
                        }
                    }
                    decision.put("parameters", parameters);
                }
                hasScanned = false;
            }
            else {
                if (!hasEchoed) { // 'ECHO' if not done already
                    parameters.put("direction", dir.name());
                    decision.put("action", "echo");
                    decision.put("parameters", parameters);
                    hasEchoed = true;
                }
                else { // 'SCAN' if not done already
                    decision.put("action", "scan");
                    hasScanned = true;
                }
            }
        }
        else {
            if (foundCreek && foundSite) {
                finalDistance = findShortest();
            }
            // finalDistance = Math.sqrt(Math.pow(sitePos.getX() - creekPos.getX(), 2) + Math.pow(sitePos.getY() - creekPos.getY(), 2));
            decision.put("action", "stop");
        }
        logger.info("** Decision: {}",decision.toString());
        return decision.toString();
    }

    @Override
    public void acknowledgeResults(String s) {
        JSONObject response = new JSONObject(new JSONTokener(new StringReader(s)));
        logger.info("** Response received:\n"+response.toString(2));
        Integer cost = response.getInt("cost");
        currentBattery -= cost; // Recalculates the current battery
        logger.info("The cost of the action was {}", cost);
        logger.info("The remaining battery of the drone is {}", currentBattery);
        String status = response.getString("status");
        logger.info("The status of the drone is {}", status);
        JSONObject extraInfo = response.getJSONObject("extras");
        logger.info("Additional information received: {}", extraInfo);
        if (extraInfo.has("range")) { // Fetches the range if using 'ECHO'
            range = extraInfo.getInt("range");
            distanceToEnd = range;
        }
        if (extraInfo.has("creeks") && extraInfo.getJSONArray("creeks").length()>0) {
            String identifier = extraInfo.getJSONArray("creeks").getString(0);
            int creek_index = creeks.length();
            if (creeks.length() == 0) {
                creeks = extraInfo.getJSONArray("creeks");
            } else {
                creeks.put(identifier);
            }
            creekcords[creek_index] = new Creek(identifier);
            creekcords[creek_index].setX(creekPos.getX());
            creekcords[creek_index].setY(creekPos.getY());
        }
        if (extraInfo.has("sites") && emergencySites.length()==0) {
            emergencySites = extraInfo.getJSONArray("sites");
        }
    }

    @Override
    public String deliverFinalReport() {
        logger.info("***** FINAL REPORT *****");
        if (creeks.length() > 0 && emergencySites.length() > 0) {
            logger.info("Final Status: Creek and Emergency Site located. Distance calculated.");
            logger.info("The distance from the nearest creek to the emergency site is: " + finalDistance);
        }
        else {
            logger.info("Final Status: Drone battery at critical level, returned to home to prevent MIA.");
            logger.info("The distance from the nearest creek to the emergency site is: N/A");
        }

        if (creeks.length() > 0) {
            logger.info("The located creek identifier is: " + creeks.getString(0));
        } else {
            logger.info("Creek could not be found.");
        }
        
        if (emergencySites.length() > 0) {
            logger.info("The located emergency site identifier is: " + emergencySites.getString(0));
        } else {
            logger.info("Emergency site could not be found.");
        }
       
        return "report printed";
    }
    public double findShortest(){
        double min = Math.sqrt(Math.pow(sitePos.getX() - creekcords[0].getX(), 2) + Math.pow(sitePos.getY() - creekcords[0].getY(), 2));
        logger.info("min value: "+ min);
        for (int i = 1; creekcords[i] != null; i++) {
            Creek candidate = creekcords[i];
            double temp_calc = Math.sqrt(Math.pow(sitePos.getX() - candidate.getX(), 2) + Math.pow(sitePos.getY() - candidate.getY(), 2));
            if (temp_calc < min) {
                min = temp_calc;
            }
        }
        return min;
    }
}