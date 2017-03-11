import java.util.*;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private final double p_graph;

    private final double p_malicious;

    private final double p_txDistribution;

    private final int numRounds;

    private int round = 0;

    private boolean[] followees;

    private Set<Transaction> pendingTransactions;

    private Set<Transaction> proposedTransactions;

    private Map<Transaction, Set<Integer>> nodeByTransaction;

    private Map<Integer, Set<Transaction>> transactionByNode;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
        this.transactionByNode = new HashMap<>();
        this.nodeByTransaction = new HashMap<>();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
        this.proposedTransactions = new HashSet<>(pendingTransactions);
    }

    public Set<Transaction> sendToFollowers() {
        if (round < numRounds - 1) {
            return this.proposedTransactions;
        }
        return evaluateTransactions();
    }

    private Set<Transaction> evaluateTransactions() {
        // valid folowees, decide if the candidate is malicious or not
        Set<Integer> validNodes = new HashSet<>();
        nodeByTransaction.entrySet().forEach(transactionSetEntry -> {
            if (this.pendingTransactions.contains(transactionSetEntry.getKey())) {
                validNodes.addAll(transactionSetEntry.getValue());
            }
        });
        Set<Transaction> unspentTransactios = new HashSet<>(this.pendingTransactions);
        unspentTransactios.addAll(proposedTransactions);
//        validNodes.stream().forEach(integer -> {
//            unspentTransactios.addAll(transactionByNode.get(integer));
//        });
        return unspentTransactios;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        round++;
        System.out.println("calling another round " + round);
        candidates.stream().forEach(candidate -> {
            if (this.followees[candidate.sender]) {
                if (!this.pendingTransactions.contains(candidate.tx)) {
                    this.pushCandidate(candidate);
                    this.proposedTransactions.add(candidate.tx);
                }
            }
        });
    }

    private void pushCandidate(Candidate candidate) {
        Integer node = Integer.valueOf(candidate.sender);
        if (!transactionByNode.containsKey(node)) {
            transactionByNode.put(node, new HashSet<>());
        }
        if (!nodeByTransaction.containsKey(candidate.tx)) {
            nodeByTransaction.put(candidate.tx, new HashSet<>());
        }
        transactionByNode.get(node).add(candidate.tx);
        nodeByTransaction.get(candidate.tx).add(node);
    }
}
