/**
 * Created by josets on 3/6/17.
 */
public class Starter {

    public static void main(String[] args) {
        // There are four required command line arguments: p_graph (.1, .2, .3),
        // p_malicious (.15, .30, .45), p_txDistribution (.01, .05, .10),
        // and numRounds (10, 20). You should try to test your CompliantNode
        // code for all 3x3x3x2 = 54 combinations.

        String [] arguments = {".2", ".45", ".05", "20"};
        Simulation.main(arguments);
    }
}
