package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.LinkedList;
import java.util.List;


import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The current tokens of the player placed on table
     */
    public final List<Integer> tokens;

    /**
     * The next presses of the player
     */
    public BlockingQueue<Integer> presses;

    /**
     * The current timer of the player (increased if should be frozen)
     */
    public long timer;

    /**
     * true if the player is frozen
     */
    volatile public boolean freeze;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        tokens = new LinkedList<Integer>();
        presses = new ArrayBlockingQueue<Integer>(3);
        timer = System.currentTimeMillis();
        freeze = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run(){
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        if (id < env.config.players - 1 && human) {
            Thread nextPlayerThread = new Thread(dealer.players[id + 1]);
            nextPlayerThread.start();
        }
        while (!terminate ) {

            while (System.currentTimeMillis() < timer) {
                env.ui.setFreeze(id, timer - System.currentTimeMillis() + 1000);
                env.ui.setFreeze(id, timer - System.currentTimeMillis() + 1000);
            }
            env.ui.setFreeze(id, timer - System.currentTimeMillis());
            freeze = false;
            while (!dealer.tableUpdating && !freeze) {
                if (!presses.isEmpty()) {
                    Integer currPress = presses.poll();
                    try {
                        dealer.sem.acquire();
                        synchronized (tokens) {
                            if (table.slotToCard[currPress] != null) {
                                if (tokens.contains(currPress)) {
                                    tokens.remove(currPress);
                                    table.removeToken(id, currPress);
                                } else if (tokens.size() < 3) {
                                    tokens.add(currPress);
                                    table.placeToken(id, currPress);
                                    if (tokens.size() == 3) {
                                        freeze = true;
                                        dealer.addSetCheck(id);
                                        }
                                    }
                                }

                            }

                        dealer.sem.release();
                    }catch (InterruptedException ignored){}
                }
            }
        }
        if(id<env.config.players-1 && human) try{
            dealer.players[id+1].playerThread.join();
        } catch (InterruptedException ignored){}
        else if (id<env.config.players-1) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            if (id < env.config.players - 1) {
                Thread nextPlayerThread = new Thread(dealer.players[id + 1]);
                nextPlayerThread.start();
            }
            while (!terminate) {
                while (!dealer.tableUpdating && !freeze) {
                        int slot = (int) (Math.random() * (env.config.tableSize));
                        if (table.slotToCard[slot] != null)
                            presses.offer(slot);
                }
            }
            if(id<env.config.players-1) try{
                dealer.players[id+1].playerThread.join();
            } catch (InterruptedException ignored){}
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);

        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        if(id<env.config.players-1)
            dealer.players[id+1].terminate();
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
          if (canPress())
            presses.offer(slot);

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        timer = System.currentTimeMillis() + env.config.pointFreezeMillis - 100;
        env.ui.setScore(id, ++score);

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {///////////////////////////////////////////////////////
        timer = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
    }

    /**
     * @return - the current score of the player
     */
    public int score() {
        return score;
    }

    /**
     * @return - the player's thread
     */
    public Thread getThread() {
        return playerThread;
    }

    /**
     * @return - true if the player is allowed to add a press
     */
    public boolean canPress() {return !dealer.tableUpdating && !freeze && System.currentTimeMillis() > timer;};
}