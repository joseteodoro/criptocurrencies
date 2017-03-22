import java.util.*;

public class TxHandler {

    private final UTXOPool currentUnspent;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool unspentPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        currentUnspent = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return isValidTx(this.currentUnspent, tx);
    }

    public boolean isValidTx(UTXOPool customUnspent, Transaction tx) {
        Middleware middleware = new Middleware();
        return middleware.isValid(customUnspent, tx);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        //rearranjar as tx pra identificar se existe um deposito depois da retirada e trocar
        //essas tx de posicao

        //try, if its not valid one, try to send it to the last position

        List<Transaction> valid = new LinkedList<>();
        List<Transaction> invalid = new LinkedList<>();
        UTXOPool unspent = removeValidTransactions(currentUnspent, valid);
        for (Transaction t : possibleTxs) {
            if (isValidTx(unspent, t)) {
                valid.add(t);
                unspent = removeValidTransactions(currentUnspent, valid);
            }
            else {
                invalid.add(t);
            }
        }

        int pastEntropy = invalid.size() +1;
        UTXOPool unspentPreview = removeValidTransactions(currentUnspent, valid);
        while (pastEntropy > invalid.size()) {
            pastEntropy = invalid.size();
            for (Transaction t : invalid) {
                if (isValidTx(unspentPreview, t)) {
                    valid.add(t);
                    unspentPreview = removeValidTransactions(unspentPreview, valid);
                }
            }
            invalid.removeAll(valid);
        }

        for (Transaction t : valid) {
            for (Transaction.Input in : t.getInputs()) {
                UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
                currentUnspent.removeUTXO(u);
            }
        }

        return valid.toArray(new Transaction[valid.size()]);
    }

    public UTXOPool getUTXOPool() {
        return currentUnspent;
    }

    private UTXOPool removeValidTransactions(UTXOPool customUnspent, List<Transaction> valid) {
        UTXOPool unspent = new UTXOPool(currentUnspent);
        for (Transaction t : valid) {
            for (Transaction.Input in : t.getInputs()) {
                UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
                unspent.removeUTXO(u);
            }
        }
        return unspent;
    }

    private class Middleware {

        private List<Chain> chains = new LinkedList<>();

        public Middleware () {
            chains.add(new ConditionOne());
            chains.add(new ConditionTwo());
            chains.add(new ConditionThree());
            chains.add(new ConditionFour());
            chains.add(new ConditionFive());
        }

        public void addToChain(Chain chain) {
            chains.add(chain);
        }

        public boolean isValid(UTXOPool utxoPool, Transaction tx) {
            Iterator<Chain> it = chains.iterator();
            boolean result = true;
            while (result && it.hasNext()) {
                result &= it.next().valid(utxoPool, tx);
            }
            return result;
        }
    }

    private abstract class Chain {

        public abstract boolean valid(UTXOPool currentUnspent, Transaction tx);

    }

    /*
    * (1) all outputs claimed by {@code tx} are in the current UTXO pool
     */
    class ConditionOne extends Chain {
        @Override
        public boolean valid(UTXOPool currentUnspent, Transaction tx) {
            for (Transaction.Input in : tx.getInputs()) {
                UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
                if (!currentUnspent.contains(u)) {
                    return false;
                }
            }
            return true;
        }
    }

    /*
    * (2) the signatures on each input of {@code tx} are valid,
     */
    class ConditionTwo extends Chain {

        @Override
        public boolean valid(UTXOPool currentUnspent, Transaction tx) {
            for ( int i = 0 ; i < tx.getInputs().size() ; i++ ) {
                Transaction.Input input = tx.getInputs().get(i);
                UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
                Transaction.Output out = currentUnspent.getTxOutput(u);
                boolean result = Crypto.verifySignature(out.address, tx.getRawDataToSign(i), input.signature);
                if (!result) {
                    return false;
                }
            };
            return true;
        }
    }

    /*
    * (3) no UTXO is claimed multiple times by {@code tx},
     */
    class ConditionThree extends Chain {
        @Override
        public boolean valid(UTXOPool currentUnspent, Transaction tx) {
            Set<UTXO> checked = new HashSet<>();
            for (Transaction.Input input : tx.getInputs()){
                UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
                if (checked.contains(u)) {
                    return false;
                }
                checked.add(u);
            };
            return true;
        }
    }

    /*
    * (4) all of {@code tx}s output values are non-negative, and
     */
    class ConditionFour extends Chain {
        @Override
        public boolean valid(UTXOPool currentUnspent, Transaction tx) {
            for (Transaction.Output out : tx.getOutputs() ) {
                if (out.value < 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /*
    * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    class ConditionFive extends Chain {
        @Override
        public boolean valid(UTXOPool currentUnspent, Transaction tx) {
            double inputSum = tx.getInputs().stream().mapToDouble(value -> {
                UTXO u = new UTXO(value.prevTxHash, value.outputIndex);
                return currentUnspent.getTxOutput(u).value;
            }).sum();

            double outputSum = tx.getOutputs().stream().mapToDouble(value -> {
                return value.value;
            }).sum();
            return inputSum >= outputSum;
        }

    }

}
