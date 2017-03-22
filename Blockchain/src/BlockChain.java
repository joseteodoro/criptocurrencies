// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {

    public static final int CUT_OFF_AGE = 10;

    private Map<Integer, Set<BlockEntry>> nodesAtHeight;

    private HashMap<byte[], BlockEntry> recentNodes;

    private BlockEntry head;

    private int maxHeight;

    private TransactionPool transactionPool;

    public static <T, K> void addBlock(T t, K k, Map<T, Set<K>> map) {
        if (!map.containsKey(t)) {
            map.put(t, new HashSet<>());
        }
        map.get(t).add(k);
    }

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        ArrayList<Transaction> txs = genesisBlock.getTransactions();
        UTXOPool uPool = new UTXOPool();

        txs.add(genesisBlock.getCoinbase());

        int index = 0;

        for (Transaction tx : txs) {
            index = 0;
            for (Transaction.Output output : tx.getOutputs()) {
                uPool.addUTXO(new UTXO(tx.getHash(), index), output);
                index++;
            }
        }

        BlockEntry genBlockNode = new BlockEntry(genesisBlock, null, uPool);

        this.nodesAtHeight = new HashMap<>();
        addBlock(1, genBlockNode, nodesAtHeight);
        this.maxHeight = 1;
        this.recentNodes = new HashMap<byte[], BlockEntry>();
        this.recentNodes.put(genesisBlock.getHash(), genBlockNode);

        this.head = genBlockNode;
        this.transactionPool = new TransactionPool();
    }

    /**
     * Get the maximum height block
     */
    public Block getMaxHeightBlock() {
        return head.block;
    }

    /**
     * Get the UTXOPool for mining a new block on top of max height block
     */
    public UTXOPool getMaxHeightUTXOPool() {
        return head.unspentPool;
    }

    /**
     * Get the transaction pool to mine a new block
     */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    private void removeAtHeight(int height) {
        if (height <= 0) {
            return;
        }
        Set<BlockEntry> bns = nodesAtHeight.get(height);
        if (bns != null) {
            bns.forEach(blockEntry -> {
                recentNodes.remove(blockEntry.block.getHash());
            });
        }
        nodesAtHeight.remove(height);
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * <p>
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     *
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null) {
            return false;
        }
        BlockEntry parentNode = recentNodes.get(block.getPrevBlockHash());
        if (parentNode == null) {
            return false;
        }

        TxHandler txHandler = new TxHandler(parentNode.copyUTXOPool());
        int myHeight = parentNode.height + 1;
        if (myHeight <= maxHeight - CUT_OFF_AGE) {
            return false;
        }

        ArrayList<Transaction> transactions = block.getTransactions();
        transactions.forEach(transaction -> {
            transaction.finalize();
        });

        Transaction[] arrayTransactions = transactions.toArray(new Transaction[transactions.size()]);
        Transaction[] validTransactions = txHandler.handleTxs(arrayTransactions);

        if (validTransactions.length < arrayTransactions.length) {
            return false;
        }

        addBlockAndUpdateHead(block, parentNode, txHandler, myHeight);
        return true;
    }

    void addBlockAndUpdateHead(Block block, BlockEntry parentNode, TxHandler txHandler, int myHeight) {
        UTXOPool up = txHandler.getUTXOPool();
        up.addUTXO(new UTXO(block.getCoinbase().getHash(), 0), block.getCoinbase().getOutput(0));
        BlockEntry bn = new BlockEntry(block, parentNode, up);
        addBlock(myHeight, bn, nodesAtHeight);
        recentNodes.put(block.getHash(), bn);
        if (myHeight > maxHeight) {
            maxHeight++;
            removeAtHeight(maxHeight - CUT_OFF_AGE - 1);
            head = bn;
        }
    }

    /**
     * Add a transaction to the transaction pool
     */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }

    private class BlockEntry {
        int height;
        int parent;
        Block block;
        UTXOPool unspentPool;

        public BlockEntry(Block block, BlockEntry parent, UTXOPool unspentPool) {
            this.block = block;
            this.unspentPool = unspentPool;
            if (parent != null) {
                this.parent = parent.hashCode();
                height = parent.height + 1;
            } else {
                this.parent = 0;
                height = 1;
            }
        }

        public UTXOPool copyUTXOPool() {
            return new UTXOPool(unspentPool);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockEntry that = (BlockEntry) o;
            return Objects.equals(this.block.getHash(), that.block.getHash());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.block.getHash());
        }
    }
}