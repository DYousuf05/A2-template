package ca.mcmaster.se2aa4.island.team24;

public class Creek extends LandCell {
    private String identifier;
    public Creek (String identifier){
        this.identifier = identifier;
    }
    public void setIdentifier(String identifier){
        if (this.identifier == null) {
            this.identifier = identifier;
        } else {
            return;
        }
    }
    public String getIdentifier(){
        return identifier;
    }
}
