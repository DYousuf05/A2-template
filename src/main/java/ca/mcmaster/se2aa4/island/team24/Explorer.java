package ca.mcmaster.se2aa4.island.team24;

import java.io.StringReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.ace_design.island.bot.IExplorerRaid;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

class DetectLand {
    // Use scanner and radar to analyze oncoming land masses
    // Determines whether to stop or fly the drone
    // Holds a parameter to indicate whether to search for creek or emergency site
}

class Flight {
    // Fly the drone in path
}

enum Rotate {
    CW, // Clockwise
    CCW // Counter-clockwise
}

public class Explorer implements IExplorerRaid {

    private final Logger logger = LogManager.getLogger();
    private boolean hasEchoed = false; // If the drone has used 'ECHO'
    private boolean hasScanned = false; // If the drone has used 'SCAN'
    private boolean foundCreek = false; // If the drone has scanned a creek
    private boolean foundSite = false; // If the drone has scanned an emergency site
    private int currentBattery = 0;
    private int count = 0;
    private int range = 0; // Max range between drone and out-of-range border
    private int distanceToEnd = 0; // Variable to check the distance between the drone and the grid border
    private Rotate nextRotation = Rotate.CW; // How to turn depending on direction of travel
    private String dir = "E"; // Current direction of drone
    JSONArray creeks = new JSONArray(); // Storage for creeks
    JSONArray emergencySites = new JSONArray(); // Storage for emergency sites

    @Override
    public void initialize(String s) {
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
        
        if (currentBattery <= 10) {
            decision.put("action", "stop");
        }

        if (creeks.length() > 0 && !foundCreek) {
            foundCreek = true;
        }

        if (emergencySites.length() > 0 && !foundSite) {
            foundSite = true;
        }

        if (!foundCreek || !foundSite) {
            if (hasEchoed && hasScanned) { // Checks if 'ECHO' and 'SCAN' have been executed
                if (distanceToEnd > 1) {
                    decision.put("action", "fly"); // Straight movement
                    distanceToEnd--;
                }
                else {
                    decision.put("action", "heading");
                    if (nextRotation == Rotate.CW) { // Clockwise turn (East-to-West)
                        if (dir.equals("E")) {
                            parameters.put("direction", "S");
                            dir = "S";
                        }
                        else if (dir.equals("S")) {
                            parameters.put("direction", "W");
                            dir = "W";
                            nextRotation = Rotate.CCW;
                            distanceToEnd = range - 1;
                        }
                    }
                    else { // Counter-clockwise Turn (West to East)
                        if (dir.equals("W")) {
                            parameters.put("direction", "S");
                            dir = "S";
                        }
                        else if (dir.equals("S")) {
                            parameters.put("direction", "E");
                            dir = "E";
                            nextRotation = Rotate.CW;
                            distanceToEnd = range - 1;
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
            count++;
        }
        else {
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
            creeks = extraInfo.getJSONArray("creeks");
        }
        if (extraInfo.has("sites")) {
            emergencySites = extraInfo.getJSONArray("sites");
        }
    }

    @Override
    public String deliverFinalReport() {
        return "no creek found";
    }

}