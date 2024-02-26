package bguspl.set.ex;

import java.util.ArrayList;

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
     * The status of the player. 1=playing. 2=waiting for dealer's response. 3=failed to make set, needs to remove tokens
     */
    protected int status;

    /**
     * the tokens the human player had placed
     */
    protected boolean[] placed_tokens;

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
                this.status=2;
                this.sendSetCards();

                synchronized(this.table.playersLocker){
                    while(this.status==2){
                        try{
                            this.table.playersLocker.wait(); //dealer will notify, and instruct point/penatly which will also change tokensleft and status
                        } catch (InterruptedException ignored) {}
                    }
                }

            }
            else{
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
                    else if(this.status==3){
                        if(this.placed_tokens[slotCommand]){
                            this.table.removeToken(this.id, slotCommand);
                            this.placed_tokens[slotCommand]=false;
                            this.tokensLeft++;
                            this.status = 1; //returns to play normally
                        }
                    }
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
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }



    /**
     * creates the alleged set of cards that the player chose, and sends it to the table
     */
    public void sendSetCards() {
        int[] cards = new int[3];
        int j=0;
        for(int i=0;j<3 && i<this.placed_tokens.length;i++){
            if(this.placed_tokens[i]==true){
                cards[j] = table.slotToCard[i];
                j++;
            }
        }
        Link link = new Link(cards, this);
        this.table.finishedPlayersCards.add(link); 
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
                this.commandsQueue.add(slot);
            }
            // System.out.println("slot pressed by player: "+ id + " is: "+slot);
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
        env.ui.setFreeze(this.id, 1000); //EYTODO chech if works correctly
        try {
            Thread.sleep(1000); //EYTODO maybe change, now 1 seconds
        } catch (InterruptedException ignored) {}
        env.ui.setFreeze(this.id, 0); //"unfreeze"
        this.status = 1; //indicates he resumes to play

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        int freezeTime = 5000;
         env.ui.setFreeze(this.id, freezeTime); //EYTODO chech if works correctly
         for(int i=1;i<6;i++){
            try {
                Thread.sleep(1000); //EYTODO maybe change, now total 5 seconds
            } catch (InterruptedException ignored) {}
            env.ui.setFreeze(this.id, freezeTime-(i*1000)); //descending until unfrozen
        }
        this.commandsQueue.Clear();
        this.status = 3;
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