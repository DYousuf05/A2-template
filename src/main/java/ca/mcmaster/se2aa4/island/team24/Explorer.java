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
    private LandCell[] creekcords = new LandCell[10];//keeps track of creeks for closest one calculation
    private LandCell creekPos;
    private LandCell sitePos;

    private int currentBattery = 0;
    private double finalDistance = 0.0;
    private int range = 0; // Max range between drone and out-of-range border
    private int distanceToEnd = 0; // Variable to check the distance between the drone and the grid border
    private Rotate nextRotation = Rotate.CW; // How to turn depending on direction of travel
    private String dir = "E"; // Current direction of drone
    JSONArray creeks = new JSONArray(); // Storage for creeks
    JSONArray emergencySites = new JSONArray(); // Storage for emergency sites

    @Override
    public void initialize(String s) {
        currentPos = new LandCell();
        creekPos = new LandCell();
        sitePos = new LandCell();
        currentPos.x = 1;
        currentPos.y = 1;
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
            creekPos.x = currentPos.x; 
            creekPos.y = currentPos.y; 
        }

        if (emergencySites.length() > 0 && !foundSite) {
            foundSite = true;
            sitePos.x = currentPos.x; 
            sitePos.y = currentPos.y; 
        }

        if (!foundSite && currentBattery > 25) {
            if (hasEchoed && hasScanned) { // Checks if 'ECHO' and 'SCAN' have been executed
                if (distanceToEnd > 1) {
                    decision.put("action", "fly"); // Straight movement
                    distanceToEnd--;
                    if (dir.equals("E")) {
                        currentPos.x++;
                    }
                    else {
                        currentPos.x--;
                    }
                }
                else {
                    decision.put("action", "heading");
                    if (nextRotation == Rotate.CW) { // Clockwise turn (East-to-South)
                        if (dir.equals("E")) {
                            parameters.put("direction", "S");
                            dir = "S";
                            currentPos.y++;
                        }
                        else if (dir.equals("S")) {
                            parameters.put("direction", "W");
                            dir = "W";
                            nextRotation = Rotate.CCW;
                            distanceToEnd = range - 1;
                            currentPos.x--;
                        }
                    }
                    else { // Counter-clockwise Turn (West to East)
                        if (dir.equals("W")) {
                            parameters.put("direction", "S");
                            dir = "S";
                            currentPos.y++;
                        }
                        else if (dir.equals("S")) {
                            parameters.put("direction", "E");
                            dir = "E";
                            nextRotation = Rotate.CW;
                            distanceToEnd = range - 1;
                            currentPos.x++;
                        }
                    }
                    decision.put("parameters", parameters);
                }
                hasScanned = false;
            }
            else {
                if (!hasEchoed) { // 'ECHO' if not done already
                    parameters.put("direction", dir);
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
            finalDistance = Math.sqrt(Math.pow(sitePos.x - creekPos.x, 2) + Math.pow(sitePos.y - creekPos.y, 2));
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
        if (extraInfo.has("creeks")) {
            if (creeks.length() == 0) {
                creeks = extraInfo.getJSONArray("creeks");
            } else {
                if (extraInfo.getJSONArray("creeks").length()>0)
                creeks.put(extraInfo.getJSONArray("creeks").getJSONObject(0));
            }
        }
        if (extraInfo.has("sites")) {
            emergencySites = extraInfo.getJSONArray("sites");
        }
    }

    @Override
    public String deliverFinalReport() {
        logger.info("***** FINAL REPORT *****");

        if (creeks.length() > 0 && emergencySites.length() > 0) {
            logger.info("Final Status: Creek and Emergency Site located. Distance calculated.");
            logger.info("The distance between the 2 sites is: " + finalDistance);
        }
        else {
            logger.info("Final Status: Drone battery at critical level, returned to home to prevent MIA.");
            logger.info("The distance between the 2 sites is: N/A");
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

}