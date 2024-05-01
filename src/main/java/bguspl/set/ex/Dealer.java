package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.Semaphore;
import java.lang.Math;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    public final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The queue that contains the ids of the players who claimed a set
     */
    public LinkedList<Integer> setCheck;

    /**
     * The lock of setCheck
     */
    public Semaphore sem;

    /**
     * true if the table is currently placing or removing cards from table
     */
    volatile boolean tableUpdating;

    /**
     * The current sleep time of the dealer
     */
    long sleep;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setCheck = new LinkedList<Integer>();
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 999;
        sem = new Semaphore(1, true);
        tableUpdating =true;
        sleep = 10;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        Thread thread = new Thread(players[0]);
        thread.start();
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            try {
                removeAllCardsFromTable();
            } catch (InterruptedException ignored){}

        }
        announceWinners();
        terminate();

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime && setsLeft()) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            try {
                removeCardsFromTable();
            } catch (InterruptedException ignored) {
            }
            updateTimerDisplay(false);
            placeCardsOnTable();
            updateTimerDisplay(false);

        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate(){

        terminate = true;
        players[0].terminate();

        try {
            players[0].getThread().join();
        } catch (InterruptedException ignored){}

        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() throws InterruptedException {

        tableUpdating = true;
        sem.acquire();
        while (!setCheck.isEmpty()) {
            Player p = players[setCheck.removeFirst()];
            int[] cards = new int[3];
            synchronized (p.tokens) {
                if (p.tokens.size() == 3){
                    for (int i = 0; i < 3; i++) {
                        cards[i] = table.slotToCard[p.tokens.get(i)];
                    }
                    if (env.util.testSet(cards)) {
                        for (int i = 0; i < 3; i++) {
                            int slot = p.tokens.get(0);
                            cardRemoved(slot);
                            table.removeCard(slot);
                        }
                        p.point();
                        p.freeze = true;
                        updateTimerDisplay(true);
                    } else {
                        p.penalty();
                        p.freeze = true;
                    }
                }
            }


        }
        sem.release();
    }


    /**
     * removes all tokens from the removed card's slot,
     * removes the players that claimed set whose tokens was placed in the slot
     */

    public void cardRemoved(Integer slot) {
        for (Player p : players) {

            if (p.tokens.contains(slot)) {
                p.tokens.remove(slot);
                setCheck.remove((Integer) p.id);
            }

        }
        env.ui.removeTokens(slot);
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> emptySlots = IntStream.rangeClosed(0, env.config.tableSize-1).boxed().collect(Collectors.toList());
        Collections.shuffle(emptySlots);

        while (!emptySlots.isEmpty() && !deck.isEmpty()) {
            int currSlot = emptySlots.remove(0);
            if (table.slotToCard[currSlot] == null) {
                int rand = (int) (Math.random() * (deck.size()));
                table.placeCard(deck.remove(rand), currSlot);
            }
        }

        tableUpdating = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    public void updateTimerDisplay(boolean reset){
        if (reset) {
            sleep = 10;
            env.ui.setCountdown(env.config.turnTimeoutMillis + 999 , false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 999;
        } else if (reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis) {
            sleep = 0;
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0) , true);
        } else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis() , false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() throws InterruptedException{
        sem.acquire();
        tableUpdating = true;
        for (Player p : players) {
            synchronized (p.tokens) {
                p.tokens.clear();
            }
        }
        env.ui.removeTokens();

        List<Integer> slots = IntStream.rangeClosed(0, env.config.tableSize-1).boxed().collect(Collectors.toList());
        Collections.shuffle(slots);

        while (!slots.isEmpty()) {
            int currSlot = slots.remove(0);
            if (table.slotToCard[currSlot] != null) {
                deck.add(table.slotToCard[currSlot]);
                table.removeCard(currSlot);
            }
        }

        setCheck.clear();
        sem.release();
    }


    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winners = new LinkedList<Player>();
        winners.add(players[0]);
        for (int i = 1; i < players.length; i++) {
            if (players[i].score() > winners.get(0).score()) {
                winners = new LinkedList<Player>();
                winners.add(players[i]);
            } else if (players[i].score() == winners.get(0).score()) {
                winners.add(players[i]);
            }
        }
        int[] AWinners = new int[winners.size()];
        int i = 0;
        for (Player p : winners) {
            AWinners[i] = p.id;
            i++;
        }
        env.ui.announceWinner(AWinners);
    }

    /**
     * adds the id of a player that claimed set
     */
    public void addSetCheck(int player){
        setCheck.addLast(player);
    }

    /**
     * @return - true if there are sets left in the cards on the table and in the deck combined
     */
    public boolean setsLeft(){
        List<Integer> allCards = new LinkedList<Integer>();
        allCards.addAll(deck);
        for (Integer card : table.slotToCard){
            if (card != null) {
                allCards.add(card);
            }
        }
        if (env.util.findSets(allCards, 1).size() == 0)
            return false;
        else
            return true;

    }
}
