package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * True iff the game just began.
     */
    // private boolean gameStart; //hazilon changed

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The system time when starting the 60 seconds loop
     */
    private long startTime = Long.MAX_VALUE;

    /**
     * The system time when starting the 60 seconds loop
     */
    private long timeElapsed = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        // this.gameStart = true; //hazilon changed
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        createAndRunPlayerThreads(); //EY new

        while (!shouldFinish()) {
            placeCardsOnTable();
            this.table.hints(); //EYTODO delete later
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }    //NEYA MODIFIED

        if (env.util.findSets(deck, 1).size() == 0) {//in case game ends when there are no more potential sets available
            System.out.println("No more sets found - GAME ENDS");
            terminate();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Creates and runs all player threads
     */
    private void createAndRunPlayerThreads() {
        for (int i = 0; i < players.length; i++){
            Player a = players[i];
            Thread playerThread = new Thread(a);
            playerThread.start();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() { //NEYA ADDED
        // TODO implement
        for (Player p: this.players){
            p.terminate();
        }
        this.terminate = true;
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
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        while(!this.table.finishedPlayerSets.isEmpty()){ //TODO was if(...). make sure it works. also was .isEmpty()
            LinkPlayerSet removedLink = this.table.finishedPlayerSets.removeFirst(); //hazilon
            // System.out.println("dealer check validity of set by player: "+removedLink.player.id);
            int[] cardsSet = removedLink.cards;
            Player player = removedLink.player;
            boolean success = this.env.util.testSet(cardsSet);
            // System.out.println("set made by player: "+player.id +" is: "+success);
            if(success){
                this.table.tableReady = false;
                System.out.println("success!");
                for(int card: cardsSet){
                    int slot = this.table.cardToSlot[card];
                    ThreadSafeList slotObj = this.table.getSlot(slot);
                    int[] playersWithToken = slotObj.getPlayers();
                    for(int playerToReturn : playersWithToken){ //first returning tokens to players
                        Player currPlayer = this.players[playerToReturn];
                        currPlayer.tokensLeft++;
                        // System.out.println("removed card from slot: "+ slot +" for player: "+currPlayer.id +" tokens left: "+currPlayer.tokensLeft);
                        currPlayer.placed_tokens[slot]=false;
                        
                        //TODO also need to check if players with finished alleged set had cards who got just deleted. if so, remove their set and give them 3 tokens and status 1
                        if(currPlayer.status==2 && currPlayer.id != player.id && currPlayer.wasCorrect!=-2){ //another player who had just finished
                            System.out.println("player failed to make set: "+currPlayer.id);
                            this.table.finishedPlayerSets.remove(currPlayer.playerSingleLink);
                            currPlayer.wasCorrect = -2;
                        }
                    }
                    slotObj.removeAll();
                    this.table.removeCard(slot);
                }
                this.updateTimerDisplay(true);
                player.wasCorrect = 1; //indicates the player to activate point() on itself
                // this.table.hints(); //EYTODO delete later 
            }
            else{
                for(int card: cardsSet){
                    if(this.table.cardToSlot[card]!=null){
                        int slot = this.table.cardToSlot[card];
                        ThreadSafeList slotObj = this.table.getSlot(slot);
                        slotObj.remove(player.id);
                    }
                }
                player.wasCorrect = 0; //indicates the player to activate penalty() on itself
            }
            synchronized(this.table.playersLocker){
                this.table.playersLocker.notifyAll(); //the first player from finishedPlayersCards got point/penalty, will change status so won't lock again. the players who weren't handled but are in the list will lock again
                // System.out.println("dealer did notifyall on lock");
            }
            // this.table.hints(); //EYTODO delete later 

            // this.table.hints(); //EYTODO delete later 
            // System.out.println("no more sets in deck? "+ (env.util.findSets(deck, 1).size() == 0));
        }

        this.table.tableReady = true;


    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        this.dealerShuffle();
        while(!deck.isEmpty()){
            for(int slot=0;slot<12;slot++){
                if(table.slotToCard[slot]==null){
                    table.placeCard(deck.remove(0), slot);
                }
            }
            break;
        }
    }

    /**
     * shuffles the deck
     */
    private void dealerShuffle() { //EYTODO new
        if(!deck.isEmpty()){
            Collections.shuffle(deck); 
        }
    }
    
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        if(this.startTime == Long.MAX_VALUE && this.reshuffleTime == Long.MAX_VALUE){
            this.startTime = System.currentTimeMillis();
            this.reshuffleTime = System.currentTimeMillis() + (env.config.turnTimeoutMillis + 1000); //EY: dont change!
            this.timeElapsed = System.currentTimeMillis() + (env.config.turnTimeoutMillis + 1000);
        }

        try {
            Thread.sleep(1000); //EYTODO maybe change, now 1 seconds
            this.timeElapsed -= 1000;
        } catch (InterruptedException ignored) {}

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            this.startTime = Long.MAX_VALUE;
            this.reshuffleTime = Long.MAX_VALUE;
            this.timeElapsed = Long.MAX_VALUE;
        }
        else{
            env.ui.setCountdown(timeElapsed-startTime, false);
        }
        if(this.table.tableReady==false){
            this.table.tableReady=true;
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        this.table.tableReady = false;

        this.table.finishedPlayerSets.removeAll(); //all attempts at sets will be removed //TODO maybe change?

        for(int slot=0;slot<12;slot++){
            if(table.slotToCard[slot]!=null){
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }

        for(Player player:players){
            player.commandsQueue.Clear();
            player.tokensLeft = 3;
            player.placed_tokens = new boolean[12];
            player.status = 1;
            player.wasCorrect = -2;

            System.out.println("player resetted: "+player.id);
        }

        this.table.removeAllTokens();
        System.out.println("removed all cards from table");

        // notifyAll();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore = -1;
        List<Integer> winners = new ArrayList<Integer>();

        for (Player p: this.players){
            int currScore = p.score();
            if(currScore > maxScore){
                maxScore = currScore;
                winners.clear();
                winners.add(p.id);
            }
            else if(currScore == maxScore){
                winners.add(p.id);
            }
        }

        int[] winPlayers = winners.stream().mapToInt(i -> i).toArray();
        env.ui.announceWinner(winPlayers);

    }
}
