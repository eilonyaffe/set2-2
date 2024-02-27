package bguspl.set.ex;

import java.util.ArrayList;
import java.util.Collections;

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

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;


    /**
     * The commands list of the current player.
     */
    protected BoundedQueue<Integer> commandsQueue;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The number of tokens the player can still place.
     */
    protected int tokensLeft;

    /**
     * The status of the player. 1=playing. 2=waiting for dealer's response.
     */
    protected int status;

    /**
     * response from dealer about made set. -1 is initialization value. 0 is wrong. 1 is correct. 2 is got one of his set elements 
     * taken by a successful set of another player
     */
    protected int wasCorrect;

    /**
     * time between AI "keypresses"
     */
    protected long AIsleep;

    /**
     * the tokens the human player had placed
     */
    protected boolean[] placed_tokens;

    /**
     * the Link containing the set the player has sent to be checked
     */
    protected LinkPlayerSet playerSingleLink;

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
        this.table = table;
        this.id = id;
        this.human = human;
        this.commandsQueue = new BoundedQueue<Integer>();
        this.tokensLeft = 3;
        this.status = 2; //ensures players don't play before dealer places all cards
        this.placed_tokens = new boolean[12];
        this.wasCorrect = -1;
        this.AIsleep = 3000;
        int[] cards = new int[3];
        this.playerSingleLink = new LinkPlayerSet(cards, this);
        System.out.println("player created, id: " + id); //TODO delete later
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            //EYTODO maybe insert here, if tableready==false, then wait. and then in the dealer we will notifyall
            if(this.tokensLeft==0 && this.status==1 && this.table.tableReady){ //player just finished making a set
                this.wasCorrect = -1;
                this.status=2;
                this.sendSetCards();

                synchronized(this.table.playersLocker){
                    while(this.wasCorrect==-1){
                        try{
                            this.table.playersLocker.wait(); //dealer will notify, and instruct point/penatly which will also change tokensleft and status
                        } catch (InterruptedException ignored) {}
                    }
                }
                if(this.wasCorrect==1){
                    this.point();
                }
                else if(this.wasCorrect==0){
                    this.penalty();
                }
                this.wasCorrect = -1;

            }
            else {
                if(!commandsQueue.lst.isEmpty() && this.table.tableReady){
                    int slotCommand = commandsQueue.lst.remove(0);
                    if(this.status==1){
                        if(this.placed_tokens[slotCommand]){ //player removes token
                            this.table.removeToken(this.id, slotCommand);
                            this.placed_tokens[slotCommand]=false;
                            this.tokensLeft++;
                        }
                        else if(!this.placed_tokens[slotCommand]){
                            this.table.placeToken(this.id, slotCommand);
                            this.placed_tokens[slotCommand]=true; //player adds token
                            this.tokensLeft--;
                        }
                    }
                    // else if(this.status==3){
                    //     if(this.placed_tokens[slotCommand]){
                    //         this.table.removeToken(this.id, slotCommand);
                    //         this.placed_tokens[slotCommand]=false;
                    //         this.tokensLeft++;
                    //         this.status = 1; //returns to play normally
                    //     }
                    // }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() { //NEYA ADDED
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

            ArrayList<Integer> slotsGenerator = new ArrayList<Integer>();
            for (int i = 0; i < 12; i++) 
                slotsGenerator.add(i);

            while (!terminate) {
                // TODO implement player key press simulator

                if(this.tokensLeft==0 && this.status==1 && this.table.tableReady){ //player just finished making a set
                    this.status=2;
                    this.sendSetCards();

                    synchronized(this.table.playersLocker){
                        while(this.wasCorrect==-1){
                            try{
                                this.table.playersLocker.wait(); //dealer will notify, and instruct point/penatly which will also change tokensleft and status
                                // System.out.println("player: "+this.id +" exited sleep");
                            } catch (InterruptedException ignored) {}
                        }
                    }

                    // System.out.println("player "+this.id+" entered if, after check, was correct: "+this.wasCorrect);

                    if(this.wasCorrect==1){
                        this.point();
                    }
                    else{
                        if(this.wasCorrect==0){
                            this.penalty();
                        }
                        this.removeAITokens(); //self remove, never is status=3
                    }
                    this.wasCorrect = -1;
                    this.status = 1;
                }

                else{
                    // if(this.tokensLeft==3){
                    //     System.out.println("ai player: "+this.id + " with 3 tokens entered else");
                    // }
                    Collections.shuffle(slotsGenerator);
                    if(this.table.tableReady && this.table.slotToCard[slotsGenerator.get(0)] != null && this.placed_tokens[slotsGenerator.get(0)]==false){ //legal "key press"
                        this.commandsQueue.add(slotsGenerator.get(0)); 
                        // System.out.println("ai player: "+this.id + " added to queue");

                    }

                    if(!commandsQueue.isEmpty() && this.table.tableReady){ //will commit "key press"
                        try{
                            int slotCommand = this.commandsQueue.lst.remove(0);
                            this.table.placeToken(this.id, slotCommand);
                            // System.out.println("ai player: "+this.id + " placed token");

                            this.placed_tokens[slotCommand]=true;
                            this.tokensLeft--;
                            if(this.tokensLeft!=0){ //if finished set, will not sleep
                                Thread.sleep(this.AIsleep);
                            }
                        } catch (InterruptedException ignored) {}
                    }

                    
                }  
            }
        

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() { //NEYA ADDED
        // TODO implement
        this.terminate = true;
    }

    /**
     * Used for AI players, to reset them
     */
    public void removeAITokens() { //Hazilon added
        for (int i = 0; i < 12; i++){ //remove all tokens if set is invalid
            if(this.placed_tokens[i] == true){
                this.table.removeToken(this.id, i);
                this.placed_tokens[i] = false;
                this.tokensLeft++;
            }
        }
        try{
            Thread.sleep(this.AIsleep); //sleep to prolongue next key press
        } catch (InterruptedException ignored) {}
    }


    /**
     * creates the alleged set of cards that the player chose, and sends it to the table
     */
    public void sendSetCards() {
        int[] newCards = new int[3];
        int j=0;
        for(int i=0;j<3 && i<this.placed_tokens.length;i++){
            if(this.placed_tokens[i]==true){
                newCards[j] = table.slotToCard[i];
                j++;
            }
        }
        this.playerSingleLink.cards = newCards;
        this.table.finishedPlayersCards.add(playerSingleLink); 
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if(this.status==3 && this.placed_tokens[slot]==false){ //player has to only remove tokens now //was slotCommand-5
            //do nothing
        }
        else if(this.status==2){ //player awaits dealer's response
            //do nothing //TODO maybe change? don't need if we implement threading correctly
        }
        else{
            if(this.table.tableReady){
                if (this.table.slotToCard[slot] != null){ //NEYA ADDED IF
                this.commandsQueue.add(slot);
                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        this.score++;
        env.ui.setScore(this.id, score);
        this.commandsQueue.Clear();
        this.placed_tokens = new boolean[12]; //resets the player's placed_tokens
        
        long freezeTime = this.env.config.pointFreezeMillis;
        env.ui.setFreeze(this.id, freezeTime); //EYTODO chech if works correctly

        while(freezeTime>0){
            freezeTime = freezeTime - 1000;
            try {
                Thread.sleep(1000); //EYTODO maybe change, now total 5 seconds
            } catch (InterruptedException ignored) {}
            env.ui.setFreeze(this.id, freezeTime); //descending until unfrozen
        }

        this.status = 1; //indicates he resumes to play

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        long freezeTime = this.env.config.penaltyFreezeMillis;
        env.ui.setFreeze(this.id, freezeTime); //EYTODO chech if works correctly
        while(freezeTime>0){
            freezeTime = freezeTime - 1000;
            try {
                Thread.sleep(1000); //EYTODO maybe change, now total 5 seconds
            } catch (InterruptedException ignored) {}
            env.ui.setFreeze(this.id, freezeTime); //descending until unfrozen
        }
        this.commandsQueue.Clear();
        this.placed_tokens = new boolean[12]; //resets the player's placed_tokens
        this.status = 1;
        this.tokensLeft = 3;
    }

    public boolean human() {
        return this.human;
    }

    public int score() {
        return score;
    }
}

class BoundedQueue<T> {
    ArrayList<Integer> lst;
    int capacity;
    BoundedQueue(){ this.capacity = 3; this.lst = new ArrayList<Integer>(); }

    public void add(Integer obj) {
        if(lst.size() < capacity)
            lst.add(obj);
    }

    public Integer remove() {
        Integer retValue = -1;
        if (!lst.isEmpty()){
            retValue = lst.remove(0);
            return retValue;
        }
        return retValue;
    }

    public void Clear() {
        lst.clear();
    }

    public boolean isEmpty() {
        return lst.isEmpty();
    }
}
